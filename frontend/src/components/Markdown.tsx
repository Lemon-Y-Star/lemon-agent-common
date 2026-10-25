import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'

/**
 * 助手回复的 Markdown 渲染（GFM：表格 / 任务列表 / 删除线；KaTeX：数学公式）。
 *
 * 流式过程中可能收到"半截 Markdown"（表格没闭合、代码块没写完），react-markdown
 * 会按已有内容尽力渲染，不会报错；最终 run_completed 的权威正文会再覆盖一次。
 */

/**
 * 把 LaTeX 原生定界符归一为 remark-math 认识的 $ 定界符：
 * \[...\] → $$...$$（块级）、\(...\) → $...$（行内）。
 * 模型两种写法都会用；代码块 / 行内代码里的 \[ 不处理（避免误伤数组字面量）。
 */
function normalizeMathDelims(text: string): string {
  const segs = text.split(/(```[\s\S]*?```|`[^`\n]*`)/g)
  return segs
    .map((seg) => {
      if (seg.startsWith('```') || seg.startsWith('`')) return seg
      return seg
        .replace(/\\\[([\s\S]*?)\\\]/g, (_m, body) => `\n$$${body}$$\n`)
        .replace(/\\\(([\s\S]*?)\\\)/g, (_m, body) => `$${body}$`)
    })
    .join('')
}

export default function Markdown({ text }: { text: string }) {
  return (
    <div className="md">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex]}
      >
        {normalizeMathDelims(text)}
      </ReactMarkdown>
    </div>
  )
}
