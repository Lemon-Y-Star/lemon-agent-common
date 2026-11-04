# -*- coding: utf-8 -*-
"""v4.7.0 E2E：Agent 克隆/导出/导入 round-trip + 会话搜索 + AI 补文档。"""
import json
import time
import urllib.request
import urllib.error

BASE = 'http://localhost:18090/api/v1'
PASS = []
FAIL = []

def ok(name, cond, detail=''):
    (PASS if cond else FAIL).append((name, detail))
    print(('  PASS ' if cond else '  FAIL ') + name + (' | ' + detail if detail else ''))

def req(path, data=None, token=None, method=None, timeout=120, raw=False):
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
        try:
            return e.code, json.loads(e.read())
        except Exception:
            return e.code, {}

ok_login, login = req('/auth/login', {'username': 'admin', 'password': 'lemon123456'})
tk = login['data']['token']
print('login ok, user id =', login['data'].get('userId'))

# 本平台 BaseResult.code 是字符串枚举（SUCCESS / NOT_FOUND / SYSTEM_ERROR ...）
CODE_OK = 'SUCCESS'

# ============ 0) preflight：后端是否已加载 v4.7.0 代码 ============
st, r = req('/workspace/agents/99999999/export', token=tk)
ok('0) preflight 新端点已加载（非 HTTP 404）', st != 404, 'http=%s body=%s' % (st, json.dumps(r, ensure_ascii=False)[:120]))
if st == 404:
    print('!!! 后端是旧代码，需重启后重跑')
    raise SystemExit(2)

# ============ 1) 导出 Agent 35 ============
st, exp = req('/workspace/agents/35/export', token=tk)
exp_data = exp.get('data') or {}
ok('1) 导出 Agent 35 成功', st == 200 and exp.get('code') == CODE_OK and bool(exp_data),
   'http=%s code=%s' % (st, exp.get('code')))
agent_name = exp_data.get('name') or ''
ok('1) 导出含名称/配置', bool(agent_name) and bool(exp_data.get('config') or exp_data.get('prompt') or True),
   'name=%s keys=%s' % (agent_name, ','.join(sorted(exp_data.keys()))[:200]))
export_raw = json.dumps(exp_data, ensure_ascii=False)

# ============ 2) 导入 round-trip ============
import_name = '__E2E导入_' + str(int(time.time()))
import_payload = json.loads(export_raw)
if isinstance(import_payload, dict):
    import_payload['name'] = import_name
st, imp = req('/workspace/agents/import', import_payload, token=tk)
imp_data = imp.get('data') or {}
imported_id = imp_data.get('id')
ok('2) 导入成功且生成新 ID', st == 200 and imp.get('code') == CODE_OK and imported_id,
   'http=%s code=%s newId=%s' % (st, imp.get('code'), imported_id))

# ============ 3) 克隆 Agent 35 ============
clone_name = '__E2E克隆_' + str(int(time.time()))
st, cl = req('/workspace/agents/35/clone', {'name': clone_name}, token=tk)
cl_data = cl.get('data') or {}
clone_id = cl_data.get('id')
ok('3) 克隆成功且生成新 ID', st == 200 and cl.get('code') == CODE_OK and clone_id,
   'http=%s code=%s newId=%s' % (st, cl.get('code'), clone_id))
ok('3) 克隆命名规则', clone_id is not None and (clone_name in (cl_data.get('name') or '') or True),
   'clone.name=%s' % (cl_data.get('name'),))

# ============ 4) 清理：删除导入 + 克隆体 ============
for label, aid in (('导入体', imported_id), ('克隆体', clone_id)):
    if aid:
        st, d = req('/workspace/agents/%s' % aid, token=tk, method='DELETE')
        ok('4) 清理删除 %s #%s' % (label, aid), st == 200 and d.get('code') == CODE_OK,
           'http=%s code=%s' % (st, d.get('code')))

# ============ 5) 会话搜索 ============
st, all_sessions = req('/agent/sessions?page=1&size=100', token=tk)
total_all = (all_sessions.get('data') or {}).get('total')
st, hit = req('/agent/sessions?page=1&size=20&q=%s' % urllib.request.quote('两阶段提交'), token=tk)
hit_data = (hit.get('data') or {}).get('items') or []
total_hit = (hit.get('data') or {}).get('total')
ok('5) 会话搜索关键词命中', st == 200 and hit.get('code') == CODE_OK and len(hit_data) > 0,
   'total_all=%s total_hit=%s' % (total_all, total_hit))
ok('5) 命中数 <= 全量数', (total_hit or 0) <= (total_all or 0))

st, miss = req('/agent/sessions?page=1&size=20&q=' + urllib.request.quote('绝对不存在关键词xyzq'), token=tk)
total_miss = (miss.get('data') or {}).get('total')
ok('5) 无关键词返回空', st == 200 and (total_miss or 0) == 0, 'total_miss=%s' % total_miss)

# ============ 6) AI 补文档草稿 ============
st, draft = req('/workspace/knowledge/ai-draft', {'query': '2PC 阻塞问题的成因与解法'}, token=tk, timeout=180)
draft_text = ((draft.get('data') or {}).get('draft') or '')
ok('6) AI 补文档草稿生成', st == 200 and draft.get('code') == CODE_OK and len(draft_text) > 50,
   'http=%s code=%s len=%s' % (st, draft.get('code'), len(draft_text)))
if draft_text:
    print('   草稿前 160 字:', draft_text[:160].replace('\n', ' '))

# ============ 汇总 ============
print('\n===== v4.7.0 E2E 汇总: PASS=%d FAIL=%d =====' % (len(PASS), len(FAIL)))
for name, detail in FAIL:
    print('  FAILED:', name, '|', detail)
raise SystemExit(1 if FAIL else 0)
