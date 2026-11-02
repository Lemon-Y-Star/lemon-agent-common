"""LemonAgent Python 沙箱执行服务（对标 Dify dify-sandbox / FastGPT sandbox）。

只暴露两个端点：
- GET  /health   健康探测
- POST /execute  执行一段 Python 代码，返回 stdout/stderr/退出码/耗时

安全模型（纵深防御）：
- 网络层：容器只挂 internal 网络（compose），出外网不可达；
- 进程层：用户代码跑在独立子进程，runner.py 用 setrlimit 限内存/CPU/文件大小；
- 时间层：服务端 wait(timeout) 超时击杀，请求级再兜底一层；
- 体积层：stdout/stderr 各截断到 8KB，防止刷爆响应。
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import time
import uuid

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="LemonAgent Sandbox", version="1.0.0")

MAX_TIMEOUT = 15          # 单次执行上限（秒）
MAX_OUTPUT = 8 * 1024     # stdout/stderr 各自截断阈值（字节）
RUNNER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "runner.py")


class ExecReq(BaseModel):
    code: str = Field(min_length=1, max_length=32 * 1024)
    timeoutSeconds: int = Field(default=10, ge=1, le=MAX_TIMEOUT)
    # 上游节点产出（执行图 CODE 节点用）：以 JSON 数组注入代码内的 upstream 变量
    upstream: list[str] = Field(default_factory=list, max_length=20)


class ExecResp(BaseModel):
    stdout: str
    stderr: str
    exitCode: int
    durationMs: int
    timedOut: bool
    truncated: bool


@app.get("/health")
def health() -> dict:
    return {"status": "UP"}


@app.post("/execute", response_model=ExecResp)
def execute(req: ExecReq) -> ExecResp:
    timeout = min(req.timeoutSeconds, MAX_TIMEOUT)
    ws = tempfile.mkdtemp(prefix="sbx_")
    code_file = os.path.join(ws, "main.py")
    try:
        with open(code_file, "w", encoding="utf-8") as f:
            # upstream 变量恒注入（含空列表）：只在非空时注入会让代码引用到未定义变量（体检 C5/C6 实测）
            f.write("import json\nupstream = json.loads(%r)\n\n"
                    % json.dumps(req.upstream, ensure_ascii=False))
            f.write(req.code)
        start = time.monotonic()
        proc = subprocess.Popen(
            ["python", "-B", RUNNER, code_file],
            cwd=ws,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            # 清空环境变量：不带任何密钥/连接串进沙箱进程
            env={"PATH": os.environ.get("PATH", "/usr/bin:/bin"),
                 "PYTHONUNBUFFERED": "1", "PYTHONIOENCODING": "utf-8",
                 "HOME": ws, "TMPDIR": ws},
        )
        timed_out = False
        try:
            out, err = proc.communicate(timeout=timeout)
        except subprocess.TimeoutExpired:
            timed_out = True
            proc.kill()
            out, err = proc.communicate()
        duration = int((time.monotonic() - start) * 1000)

        stdout = out.decode("utf-8", errors="replace")
        stderr = err.decode("utf-8", errors="replace")
        truncated = len(stdout) > MAX_OUTPUT or len(stderr) > MAX_OUTPUT
        return ExecResp(
            stdout=stdout[:MAX_OUTPUT],
            stderr=stderr[:MAX_OUTPUT],
            exitCode=proc.returncode if proc.returncode is not None else -9,
            durationMs=duration,
            timedOut=timed_out,
            truncated=truncated,
        )
    finally:
        shutil.rmtree(ws, ignore_errors=True)


@app.exception_handler(Exception)
def on_error(request, exc):  # noqa: ANN001, ANN201
    return HTTPException(status_code=500, detail=f"沙箱内部错误：{exc}")
