# -*- coding: utf-8 -*-
"""v4.8 E2E：多模型对比（真实并发调用 + 参数校验 + 越权拒绝）。"""
import json
import os
import time
import urllib.request
import urllib.error

BASE = os.environ.get('LEMON_BASE', 'http://localhost:18090/api/v1')
SKIP_REAL = os.environ.get('LEMON_SKIP_REAL', '') == '1'  # 线上验证可跳过真实 LLM 调用
PASS = []
FAIL = []

def ok(name, cond, detail=''):
    (PASS if cond else FAIL).append((name, detail))
    print(('  PASS ' if cond else '  FAIL ') + name + (' | ' + detail if detail else ''))

def req(path, data=None, token=None, method=None, timeout=200):
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(BASE + path, data=body, method=method or ('POST' if body else 'GET'))
    r.add_header('Content-Type', 'application/json')
    # 线上过 Cloudflare：默认 Python-urllib UA 会 403/1010
    r.add_header('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0.0.0')
    if token:
        r.add_header('Authorization', 'Bearer ' + token)
    try:
        resp = urllib.request.urlopen(r, timeout=timeout)
        return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())

_, login = req('/auth/login', {'username': 'admin', 'password': 'lemon123456'})
tk = login['data']['token']
print('login ok, BASE =', BASE)

# ============ 1) 列连接 ============
st, conns = req('/workspace/model-connections', token=tk)
enabled = [c for c in conns['data']['connections'] if c['enabled']]
print('1) 连接列表：', [(c['id'], c['name'], (c['models'] or ['?'])[0]) for c in conns['data']['connections']])
ok('1) 有启用连接', len(enabled) >= 1)

if not enabled:
    print('!!! 没有启用连接，后续用例无法执行')
    raise SystemExit(1)

# ============ 2) 参数校验：不真实调模型 ============
st, r = req('/workspace/model-connections/compare', {'query': '  ', 'connectionIds': [enabled[0]['id']]}, token=tk)
ok('2) 空问题被拒', st == 200 and r.get('code') == 'VALIDATION_ERROR', 'code=%s' % r.get('code'))

st, r = req('/workspace/model-connections/compare', {'query': 'hi', 'connectionIds': []}, token=tk)
ok('2) 空连接列表被拒', st == 200 and r.get('code') == 'VALIDATION_ERROR', 'code=%s' % r.get('code'))

st, r = req('/workspace/model-connections/compare', {'query': 'hi', 'connectionIds': [999999]}, token=tk)
ok('2) 不存在/非本人连接被拒', st == 200 and r.get('code') == 'VALIDATION_ERROR',
   'code=%s msg=%s' % (r.get('code'), r.get('message')))

if len(enabled) >= 1:
    st, r = req('/workspace/model-connections/compare',
                {'query': 'hi', 'connectionIds': [enabled[0]['id'], enabled[0]['id']]}, token=tk)
    ok('2) 重复连接被拒', st == 200 and r.get('code') == 'VALIDATION_ERROR', 'code=%s' % r.get('code'))

# ============ 3) 真实对比（本地跑；线上设 LEMON_SKIP_REAL=1 跳过） ============
if SKIP_REAL:
    print('3) 真实对比：线上跳过（LEMON_SKIP_REAL=1）')
else:
    pick = [c['id'] for c in enabled[:4]]
    t0 = time.time()
    st, r = req('/workspace/model-connections/compare', {
        'query': '用一句话解释什么是幂等性，并举一个分布式系统里的例子',
        'connectionIds': pick,
    }, token=tk, timeout=200)
    wall = time.time() - t0
    items = r.get('data') or []
    ok('3) 对比返回项数一致', st == 200 and r.get('code') == 'SUCCESS' and len(items) == len(pick),
       'http=%s code=%s items=%s wall=%.1fs' % (st, r.get('code'), len(items), wall))
    ok('3) 顺序与请求一致', [i['connectionId'] for i in items] == pick,
       '%s' % [i['connectionId'] for i in items])
    for it in items:
        name = '%s(%s)' % (it['name'], it['model'])
        if it.get('error'):
            print('  - %s 失败： %s' % (name, it['error']))
        else:
            print('  - %s ⏱%.1fs in %s / out %s tok | %s...' % (
                name, (it.get('latencyMs') or 0) / 1000, it['inputTokens'], it['outputTokens'],
                (it.get('content') or '')[:40].replace('\n', ' ')))
    succeeded = [i for i in items if not i.get('error')]
    ok('3) 至少一个连接返回内容', len(succeeded) >= 1,
       'ok=%s/%s' % (len(succeeded), len(items)))
    for it in succeeded:
        ok('3) %s 内容/耗时/tokens 齐' % it['name'],
           bool(it.get('content')) and bool(it.get('latencyMs')) and it['inputTokens'] > 0)

# ============ 汇总 ============
print('\n===== v4.8 E2E 汇总: PASS=%d FAIL=%d =====' % (len(PASS), len(FAIL)))
for name, detail in FAIL:
    print('  FAILED:', name, '|', detail)
raise SystemExit(1 if FAIL else 0)
