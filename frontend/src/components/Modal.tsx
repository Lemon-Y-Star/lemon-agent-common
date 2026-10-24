import type { ReactNode } from 'react'

/**
 * 轻量弹层：遮罩 + 居中卡片 + 右上角关闭。
 */
export default function Modal(props: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="modal-mask" onClick={props.onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>{props.title}</h3>
        {props.children}
      </div>
    </div>
  )
}
