# -*- coding: utf-8 -*-
"""v4.5.4 START 输入表单 UI E2E：设计器 + 试运行表单 + 对话页表单弹窗。"""
import json
import sys
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:5173'
OUT = r'Y:\面试\lemon-agent-common\_e2e\v454_form_results.json'
results = []

def check(name, cond, detail=''):
    results.append({'name': name, 'ok': bool(cond), 'detail': detail})
    print(('PASS' if cond else 'FAIL'), name, ('| ' + detail if detail else ''))

with sync_playwright() as p:
    browser = p.chromium.launch(channel='msedge', headless=True)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    js_errors = []
    page.on('pageerror', lambda e: js_errors.append(str(e)))

    # ---------- 登录 ----------
    page.goto(BASE + '/login')
    page.fill('input[type="text"], input[name="username"]', 'admin')
    page.fill('input[type="password"]', 'lemon123456')
    page.click('button[type="submit"]')
    page.wait_for_url('**/chat**', timeout=15000)
    page.goto(BASE + '/agents/34/graph')
    page.wait_for_selector('.ge-canvas', timeout=15000)
    check('登录并打开画布', True)

    # ---------- 1) 检查器：START 表单设计器 ----------
    start_node = page.locator('.ge-node', has_text='开始').first
    start_node.click()
    page.wait_for_timeout(400)
    designer = page.locator('.ff-row')
    n_rows = designer.count()
    check('设计器出现 3 行字段', n_rows == 3, f'rows={n_rows}')

    # 字段值断言（city/days/notes）
    first_name = page.locator('.ff-grid input').first.input_value()
    check('第一行字段名=city', first_name == 'city', first_name)

    # 修改 notes 的默认值为「夜间高发，已持续一周」再改回来（验证可编辑且不破坏保存）
    notes_default = page.locator('.ff-grid input[placeholder="默认值"]').nth(2)
    notes_default.fill('夜间高发')
    page.wait_for_timeout(200)

    # ---------- 2) 保存（v2 → v3） ----------
    save_btn = page.get_by_role('button', name='保存')
    check('保存按钮可用', save_btn.is_enabled())
    save_btn.click()
    page.wait_for_selector('.tag:has-text("v3")', timeout=10000)
    check('保存成功出版本 v3', True)

    # ---------- 3) 试运行：表单收集 + 默认值预填 ----------
    page.get_by_role('button', name='试运行').click()
    page.wait_for_timeout(400)
    check('试运行弹窗出现开始节点表单', page.locator('.ff-ask').count() == 3)
    days_val = page.locator('.ff-ask input[type="number"]').input_value()
    check('days 默认值预填为 3', days_val == '3', days_val)

    # 必填校验：清空 city 点开始 → 前端拦截
    city_input = page.locator('.ff-ask input').first
    city_input.fill('')
    page.get_by_role('button', name='开始运行').click()
    page.wait_for_timeout(300)
    err_text = page.locator('.status-line[role="status"]').last.text_content() or ''
    check('前端拦截必填为空', '必填' in err_text, err_text.strip()[:60])

    # 填好提交
    city_input.fill('杭州')
    page.locator('.ff-ask input[type="number"]').fill('2')
    page.locator('.ff-ask input').nth(2).fill('凌晨发生')
    page.get_by_role('button', name='开始运行').click()
    page.wait_for_selector('text=运行中', timeout=10000)
    check('试运行已发起', True)

    # 等待终态（最多 120s）
    done = False
    for _ in range(48):
        page.wait_for_timeout(2500)
        if page.locator('text=最终输出').count() > 0 or page.locator('.tag.err:has-text("FAILED")').count() > 0:
            done = True
            break
    check('试运行到达终态', done)
    # START 节点染色 + 染色输出含表单值
    start_card = page.locator('.ge-node', has_text='开始').first
    cls = start_card.get_attribute('class') or ''
    check('START 节点染色 DONE', 'run-done' in cls, cls)

    # 关弹窗
    close_btn = page.get_by_role('button', name='关闭')
    if close_btn.count() > 0:
        close_btn.click()

    # ---------- 4) 对话页：表单弹窗收集 ----------
    page.goto(BASE + '/chat')
    page.wait_for_timeout(1200)
    # 选 Agent 34
    agent_select = page.locator('select').first
    options = agent_select.locator('option').all_text_contents()
    target_idx = None
    for i, t in enumerate(options):
        if 'SRE' in t and '流水线' in t:
            target_idx = i
            break
    if target_idx is not None:
        agent_select.select_option(index=target_idx)
        page.wait_for_timeout(1200)  # 等 fetchGraph 拿 startForm
        page.fill('textarea', '帮我看下线上告警')
        page.locator('textarea').press('Enter')
        page.wait_for_selector('text=输入表单', timeout=8000)
        check('对话发送弹出输入表单', True)
        # days 默认值预填
        days2 = page.locator('.modal input[type="number"]').input_value()
        check('对话弹窗 days 预填 3', days2 == '3', days2)
        # 必填拦截：city 留空 → toast
        page.get_by_role('button', name='发送').click()
        page.wait_for_timeout(400)
        check('对话弹窗必填拦截 toast', '必填' in (page.locator('.toast, [class*="toast"]').last.text_content() or ''))
        # 填好发送
        page.locator('.modal input').first.fill('深圳')
        page.locator('.modal input[type="number"]').fill('4')
        page.get_by_role('button', name='发送').click()
        page.wait_for_timeout(1500)
        # custom 运行已发起：出现用户气泡 + 运行中
        check('对话 custom 运行已发起', page.locator('text=帮我看下线上告警').count() > 0)
    else:
        check('找到 Agent 34 选项', False, str(options))

    check('无 JS 报错', len(js_errors) == 0, '; '.join(js_errors[:3]))
    browser.close()

with open(OUT, 'w', encoding='utf-8') as f:
    json.dump(results, f, ensure_ascii=False, indent=2)
fails = [r for r in results if not r['ok']]
print(f'==== {len(results) - len(fails)}/{len(results)} passed ====')
sys.exit(1 if fails else 0)
