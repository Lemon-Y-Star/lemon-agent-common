# -*- coding: utf-8 -*-
"""修复工作流 13 处置预案的 CODE 节点源码（此前经命令行传输时 \n 被改写成 /n）。"""
import json
import urllib.request

BASE = 'http://localhost:18090/api/v1'

def req(path, data=None, token=None, method=None):
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(BASE + path, data=body, method=method or ('POST' if body else 'GET'))
    r.add_header('Content-Type', 'application/json')
    if token:
        r.add_header('Authorization', 'Bearer ' + token)
    return json.loads(urllib.request.urlopen(r, timeout=30).read())

tk = req('/auth/login', {'username': 'admin', 'password': 'lemon123456'})['data']['token']

# CODE 节点源码用行数组拼，不依赖字符串转义
code_lines = [
    "text = upstream[0] if upstream else ''",
    '# 按处置动作关键词评分：立即回滚最紧急',
    "if '立即回滚' in text:",
    "    score, level = 95, 'P0'",
    "elif '限流降级' in text:",
    "    score, level = 80, 'P1'",
    "elif '扩容' in text:",
    "    score, level = 65, 'P2'",
    'else:',
    "    score, level = 40, 'P3'",
    "print(f'处置优先级评分：{score}/100（{level}）')",
]
code = chr(10).join(code_lines)

g = req('/workspace/workflows/13/graph', token=tk)['data']
for n in g['nodes']:
    if n['type'] == 'CODE':
        n['code'] = code
saved = req('/workspace/workflows/13/graph', {'nodes': g['nodes'], 'edges': g['edges']}, token=tk, method='PUT')
print('WF-13 graph saved: v' + str(saved.get('data', saved).get('currentVersion')))

# 校验落库后的源码
check = req('/workspace/workflows/13/graph', token=tk)['data']
for n in check['nodes']:
    if n['type'] == 'CODE':
        print('CODE now has /n?', '/n' in n['code'], '| lines:', len(n['code'].split(chr(10))))
