import type { ReactNode } from 'react'

/**
 * 原型同款弹层：.mask 遮罩 + .modal 卡片（640px / 圆角 14 / h3 带右上角 ×）。
 * wide=true 时用 920px（多模型对比这类多栏内容）。
 */
export default function Modal(props: {
  title: string
  onClose: () => void
  wide?: boolean
  children: ReactNode
}) {
  return (
    <div className="mask" onClick={props.onClose}>
      <div
        className="modal"
        style={props.wide ? { width: 920, maxWidth: '94vw' } : undefined}
        onClick={(e) => e.stopPropagation()}
      >
        <h3>
          {props.title}
          <span className="x" onClick={props.onClose}>
            ✕
          </span>
        </h3>
        {props.children}
      </div>
    </div>
  )
}
