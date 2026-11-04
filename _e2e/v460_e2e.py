# -*- coding: utf-8 -*-
"""v4.6.0 E2E：父子标记 / 标注闭环 / 分享链接（免登录+口令+频控）。"""
import json
import time
import urllib.request
import urllib.error

BASE = 'http://localhost:18090/api/v1'

def req(path, data=None, token=None, method=None, timeout=60, raw=False):
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(BASE + path, data=body, method=method or ('POST' if body else 'GET'))
    r.add_header('Content-Type', 'application/json')
    if token:
        r.add_header('Authorization', 'Bearer ' + token)
    try:
        resp = urllib.request.urlopen(r, timeout=timeout)
        text = resp.read().decode()
        return resp.status, (text if raw else json.loads(text))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())

ok, login = req('/auth/login', {'username': 'admin', 'password': 'lemon123456'})
tk = login['data']['token']

# ============ 1) 父子标记 ============
st, runs = req('/agent/runs?page=1&size=50', token=tk)
items = runs['data']['items']
children = [r for r in items if r.get('runKind') == 'child']
mains = [r for r in items if r.get('runKind') == 'main']
print('1) 父子标记：main', len(mains), '/ child', len(children))
child = next((r for r in children if r.get('parentRunId')), None)
print('   子运行示例:', child['runId'] if child else None, '-> 父', child.get('parentRunId') if child else None)

# ============ 2) 标注闭环：发起协同 → 差评 + 标准答案 → 知识库文档 ============
st, run = req('/agent/runs', {'agentId': 35, 'input': '什么是两阶段提交 2PC 的阻塞问题？',
    'strategy': 'auto', 'clientRequestId': 'annot_e2e_' + str(int(time.time()))}, token=tk)
rid = run['data']['runId']
print('2) 标注闭环：回归运行', rid)
terminal = {'COMPLETED', 'FAILED', 'BLOCKED', 'CANCELLED', 'TIMEOUT'}
for i in range(100):
    time.sleep(3)
    st, snap = req(f'/agent/runs/{rid}', token=tk)
    if snap['data']['status'] in terminal:
        print('   运行终态:', snap['data']['status'])
        break

st, fb = req(f'/workspace/artifacts/{rid}/feedback', {
    'rating': 'DOWN', 'comment': '答案没讲清楚协调者单点',
    'expectedAnswer': '2PC 的阻塞问题：协调者在 Prepare 后崩溃，所有参与者会无限期锁定资源等待 Commit/Rollback 指令，'
                      '导致资源不可用。解法：3PC 引入超时+CanCommit 预询，或改用 Paxos/Raft 类共识协议。'
}, token=tk)
print('   差评标注:', fb['data']['rating'], '| createdDocId:', fb['data'].get('createdDocId'))
doc_id = fb['data'].get('createdDocId')

# ============ 3) 分享链接 ============
st, share = req('/workspace/agents/35/share', {'enabled': True, 'accessCode': ''}, token=tk)
code = share['data']['shareCode']
print('3) 分享开启：code =', code)

# 3a) 免登录 info（无 token）
info_url = BASE + f'/share/{code}/info'
resp = urllib.request.urlopen(info_url, timeout=20)
info = json.loads(resp.read())['data']
print('   免登录 info:', info['name'], '| 开场白:', bool(info.get('openingStatement')), '| 建议问题:', len(info.get('suggestedQuestions') or []))

# 3b) 免登录发起运行 + SSE 读几帧
st, srun = req(f'/share/{code}/runs', {'message': '接口超时怎么排查？'}, method='POST')
srun_id = srun['data']['runId']
print('   免登录运行:', srun_id, '| sessionId:', srun['data']['sessionId'])

# SSE 读取（读 15s 就断开）
conn = urllib.request.urlopen(BASE + f'/share/{code}/runs/{srun_id}/events', timeout=15)
frames = 0
start = time.time()
try:
    while time.time() - start < 15:
        line = conn.readline().decode()
        if line.startswith('event:') or line.startswith('data:'):
            frames += 1
except Exception:
    pass
conn.close()
print('   SSE 帧（15s 采样）:', frames, '（>0 即流式可用）')

# 3c) 关闭分享 → info 应 404
req('/workspace/agents/35/share', {'enabled': False}, token=tk, method='POST')
try:
    urllib.request.urlopen(info_url, timeout=20)
    print('   关闭后 info 仍可访问（FAIL）')
except urllib.error.HTTPError as e:
    print('   关闭后 info:', e.code, '(期望 404)')

# 3d) 重新开启 + 口令保护 → 无口令 403、对口令 200
req('/workspace/agents/35/share', {'enabled': True, 'accessCode': 'abc123'}, token=tk, method='POST')
try:
    urllib.request.urlopen(info_url, timeout=20)
    print('   有口令无凭据访问: 放行（FAIL）')
except urllib.error.HTTPError as e:
    print('   有口令无凭据访问:', e.code, '(期望 403)')
resp = urllib.request.urlopen(info_url + '?accessCode=abc123', timeout=20)
print('   带正确口令:', json.loads(resp.read())['code'])

# 收尾：关闭分享，清理标注文档
req('/workspace/agents/35/share', {'enabled': False}, token=tk, method='POST')
if doc_id:
    req(f'/workspace/knowledge/{doc_id}', token=tk, method='DELETE')
    print('清理：标注文档已删', doc_id)
print('==== DONE ====')
