import { post } from './client'

/** 代码沙箱 API（v4.4）：编辑器 CODE 节点「试运行」直通。 */

export interface SandboxResult {
  stdout: string
  stderr: string
  exitCode: number
  durationMs: number
  timedOut: boolean
  truncated: boolean
}

export function runSandbox(code: string): Promise<SandboxResult> {
  return post<SandboxResult>('/workspace/sandbox/execute', { code })
}
