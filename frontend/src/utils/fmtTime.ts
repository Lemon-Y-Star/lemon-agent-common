/**
 * 时间显示统一工具（v4.5.5 时区修复）。
 *
 * 背景：API 返回的时间是 ISO-8601 UTC 字符串（带 Z，如 2026-11-03T14:02:00Z），
 * 此前各页面用 `slice()` 纯字符串截取，把 UTC 字面当本地时间显示，东八区差 8 小时。
 * 这里统一 `new Date()` 解析（带 Z 能正确得绝对时间）后按浏览器本地时区格式化；
 * 解析失败（旧数据/异常格式）时退回原来的字符串截取，保证不显示空。
 */

/** 本地时间格式化：默认 `yyyy-MM-dd HH:mm`，withSeconds 时带秒。 */
export function fmtLocal(iso: string | null | undefined, withSeconds = false): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (isNaN(d.getTime())) {
    // 兜底：不是合法 ISO 串就退回旧的截取展示（不吞数据）
    return withSeconds ? iso.slice(0, 19).replace('T', ' ') : iso.slice(0, 16).replace('T', ' ')
  }
  const p = (n: number) => String(n).padStart(2, '0')
  const base = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  return withSeconds ? `${base}:${p(d.getSeconds())}` : base
}
