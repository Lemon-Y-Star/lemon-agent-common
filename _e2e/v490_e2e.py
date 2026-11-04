# -*- coding: utf-8 -*-
"""v4.9 E2E：对话图片输入（多模态）——带图发起运行 + 校验 + 消息回读。"""
import base64
import io
import json
import os
import struct
import time
import urllib.request
import urllib.error
import zlib

BASE = os.environ.get('LEMON_BASE', 'http://localhost:18090/api/v1')
PASS = []
FAIL = []

def ok(name, cond, detail=''):
    (PASS if cond else FAIL).append((name, detail))
    print(('  PASS ' if cond else '  FAIL ') + name + (' | ' + detail if detail else ''))

def req(path, data=None, token=None, method=None, timeout=240):
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
    """生成 64x64 纯色 PNG（struct+zlib 手搓，无第三方依赖）。"""
    w, h = 64, 64
    raw = b''.join(b'\x00' + bytes(rgb) * w for _ in range(h))
    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    ihdr = struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)
    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', ihdr)
           + chunk(b'IDAT', zlib.compress(raw))
           + chunk(b'IEND', b''))
    return png

_, login = req('/auth/login', {'username': 'admin', 'password': 'lemon123456'})
tk = login['data']['token']
print('login ok, BASE =', BASE)

png_red = make_png((220, 38, 38))
data_url = 'data:image/png;base64,' + base64.b64encode(png_red).decode()
print('测试图大小（base64）:', len(data_url), 'chars')

# ============ 1) 参数校验（不调模型） ============
st, r = req('/agent/runs', {'input': 'hi', 'clientRequestId': 'v49_bad_' + str(int(time.time())),
                            'images': ['http://example.com/a.png']}, token=tk)
ok('1) 非 data URL 被拒', st == 200 and r.get('code') == 'VALIDATION_ERROR', 'code=%s' % r.get('code'))

st, r = req('/agent/runs', {'input': 'hi', 'clientRequestId': 'v49_bad_' + str(int(time.time())),
                            'images': [data_url] * 5}, token=tk)
ok('1) 超 4 张被拒', st == 200 and r.get('code') == 'VALIDATION_ERROR', 'code=%s' % r.get('code'))

st, r = req('/agent/runs', {'input': 'hi', 'clientRequestId': 'v49_bad_' + str(int(time.time())),
                            'images': ['data:image/png;base64,' + 'A' * 2_900_000]}, token=tk)
ok('1) 超大图被拒', st == 200 and r.get('code') == 'VALIDATION_ERROR', 'code=%s' % r.get('code'))

# ============ 2) 带图真实运行（1 张红图） ============
st, run = req('/agent/runs', {'agentId': 35, 'input': '这张图片是什么颜色？只回答颜色名，不要解释。',
                              'strategy': 'fixed',
                              'clientRequestId': 'v49_img_' + str(int(time.time())),
                              'images': [data_url]}, token=tk)
rid = run.get('data', {}).get('runId')
ok('2) 带图运行创建成功', st == 200 and run.get('code') == 'SUCCESS' and rid,
   'http=%s code=%s runId=%s' % (st, run.get('code'), rid))

if rid:
    terminal = {'COMPLETED', 'FAILED', 'BLOCKED', 'CANCELLED', 'TIMEOUT'}
    status = None
    for i in range(90):
        time.sleep(3)
        st, snap = req('/agent/runs/%s' % rid, token=tk)
        status = snap['data']['status']
        if status in terminal:
            break
    print('   运行终态:', status)
    ok('2) 运行 COMPLETED', status == 'COMPLETED', 'status=%s' % status)
    output = (snap['data'].get('outputText') or '')
    print('   模型回答:', output[:160].replace('\n', ' '))
    # 渠道兼容性观察（软断言）：DeepSeek 官方 API 无视觉模型，中转渠道行为不定——剥图 / 报错 / 识别
    if '红' in output or 'red' in output.lower():
        print('   ✓ 渠道识别出红色（支持视觉）')
    else:
        print('   ⚠ 当前渠道/模型未识别图片内容（大概率不支持视觉输入）——链路已由 mock 验证正确，换视觉模型即可')

    # ============ 3) 会话消息回读：USER 消息带 images ============
    st, detail = req('/agent/sessions/%s/messages' % run['data']['sessionId'], token=tk)
    msgs = detail.get('data', {}).get('messages') or []
    user_msgs = [m for m in msgs if m.get('role') == 'USER']
    last_user = user_msgs[-1] if user_msgs else {}
    imgs = last_user.get('images')
    ok('3) 消息接口回读附图', imgs and len(imgs) == 1 and imgs[0].startswith('data:image/png;base64,'),
       'images=%s' % (len(imgs) if imgs else 0))

# ============ 4) 纯文本运行不受影响（回归） ============
st, run2 = req('/agent/runs', {'agentId': 35, 'input': '用两个字回答：你好',
                               'strategy': 'fixed',
                               'clientRequestId': 'v49_txt_' + str(int(time.time()))}, token=tk)
rid2 = run2.get('data', {}).get('runId')
ok('4) 纯文本运行创建', st == 200 and rid2)
if rid2:
    terminal = {'COMPLETED', 'FAILED', 'BLOCKED', 'CANCELLED', 'TIMEOUT'}
    status2 = None
    for i in range(60):
        time.sleep(3)
        st, snap2 = req('/agent/runs/%s' % rid2, token=tk)
        status2 = snap2['data']['status']
        if status2 in terminal:
            break
    ok('4) 纯文本运行 COMPLETED', status2 == 'COMPLETED', 'status=%s' % status2)

print('\n===== v4.9 E2E 汇总: PASS=%d FAIL=%d =====' % (len(PASS), len(FAIL)))
for name, detail in FAIL:
    print('  FAILED:', name, '|', detail)
raise SystemExit(1 if FAIL else 0)
