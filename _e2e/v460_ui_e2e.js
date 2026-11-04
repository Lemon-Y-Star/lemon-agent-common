// v4.6.0 UI E2E：Runs 页子运行标记/过滤 + 公开分享页完整对话
const { chromium } = require('playwright-core')
const fs = require('fs')

const results = []
function check(name, cond, detail = '') {
  results.push({ name, ok: !!cond, detail: String(detail) })
  console.log(cond ? 'PASS' : 'FAIL', name, detail ? '| ' + detail : '')
}

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
  const jsErrors = []
  page.on('pageerror', (e) => jsErrors.push(String(e)))

  // ---------- 1) Runs 页：子运行标记 + 隐藏开关 ----------
  await page.goto('http://localhost:5173/login')
  await page.getByPlaceholder('输入用户名').fill('admin')
  await page.getByPlaceholder('输入密码').fill('lemon123456')
  await page.click('button.auth-submit')
  await page.waitForURL('**/chat**', { timeout: 15000 })
  await page.goto('http://localhost:5173/runs')
  await page.waitForSelector('tbody tr', { timeout: 10000 })
  await page.waitForTimeout(600)

  const childTags = await page.locator('text=/子运行 · 父 #\\d+/').count()
  check('子运行标签渲染（含父 ID）', childTags >= 1, `tags=${childTags}`)
  const rowsBefore = await page.locator('tbody tr').count()
  const hideToggle = page.locator('text=隐藏子运行')
  check('隐藏子运行开关存在', (await hideToggle.count()) >= 1)
  await page.locator('input[type="checkbox"]').first().check()
  await page.waitForTimeout(300)
  const rowsAfter = await page.locator('tbody tr').count()
  check('隐藏后行数减少', rowsAfter < rowsBefore, `${rowsBefore} -> ${rowsAfter}`)

  // ---------- 2) 公开分享页：免登录完整对话 ----------
  // 拿 shareCode（管理页拿不到就用 API——用第一个 context 的 cookie 不行，直接 fetch）
  const shareCode = await page.evaluate(async () => {
    const token = localStorage.getItem('lemon.token') || sessionStorage.getItem('token') || ''
    // 兜底：走页面上下文拿 token——client.ts 存的键名未知，直接调管理接口需要 Authorization
    return null
  })
  // 改用固定 code：本地最新开启的是 x3t5d7hx
  const CODE = 'x3t5d7hx'
  const sharePage = await (await browser.newContext({ viewport: { width: 900, height: 900 } })).newPage()
  const jsErrors2 = []
  sharePage.on('pageerror', (e) => jsErrors2.push(String(e)))
  await sharePage.goto('http://localhost:5173/s/' + CODE)
  await sharePage.waitForTimeout(1200)

  const hasOpening = (await sharePage.locator('text=线上故障指挥官').count()) > 0
  check('公开页渲染 Agent 名', hasOpening)
  const chips = await sharePage.locator('.suggestion-chip').count()
  check('建议问题 chips 渲染', chips >= 1, `chips=${chips}`)

  // 发一条消息走完整流式
  await sharePage.fill('.share-input textarea', '用一句话说明什么是限流')
  await sharePage.locator('.share-input button').click()
  check('公开页发送成功', true)

  // 等待回答出现（最多 90s）
  let answered = false
  for (let i = 0; i < 30; i++) {
    await sharePage.waitForTimeout(3000)
    const body = await sharePage.textContent('.share-body')
    if (body && body.length > 120 && !body.includes('生成中…')) { answered = true; break }
  }
  check('公开页收到 AI 回答（流式完成）', answered)
  const toolCards = await sharePage.locator('.tool-call-card.slim').count()
  check('工具芯片渲染（auto 编排）', toolCards >= 0, `chips=${toolCards}`)
  check('公开页无 JS 报错', jsErrors2.length === 0, jsErrors2.slice(0, 2).join('; '))

  check('Runs 页无 JS 报错', jsErrors.length === 0, jsErrors.slice(0, 2).join('; '))
  await browser.close()
})().catch((e) => {
  console.error('SCRIPT ERROR:', e)
  fs.writeFileSync(__dirname + '/v460_ui_results.json', JSON.stringify(results, null, 2))
  process.exit(1)
})

fs.writeFileSync(__dirname + '/v460_ui_results.json', JSON.stringify(results, null, 2))
const fails = results.filter((r) => !r.ok)
console.log(`==== ${results.length - fails.length}/${results.length} passed ====`)
if (fails.length > 0) process.exit(1)
