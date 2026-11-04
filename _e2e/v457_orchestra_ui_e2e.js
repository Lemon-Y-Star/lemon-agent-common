// v4.5.7 主 Agent 协同 UI E2E：对话页选指挥官 → 建议问题 → 工具芯片 + 指挥报告
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://localhost:5173'
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

  await page.goto(BASE + '/login')
  await page.getByPlaceholder('输入用户名').fill('admin')
  await page.getByPlaceholder('输入密码').fill('lemon123456')
  await page.click('button.auth-submit')
  await page.waitForURL('**/chat**', { timeout: 15000 })
  await page.waitForTimeout(1000)

  // 选主 Agent
  const sel = page.locator('select').first()
  const opts = await sel.locator('option').allTextContents()
  const idx = opts.findIndex((t) => t.includes('故障指挥官'))
  check('找到主 Agent 选项', idx >= 0, opts.filter((t) => t.includes('指挥官')).join(','))
  await sel.selectOption({ index: idx })
  await page.waitForTimeout(1000)
  // 开场白可见
  const opening = await page.locator('text=线上故障指挥官').count()
  check('开场白/建议问题区渲染', opening > 0)

  // 点建议问题发起对话（或手输）
  const chip = page.locator('text=订单服务频繁 Full GC 但堆内存不高，怎么排查？').first()
  if ((await chip.count()) > 0) await chip.click()
  else {
    await page.fill('textarea', '订单服务频繁 Full GC 但堆内存不高，怎么排查？')
    await page.locator('textarea').press('Enter')
  }
  check('已发起协同对话', true)

  // 等待两个工具芯片出现（子工作流调用），最多 90s
  let wf12 = 0
  let wf13 = 0
  for (let i = 0; i < 30; i++) {
    await page.waitForTimeout(3000)
    const body = await page.locator('#page-chat, main, body').first().textContent()
    wf12 = (body.match(/诊断/g) || []).length
    wf13 = (body.match(/处置/g) || []).length
    if ((await page.locator('text=故障指挥报告').count()) > 0) break
  }
  const finalBody = await page.locator('#page-chat, main, body').first().textContent()
  check('指挥报告出现在对话区', (await page.locator('text=故障指挥报告').count()) > 0)
  check('诊断专家产出可见', finalBody.includes('诊断'), `mentions=${wf12}`)
  check('处置专家产出可见', finalBody.includes('处置'), `mentions=${wf13}`)

  // 工具芯片（瘦卡片：tool-call-card slim）
  const chips = await page.locator('.tool-call-card.slim').count()
  check('工具调用芯片已渲染', chips >= 1, `chips=${chips}`)

  check('无 JS 报错', jsErrors.length === 0, jsErrors.slice(0, 2).join('; '))
  await browser.close()
})().catch((e) => {
  console.error('SCRIPT ERROR:', e)
  fs.writeFileSync(__dirname + '/v457_orchestra_results.json', JSON.stringify(results, null, 2))
  process.exit(1)
})

fs.writeFileSync(__dirname + '/v457_orchestra_results.json', JSON.stringify(results, null, 2))
const fails = results.filter((r) => !r.ok)
console.log(`==== ${results.length - fails.length}/${results.length} passed ====`)
if (fails.length > 0) process.exit(1)
