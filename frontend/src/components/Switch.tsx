/**
 * 原型同款开关（.switch / .switch.on）：用于连接的启用/停用。
 */
export default function Switch(props: { on: boolean; onChange: (on: boolean) => void }) {
  return (
    <span
      className={`switch ${props.on ? 'on' : ''}`}
      onClick={() => props.onChange(!props.on)}
      role="switch"
      aria-checked={props.on}
    />
  )
}
