import type { ReactNode } from 'react'

/**
 * 原型同款弹层：.mask 遮罩 + .modal 卡片（640px / 圆角 14 / h3 带右上角 ×）。
 */
export default function Modal(props: {
  title: string
  onClose: () => void
  children: ReactNode
}) {
  return (
    <div className="mask" onClick={props.onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
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
