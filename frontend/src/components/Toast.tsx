import { useEffect, useState } from 'react'

/**
 * 原型同款底部居中黑色 toast：任意模块 import { toast } 后一行触发。
 */
let notify: ((msg: string) => void) | null = null

export function toast(msg: string) {
  notify?.(msg)
}

export function ToastHost() {
  const [msg, setMsg] = useState<string | null>(null)
  const [show, setShow] = useState(false)

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>
    notify = (text) => {
      setMsg(text)
      setShow(true)
      timer = setTimeout(() => setShow(false), 2200)
    }
    return () => {
      notify = null
      clearTimeout(timer)
    }
  }, [])

  return <div id="toast" className={show ? 'show' : ''}>{msg}</div>
}
