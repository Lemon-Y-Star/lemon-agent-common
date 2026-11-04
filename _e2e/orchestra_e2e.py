# -*- coding: utf-8 -*-
"""主 Agent 协同 E2E：指挥官先后调用两个子工作流，验证剥 think 后的完整产出。"""
import json
import time
import urllib.request

BASE = 'http://localhost:18090/api/v1'

def req(path, data=None, token=None, timeout=30):
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(BASE + path, data=body, method='POST' if body else 'GET')
    r.add_header('Content-Type', 'application/json')
    if token:
        r.add_header('Authorization', 'Bearer ' + token)
    return json.loads(urllib.request.urlopen(r, timeout=timeout).read())

tk = req('/auth/login', {'username': 'admin', 'password': 'lemon123456'})['data']['token']

run = req('/agent/runs', {
    'agentId': 35,
    'input': '订单服务从 14:00 开始频繁 Full GC，但堆内存看起来不高，接口超时明显增多，帮我定位并给出处置方案',
    'strategy': 'auto',
    'clientRequestId': 'orchestra_e2e_v2_' + str(int(time.time())),
}, token=tk)['data']
rid = run['runId']
print('主运行:', rid)

terminal = {'COMPLETED', 'FAILED', 'BLOCKED', 'CANCELLED', 'TIMEOUT'}
status = None
for i in range(170):
    time.sleep(3)
    snap = req(f'/agent/runs/{rid}', token=tk)['data']
    status = snap['status']
    if status in terminal:
        print(f'[{(i + 1) * 3}s] 终态: {status}')
        break

out = snap.get('outputText') or ''
# 剥 think 看正文
think_end = out.find('</think>')
body = out[think_end + 8:].strip() if think_end >= 0 else out
print('--- 正文（前 1300 字）---')
print(body[:1300])
print('---')
print('正文长度:', len(body), '| 含「故障指挥报告」:', '故障指挥报告' in body,
      '| 含根因:', ('根因' in body), '| 含处置预案:', ('处置' in body))
