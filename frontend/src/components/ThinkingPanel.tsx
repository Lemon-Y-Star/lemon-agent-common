import { useEffect, useRef } from 'react'

/**
 * 深度思考（CoT，v3.9）：推理内容以 <think>…</think> 标签随正文流式输出。
 * - 流式过程中面板自动展开（「思考中…」+ 推理流式滚动）；
 * - 结论输出完（run_completed）自动收起；
 * - 用户随时点击标题手动开合（手动操作后自动行为让位）。
 */

/** 拆分 <think> 包裹的推理与最终回答；done=true 表示闭合标签已到。 */
export function splitThink(raw: string): { think: string; answer: string; done: boolean } {
  const start = raw.indexOf('<think>')
  if (start === -1) return { think: '', answer: raw, done: false }
  const body = raw.slice(start + '<think>'.length)
  const end = body.indexOf('</think>')
  if (end === -1) {
    // 流式边缘可能收到半截闭合标签（</t / </thi…），剪掉再展示
    const think = body.replace(/<\/(t|h|i|n|k)?$/, '')
    return { think: think.trim(), answer: '', done: false }
  }
  return { think: body.slice(0, end).trim(), answer: body.slice(end + '</think>'.length).trim(), done: true }
}

/** 去掉 <think> 块（产物 / 评测等只展示结论的场景）。 */
export function stripThink(raw: string): string {
  return splitThink(raw).answer
}

export default function ThinkingPanel({ think, done, open, onToggle }: {
  think: string
  done: boolean
  open: boolean
  onToggle: () => void
}) {
  const bodyRef = useRef<HTMLDivElement | null>(null)

  // 流式且展开时贴底滚动；done 后停手，让用户回看
  useEffect(() => {
    if (open && !done && bodyRef.current) {
      bodyRef.current.scrollTop = bodyRef.current.scrollHeight
    }
  }, [think, open, done])

  return (
    <div className="think-panel">
      <div className={`think-head ${open ? 'open' : ''}`} onClick={onToggle}>
        <span>🧠 {done ? '已完成思考' : '思考中…'}</span>
        <span className="chev">▶</span>
      </div>
      {open && <div className={`think-body ${done ? '' : 'streaming'}`} ref={bodyRef}>{think || '…'}</div>}
    </div>
  )
}
