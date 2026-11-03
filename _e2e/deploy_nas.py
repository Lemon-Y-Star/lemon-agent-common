# -*- coding: utf-8 -*-
"""NAS Dokploy 部署：触发 compose.deploy + 轮询 deployment.allByCompose 直到终态。"""
import json
import sys
import time
import urllib.request

TOKEN_PATH = r'C:\Users\50626\AppData\Roaming\WorkDaddy\client-tmp\workbuddy-cn\dk_token.txt'
DK = 'http://192.168.5.250:18787'
CID = '4iUHWmxoW5fjL9CfY1UCh'

dk = open(TOKEN_PATH, encoding='utf-8').read().strip()

def call(path, data=None):
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(DK + path, data=body, method='POST' if body else 'GET')
    r.add_header('Content-Type', 'application/json')
    r.add_header('x-api-key', dk)
    return json.loads(urllib.request.urlopen(r, timeout=30).read())

# 0) 部署前确认 compose 状态
one = call('/api/compose.one?composeId=' + CID)
print('composeStatus:', one.get('composeStatus'))

# 1) 触发部署
dep = call('/api/compose.deploy', {'composeId': CID})
print('deploy resp:', json.dumps(dep, ensure_ascii=False)[:200])

# 2) 轮询 deployment 列表（2.5s 一次，最多 12 分钟）
terminal = {'done', 'error', 'canceled'}
last_status = None
deadline = time.time() + 720
while time.time() < deadline:
    time.sleep(2.5)
    try:
        deps = call('/api/deployment.allByCompose?composeId=' + CID)
        items = deps if isinstance(deps, list) else deps.get('data', deps.get('deployments', []))
        if not items:
            print('no deployments yet'); continue
        d = items[0]
        status = str(d.get('status'))
        if status != last_status:
            print(time.strftime('%H:%M:%S'), 'status:', status, '|', str(d.get('logPath', ''))[-40:])
            last_status = status
        if status in terminal:
            print('FINAL:', status)
            break
    except Exception as e:
        print('poll err:', e)

sys.exit(0 if last_status == 'done' else 1)
