// v4.5.6 知识库 UI E2E：详情弹窗 + 双分页 + 滚动容器（API 造临时文档凑页，测完清理）
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://localhost:5173'
const API = 'http://localhost:18090/api/v1'
const results = []
function check(name, cond, detail = '') {
  results.push({ name, ok: !!cond, detail: String(detail) })
  console.log(cond ? 'PASS' : 'FAIL', name, detail ? '| ' + detail : '')
}

async function api(path, data, token, method) {
  const r = await fetch(API + path, {
    method: method || (data ? 'POST' : 'GET'),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: data ? JSON.stringify(data) : undefined,
  })
  return r.json()
}

;(async () => {
  // ---------- 0) 造临时文档凑分页 ----------
  const login = await api('/auth/login', { username: 'admin', password: 'lemon123456' })
  const tk = login.data.token
  const before = await api('/workspace/knowledge?page=1&size=1', null, tk)
  const need = 11 - before.data.total
  const created = []
  for (let i = 0; i < need; i++) {
    const r = await api('/workspace/knowledge', {
      title: `e2e临时文档_${Date.now()}_${i}`,
      content: `## 小节标题 ${i}\n\n这是一段测试正文，用于验证详情弹窗的 Markdown 渲染与分页，编号 ${i}。\n\n- 要点 A\n- 要点 B`,
    }, tk)
    if (r.data && r.data.id) created.push(r.data.id)
  }
  console.log(`临时文档创建 ${created.length} 篇（原 ${before.data.total} → 目标 11）`)

  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
  const jsErrors = []
  page.on('pageerror', (e) => jsErrors.push(String(e)))

  await page.goto(BASE + '/login')
  await page.getByPlaceholder('输入用户名').fill('admin')
  await page.getByPlaceholder('输入密码').fill('lemon123456')
  await page.click('button.auth-submit')
  await page.waitForURL('**/chat**', { timeout: 15000 })
  await page.goto(BASE + '/knowledge')
  await page.waitForSelector('.kb-docs', { timeout: 10000 })
  await page.waitForTimeout(800)

  // ---------- 1) 文档分页 ----------
  const docCard = page.locator('.card').first()
  const pagerInfo = (await docCard.locator('span.note').first().textContent()) || ''
  check('文档分页器显示 共11条', pagerInfo.includes('共 11 条'), pagerInfo.trim())
  const docCount = await docCard.locator('.kb-docs .kb-doc').count()
  check('第一页文档条数 = 8', docCount === 8, `count=${docCount}`)
  const firstTitle = await docCard.locator('.kb-doc b').first().textContent()
  await docCard.getByRole('button', { name: '下一页' }).click()
  await page.waitForTimeout(700)
  const firstTitle2 = await docCard.locator('.kb-doc b').first().textContent()
  check('翻页后内容变化', firstTitle !== firstTitle2, `${(firstTitle || '').slice(0, 12)} -> ${(firstTitle2 || '').slice(0, 12)}`)
  const prevDisabled = await docCard.locator('.kb-doc').first()
  void prevDisabled
  await docCard.getByRole('button', { name: '上一页' }).click()
  await page.waitForTimeout(700)

  // ---------- 2) 文档详情弹窗 ----------
  await docCard.locator('.kb-doc').first().getByRole('button', { name: '详情' }).click()
  await page.waitForSelector('.kb-detail-body', { timeout: 8000 })
  await page.waitForTimeout(600)
  const bodyText = (await page.locator('.kb-detail-body').textContent()) || ''
  check('详情正文非空（全文渲染）', bodyText.trim().length > 30, `len=${bodyText.trim().length}`)
  const hasMd = (await page.locator('.kb-detail-body h2, .kb-detail-body li, .kb-detail-body p').count()) > 0
  check('Markdown 结构化渲染', hasMd)
  // Modal 组件关闭：右上角 X 或遮罩
  const closeBtn = page.locator('.modal').getByRole('button').first()
  if ((await closeBtn.count()) > 0) await closeBtn.click()
  await page.waitForTimeout(300)
  if ((await page.locator('.modal').count()) > 0) {
    await page.mouse.click(30, 450)
    await page.waitForTimeout(300)
  }
  check('详情弹窗可关闭', (await page.locator('.modal').count()) === 0)

  // ---------- 3) 未命中队列分页（62 条 → 7 页） ----------
  const missCard = page.locator('.card').nth(2)
  const missInfo = (await missCard.locator('span.note').first().textContent()) || ''
  check('未命中分页器显示总数', /共 \d+ 条/.test(missInfo), missInfo.trim())
  const missBefore = (await missCard.locator('.kb-doc span').first().textContent()) || ''
  await missCard.getByRole('button', { name: '下一页' }).click()
  await page.waitForTimeout(700)
  const missAfter = (await missCard.locator('.kb-doc span').first().textContent()) || ''
  check('未命中翻页内容变化', missBefore !== missAfter, `${(missBefore || '').slice(0, 10)} -> ${(missAfter || '').slice(0, 10)}`)

  // ---------- 4) 滚动容器 ----------
  const scrollable = await page.evaluate(() => {
    return [...document.querySelectorAll('.kb-scroll')].map((e) => ({ sh: e.scrollHeight, ch: e.clientHeight }))
  })
  check('kb-scroll 容器存在 ≥2 个', scrollable.length >= 2, `n=${scrollable.length}`)
  check('至少一个容器内容超高可内滚', scrollable.some((s) => s.sh > s.ch), JSON.stringify(scrollable))

  check('无 JS 报错', jsErrors.length === 0, jsErrors.slice(0, 2).join('; '))
  await browser.close()

  // ---------- 5) 清理临时文档 ----------
  for (const id of created) {
    await api(`/workspace/knowledge/${id}`, null, tk, 'DELETE')
  }
  const after = await api('/workspace/knowledge?page=1&size=1', null, tk)
  console.log(`清理完成，文档总数回落后 total=${after.data.total}`)
})().catch((e) => {
  console.error('SCRIPT ERROR:', e)
  fs.writeFileSync(__dirname + '/v456_kb_results.json', JSON.stringify(results, null, 2))
  process.exit(1)
})

fs.writeFileSync(__dirname + '/v456_kb_results.json', JSON.stringify(results, null, 2))
const fails = results.filter((r) => !r.ok)
console.log(`==== ${results.length - fails.length}/${results.length} passed ====`)
if (fails.length > 0) process.exit(1)
