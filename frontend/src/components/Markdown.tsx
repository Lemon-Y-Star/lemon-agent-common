import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

/**
 * 助手回复的 Markdown 渲染（GFM：表格 / 任务列表 / 删除线）。
 *
 * 流式过程中可能收到"半截 Markdown"（表格没闭合、代码块没写完），react-markdown
 * 会按已有内容尽力渲染，不会报错；最终 run_completed 的权威正文会再覆盖一次。
 */
export default function Markdown({ text }: { text: string }) {
  return (
    <div className="md">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{text}</ReactMarkdown>
    </div>
  )
}
