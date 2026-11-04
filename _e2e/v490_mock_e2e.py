# -*- coding: utf-8 -*-
"""v4.9 隔离验证：本地 mock OpenAI 服务端抓取请求体，验证 Spring AI 序列化确实带出 image_url。"""
import base64
import json
import os
import struct
import threading
import time
import urllib.error
import urllib.request
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE = os.environ.get('LEMON_BASE', 'http://localhost:18090/api/v1')
CAPTURE_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'v490_mock_capture.json')
PASS, FAIL = [], []

def ok(name, cond, detail=''):
    (PASS if cond else FAIL).append((name, detail))
    print(('  PASS ' if cond else '  FAIL ') + name + (' | ' + detail if detail else ''))

def req(path, data=None, token=None, method=None, timeout=120):
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(BASE + path, data=body, method=method or ('POST' if body else 'GET'))
    r.add_header('Content-Type', 'application/json')
    r.add_header('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0.0.0')
    if token:
        r.add_header('Authorization', 'Bearer ' + token)
    try:
        resp = urllib.request.urlopen(r, timeout=timeout)
        return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())

def make_png(rgb):
    w, h = 64, 64
    raw = b''.join(b'\x00' + bytes(rgb) * w for _ in range(h))
    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    ihdr = struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr)
            + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))

class MockOpenAI(BaseHTTPRequestHandler):
    def log_message(self, *a):  # 静默访问日志
        pass

    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(length)
        # 多份 capture（fallback 链会连发多次，最后一份不是 mock 的）
        n = 0
        while os.path.exists(CAPTURE_PATH.replace('.json', '_%d.json' % n)):
            n += 1
        with open(CAPTURE_PATH.replace('.json', '_%d.json' % n), 'wb') as f:
            f.write(body)
        self.send_response(200)
        self.send_header('Content-Type', 'text/event-stream')
        self.end_headers()
        # 标准 OpenAI chunk 形态（含 role / finish_reason / usage），缺字段会触发 Spring AI 解析异常走回落
        toks = ['mock ', 'vision ', 'ok']
        for i, tok in enumerate(toks):
            chunk = {
                'id': 'chatcmpl-mock', 'object': 'chat.completion.chunk', 'created': int(time.time()),
                'model': 'mock-vision',
                'choices': [{'index': 0,
                             'delta': {'role': 'assistant', 'content': tok},
                             'finish_reason': None}],
            }
            self.wfile.write(('data: ' + json.dumps(chunk) + '\n\n').encode())
        final = {
            'id': 'chatcmpl-mock', 'object': 'chat.completion.chunk', 'created': int(time.time()),
            'model': 'mock-vision',
            'choices': [{'index': 0, 'delta': {}, 'finish_reason': 'stop'}],
            'usage': {'prompt_tokens': 100, 'completion_tokens': 3, 'total_tokens': 103},
        }
        self.wfile.write(('data: ' + json.dumps(final) + '\n\n').encode())
        self.wfile.write(b'data: [DONE]\n\n')

png = make_png((220, 38, 38))
data_url = 'data:image/png;base64,' + base64.b64encode(png).decode()

server = ThreadingHTTPServer(('127.0.0.1', 18099), MockOpenAI)
threading.Thread(target=server.serve_forever, daemon=True).start()
print('mock openai up on 18099')

_, login = req('/auth/login', {'username': 'admin', 'password': 'lemon123456'})
tk = login['data']['token']

# 0) 清理历史残留的 mock 连接（上轮失败残留）
st, conns = req('/workspace/model-connections', token=tk)
for c in conns['data']['connections']:
    if c['name'].startswith('__E2E mock'):
        st0, r0 = req('/workspace/model-connections/%s' % c['id'], token=tk, method='DELETE')
        print('0) 清理残留连接 %s: %s' % (c['id'], r0.get('code')))

# 1) 创建临时连接（白名单允许 127.0.0.1）
st, conn = req('/workspace/model-connections', {
    'name': '__E2E mock vision', 'provider': 'mock', 'baseUrl': 'http://127.0.0.1:18099/v1',
    'apiKey': 'test-key', 'models': ['mock-vision'], 'allowAuxiliary': False,
}, token=tk)
conn_id = (conn.get('data') or {}).get('id')
ok('1) 临时 mock 连接创建', st == 200 and conn.get('code') == 'SUCCESS' and conn_id,
   'http=%s code=%s id=%s' % (st, conn.get('code'), conn_id))
if not conn_id:
    raise SystemExit(1)

# 1b) 创建临时 Agent 绑定 mock 连接（Agent 绑定连接优先于用户最新，不动真实连接）
st, agent = req('/workspace/agents', {
    'name': '__E2E mock agent', 'description': '临时验证用',
    'instruction': '你是助手。', 'strategy': 'fixed',
    'modelConnectionId': conn_id,
}, token=tk)
agent_id = (agent.get('data') or {}).get('id')
ok('1b) 临时 Agent 创建并绑定 mock 连接', st == 200 and agent_id, 'code=%s id=%s' % (agent.get('code'), agent_id))
if not agent_id:
    st_d, r_d = req('/workspace/model-connections/%s' % conn_id, token=tk, method='DELETE')
    raise SystemExit(1)

# 2) 带图发起运行（fixed 单轮，走临时 Agent → mock 连接）
st, run = req('/agent/runs', {'input': '图里是什么？',
                              'agentId': agent_id,
                              'strategy': 'fixed',
                              'clientRequestId': 'v49mock_' + str(int(time.time())),
                              'images': [data_url]}, token=tk)
rid = (run.get('data') or {}).get('runId')
ok('2) 带图运行创建', st == 200 and rid, 'code=%s runId=%s' % (run.get('code'), rid))

status = None
for i in range(40):
    time.sleep(2)
    st, snap = req('/agent/runs/%s' % rid, token=tk)
    status = snap['data']['status']
    if status in {'COMPLETED', 'FAILED', 'CANCELLED', 'TIMEOUT'}:
        break
ok('2) 运行 COMPLETED', status == 'COMPLETED', 'status=%s output=%s' % (
    status, (snap['data'].get('outputText') or '')[:60]))

# 3) 校验 mock 抓到的请求体（取第 0 份 = 主连接请求；后续份是 fallback 回落的）
time.sleep(1)
import glob
captures = sorted(glob.glob(CAPTURE_PATH.replace('.json', '_*.json')))
print('   capture 份数:', len(captures))
if not captures:
    ok('3) mock 收到请求', False, 'no capture — 主连接可能没被调到')
    img_parts = []
else:
    with open(captures[0], encoding='utf-8') as f:
        captured = json.loads(f.read())
    messages = captured.get('messages') or []
    img_parts = []
    for m in messages:
        content = m.get('content')
        if isinstance(content, list):
            for part in content:
                if isinstance(part, dict) and part.get('type') == 'image_url':
                    img_parts.append(part)
ok('3) mock 请求含 image_url 部件', len(img_parts) == 1,
   'image_parts=%s total_msgs=%s' % (len(img_parts), len(messages)))
if img_parts:
    url = img_parts[0].get('image_url', {}).get('url', '')
    ok('3) image_url 是完整 data URL（base64 一致）', url == data_url,
       'len=%s prefix=%s' % (len(url), url[:40]))

# 4) 清理：删临时 Agent + 删临时连接
st3, r3 = req('/workspace/agents/%s' % agent_id, token=tk, method='DELETE')
print('4) 清理临时 Agent %s: %s' % (agent_id, r3.get('code')))
st, d = req('/workspace/model-connections/%s' % conn_id, token=tk, method='DELETE')
ok('4) 清理临时连接', st == 200 and d.get('code') == 'SUCCESS', 'code=%s' % d.get('code'))

print('\n===== v4.9 mock E2E: PASS=%d FAIL=%d =====' % (len(PASS), len(FAIL)))
for name, detail in FAIL:
    print('  FAILED:', name, '|', detail)
server.shutdown()
raise SystemExit(1 if FAIL else 0)
