# -*- coding: utf-8 -*-
"""v4.5.8 协同优化回归：会话过滤 / resultBrief 截断 / childRunId 透出 / 知识库绑定。"""
import json
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

# ---- E. 指挥官绑定 SRE 知识库（2=公共,4/5/6=SRE 手册）----
cur = req('/workspace/agents?page=1&size=50', token=tk)['data']
a35 = next(a for a in cur['mine'] if a['id'] == 35)
print('绑定前 kbDocIds:', a35.get('knowledgeDocIds'))
# 更新接口是无条件覆盖语义：全量查询后合并再 PUT
payload = {k: a35.get(k) for k in ['name', 'description', 'instruction', 'tools', 'strategy',
                                   'historyTurns', 'openingStatement', 'suggestedQuestions', 'modelConnectionId']}
payload['knowledgeDocIds'] = [2, 4, 5, 6]
payload = {k: v for k, v in payload.items() if v is not None}
upd = req('/workspace/agents/35', payload, token=tk, method='PUT')
print('绑定结果:', upd['data'].get('knowledgeDocIds'))

# ---- 发起协同运行 ----
run = req('/agent/runs', {
    'agentId': 35,
    'input': '文件服务磁盘 IO 打满，写入延迟飙升，帮我定位并给出处置方案',
    'strategy': 'auto',
    'clientRequestId': 'opt_regress_' + str(int(time.time())),
}, token=tk)['data']
rid = run['runId']
print('回归运行:', rid)

terminal = {'COMPLETED', 'FAILED', 'BLOCKED', 'CANCELLED', 'TIMEOUT'}
status = None
for i in range(120):
    time.sleep(3)
    snap = req(f'/agent/runs/{rid}', token=tk)['data']
    status = snap['status']
    if status in terminal:
        print(f'[{(i + 1) * 3}s] 终态: {status}')
        break

print('--- 回归结果 ---')
print('状态:', status)

# ---- 校验会话列表不再出现工具子运行 ----
sess = req('/agent/sessions?page=1&size=50', token=tk)['data']
leaked = [s for s in sess['items'] if (s.get('title') or '').startswith('工具子运行')]
print('会话侧栏泄漏子运行会话:', len(leaked), '(期望 0) | 会话总数:', len(sess['items']))
print('RID_FOR_CHECK:', rid)
