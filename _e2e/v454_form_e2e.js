// v4.5.4 START 输入表单 UI E2E：设计器 + 试运行表单 + 对话页表单弹窗（playwright-core + msedge）
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://localhost:5173'
const OUT = __dirname + '/v454_form_results.json'
const results = []

function check(name, cond, detail = '') {
  results.push({ name, ok: !!cond, detail: String(detail) })
  console.log(cond ? 'PASS' : 'FAIL', name, detail ? '| ' + detail : '')
}

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const jsErrors = []
  page.on('pageerror', (e) => jsErrors.push(String(e)))

  // ---------- 登录 ----------
  await page.goto(BASE + '/login')
  await page.getByPlaceholder('输入用户名').fill('admin')
  await page.getByPlaceholder('输入密码').fill('lemon123456')
  await page.click('button.auth-submit')
  await page.waitForURL('**/chat**', { timeout: 15000 })
  await page.goto(BASE + '/agents/34/graph')
  await page.waitForSelector('.ge-canvas', { timeout: 15000 })
  check('登录并打开画布', true)

  // ---------- 1) 检查器：START 表单设计器 ----------
  const startNode = page.locator('.ge-node', { hasText: '开始' }).first()
  await startNode.click()
  await page.waitForTimeout(400)
  const nRows = await page.locator('.ff-row').count()
  check('设计器出现 3 行字段', nRows === 3, `rows=${nRows}`)

  const firstName = await page.locator('.ff-grid input').first().inputValue()
  check('第一行字段名=city', firstName === 'city', firstName)

  // 改 notes 默认值（每次填唯一值：React 对相同值不触发 onChange，重复值会导致不置脏）
  const notesDefault = page.locator('.ff-grid input[placeholder="默认值"]').nth(2)
  const uniqueNote = '已持续一周' + String(Date.now()).slice(-4)
  await notesDefault.fill(uniqueNote)
  await page.waitForTimeout(200)
  check('编辑字段后置脏（未保存标签出现）', (await page.locator('.tag:has-text("未保存")').count()) > 0)

  // ---------- 2) 保存（版本号递增；每次保存生成新版本，不能写死） ----------
  const verBefore = await page.locator('.page-head .tag.gray').first().textContent()
  const saveBtn = page.getByRole('button', { name: '保存' })
  check('保存按钮可用', await saveBtn.isEnabled())
  await saveBtn.click()
  await page.waitForFunction(
    (prev) => {
      const el = document.querySelector('.page-head .tag.gray')
      return el && el.textContent !== prev
    },
    verBefore,
    { timeout: 10000 },
  )
  const verAfter = await page.locator('.page-head .tag.gray').first().textContent()
  check(`保存成功出版本 ${verAfter.trim()}（原 ${verBefore.trim()}）`, true)

  // ---------- 3) 试运行：表单收集 + 默认值预填 ----------
  await page.getByRole('button', { name: '试运行' }).click()
  await page.waitForTimeout(400)
  check('试运行弹窗出现开始节点表单', (await page.locator('.ff-ask').count()) === 3)
  const daysVal = await page.locator('.ff-ask input[type="number"]').inputValue()
  check('days 默认值预填为 3', daysVal === '3', daysVal)

  // 必填校验：先填 textarea（它有 required，原生校验先于我们的逻辑），再清空 city 点开始 → 前端拦截
  await page.locator('.modal textarea').fill('测试开始节点表单')
  const cityInput = page.locator('.ff-ask input').first()
  await cityInput.fill('')
  await page.getByRole('button', { name: '开始运行' }).click()
  await page.waitForTimeout(300)
  const errText = (await page.locator('.status-line[role="status"]').last().textContent()) || ''
  check('前端拦截必填为空', errText.includes('必填'), errText.trim().slice(0, 60))

  // 填好提交
  await cityInput.fill('杭州')
  await page.locator('.ff-ask input[type="number"]').fill('2')
  await page.locator('.ff-ask input').nth(2).fill('凌晨发生')
  await page.getByRole('button', { name: '开始运行' }).click()
  await page.waitForSelector('text=运行中', { timeout: 10000 })
  check('试运行已发起', true)

  // 等待终态（最多 120s）
  let done = false
  for (let i = 0; i < 48; i++) {
    await page.waitForTimeout(2500)
    if ((await page.locator('text=最终输出').count()) > 0 || (await page.locator('.tag.err:has-text("FAILED")').count()) > 0) {
      done = true
      break
    }
  }
  check('试运行到达终态', done)
  const startCard = page.locator('.ge-node', { hasText: '开始' }).first()
  const cls = (await startCard.getAttribute('class')) || ''
  check('START 节点染色 DONE', cls.includes('run-done'), cls)

  const closeBtn = page.getByRole('button', { name: '关闭' })
  if ((await closeBtn.count()) > 0) await closeBtn.click()

  // ---------- 4) 对话页：表单弹窗收集 ----------
  await page.goto(BASE + '/chat')
  await page.waitForTimeout(1200)
  const agentSelect = page.locator('select').first()
  const options = await agentSelect.locator('option').allTextContents()
  const targetIdx = options.findIndex((t) => t.includes('SRE') && t.includes('流水线'))
  if (targetIdx >= 0) {
    await agentSelect.selectOption({ index: targetIdx })
    await page.waitForTimeout(1200) // 等 fetchGraph 拿 startForm
    await page.fill('textarea', '帮我看下线上告警')
    await page.locator('textarea').press('Enter')
    await page.waitForSelector('text=输入表单', { timeout: 8000 })
    check('对话发送弹出输入表单', true)
    const days2 = await page.locator('.modal input[type="number"]').inputValue()
    check('对话弹窗 days 预填 3', days2 === '3', days2)
    // 必填拦截：city 留空 → toast（Toast 组件是 #toast.show）
    const askForm = page.locator('.modal form')
    await askForm.getByRole('button', { name: '发送' }).click()
    await page.waitForSelector('#toast.show', { timeout: 5000 })
    const toastText = (await page.locator('#toast.show').last().textContent()) || ''
    check('对话弹窗必填拦截 toast', toastText.includes('必填'), toastText.slice(0, 50))
    // 填好发送
    await page.locator('.modal input').first().fill('深圳')
    await page.locator('.modal input[type="number"]').fill('4')
    await askForm.getByRole('button', { name: '发送' }).click()
    await page.waitForTimeout(1500)
    check('对话 custom 运行已发起', (await page.locator('text=帮我看下线上告警').count()) > 0)
  } else {
    check('找到 Agent 34 选项', false, options.join(' / '))
  }

  check('无 JS 报错', jsErrors.length === 0, jsErrors.slice(0, 3).join('; '))
  await browser.close()
})().catch((e) => {
  results.push({ name: '脚本异常', ok: false, detail: String(e) })
  console.error('SCRIPT ERROR:', e)
  fs.writeFileSync(OUT, JSON.stringify(results, null, 2))
  process.exit(1)
})

fs.writeFileSync(OUT, JSON.stringify(results, null, 2))
const fails = results.filter((r) => !r.ok)
console.log(`==== ${results.length - fails.length}/${results.length} passed ====`)
if (fails.length > 0) process.exit(1)
