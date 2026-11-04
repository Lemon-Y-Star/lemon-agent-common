# -*- coding: utf-8 -*-
"""v4.5.9 异步跟进 E2E：短超时(10s)强制 run_wf 返回 RUNNING，验证主 Agent 调 check_workflow_run 跟进。"""
import io
import json
import re
import time
import urllib.request

BASE = 'http://localhost:18090/api/v1'

def req(path, data=None, token=None, method=None, timeout=30):
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(BASE + path, data=body, method=method or ('POST' if body else 'GET'))
    r.add_header('Content-Type', 'application/json')
    if token:
        r.add_header('Authorization', 'Bearer ' + token)
    return json.loads(urllib.request.urlopen(r, timeout=timeout).read())

tk = req('/auth/login', {'username': 'admin', 'password': 'lemon123456'})['data']['token']

# ---- 1) 给指挥官授权 check_workflow_run（全查全更）----
cur = req('/workspace/agents?page=1&size=50', token=tk)['data']
a35 = next(a for a in cur['mine'] if a['id'] == 35)
payload = {k: a35.get(k) for k in ['name', 'description', 'instruction', 'tools', 'strategy',
                                   'historyTurns', 'openingStatement', 'suggestedQuestions',
                                   'modelConnectionId', 'knowledgeDocIds']}
payload = {k: v for k, v in payload.items() if v is not None}
payload['tools'] = ['run_wf_12', 'run_wf_13', 'check_workflow_run', 'search_docs', 'current_time']
req('/workspace/agents/35', payload, token=tk, method='PUT')
print('授权已更新:', payload['tools'])

# ---- 2) 发起协同（10s 超时 → run_wf 必返 RUNNING → 主 Agent 应调 check_workflow_run）----
run = req('/agent/runs', {
    'agentId': 35,
    'input': '订单服务频繁 Full GC，堆内存不高，接口超时增多，帮我定位并给出处置方案',
    'strategy': 'auto',
    'clientRequestId': 'async_follow_' + str(int(time.time())),
}, token=tk)['data']
rid = run['runId']
print('回归运行:', rid)

import http.client
terminal = {'COMPLETED', 'FAILED', 'BLOCKED', 'CANCELLED', 'TIMEOUT'}
status = None
for i in range(150):
    time.sleep(3)
    snap = req(f'/agent/runs/{rid}', token=tk)['data']
    status = snap['status']
    if status in terminal:
        print(f'[{(i + 1) * 3}s] 终态: {status}')
        break

print('RID:', rid)
