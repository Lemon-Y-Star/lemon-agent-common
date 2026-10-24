import Modal from './Modal'

/**
 * 原型风格的二次确认弹层（对应 PRD §9.8「关键操作与二次确认」）。
 */
export default function ConfirmDialog(props: {
  title: string
  message: string
  confirmText?: string
  onConfirm: () => void
  onCancel: () => void
}) {
  return (
    <Modal title={props.title} onClose={props.onCancel}>
      <p style={{ fontSize: 13, color: '#4b5563', lineHeight: 1.8 }}>{props.message}</p>
      <div className="modal-btns">
        <button className="btn" onClick={props.onCancel}>
          取消
        </button>
        <button className="btn primary" onClick={props.onConfirm}>
          {props.confirmText ?? '确认'}
        </button>
      </div>
    </Modal>
  )
}
