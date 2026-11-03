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

import base64
import json
import os
import shutil
import subprocess
import tempfile
import time
import uuid
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="LemonAgent Sandbox", version="1.0.0")

MAX_TIMEOUT = 15          # 单次执行上限（秒）
MAX_OUTPUT = 8 * 1024     # stdout/stderr 各自截断阈值（字节）
RUNNER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "runner.py")

# 文件回收（v4.4.4）：执行后扫描工作区的 png/csv/html，base64 回传后端落库为运行产物
MAX_FILES = 8                     # 单次最多回收文件数
MAX_FILE_BYTES = 512 * 1024       # 单文件上限 512KB
MAX_FILES_TOTAL = 2 * 1024 * 1024 # 单次总量上限 2MB
FILE_TYPES = {".png": "image/png", ".csv": "text/csv", ".html": "text/html"}

# 访问令牌（竞品对比加固）：FastGPT v4.14.8 被审计点名"沙箱 HTTP 接口默认无认证"，
# 同内网任何容器都能提交代码。设了 LEMON_SANDBOX_TOKEN 就强制校验，没设则只靠网络隔离。
SANDBOX_TOKEN = os.environ.get("LEMON_SANDBOX_TOKEN", "").strip()


class ExecReq(BaseModel):
    code: str = Field(min_length=1, max_length=32 * 1024)
    timeoutSeconds: int = Field(default=10, ge=1, le=MAX_TIMEOUT)
    # 上游节点产出（执行图 CODE 节点用）：以 JSON 注入代码内的 upstream 变量（v4.5.3 起兼容字典形态）
    upstream: list[str] = Field(default_factory=list, max_length=20)
    # 输入变量绑定（v4.5.3）：{别名: 产出}，注入代码内的 vars 字典变量；空 = 不注入
    vars: dict[str, str] = Field(default_factory=dict)


class ExecResp(BaseModel):
    stdout: str
    stderr: str
    exitCode: int
    durationMs: int
    timedOut: bool
    truncated: bool
    files: list[dict] = Field(default_factory=list)


@app.get("/health")
def health() -> dict:
    return {"status": "UP"}


def require_token(x_sandbox_token: str) -> None:
    if SANDBOX_TOKEN and x_sandbox_token != SANDBOX_TOKEN:
        raise HTTPException(status_code=401, detail="沙箱令牌无效")


@app.post("/execute", response_model=ExecResp)
def execute(req: ExecReq, x_sandbox_token: str = Header(default="")) -> ExecResp:
    require_token(x_sandbox_token)
    timeout = min(req.timeoutSeconds, MAX_TIMEOUT)
    ws = tempfile.mkdtemp(prefix="sbx_")
    code_file = os.path.join(ws, "main.py")
    try:
        with open(code_file, "w", encoding="utf-8") as f:
            # upstream 变量恒注入（含空列表）：只在非空时注入会让代码引用到未定义变量（体检 C5/C6 实测）
            f.write("import json\nupstream = json.loads(%r)\n\n"
                    % json.dumps(req.upstream, ensure_ascii=False))
            # vars 字典恒注入（v4.5.3 输入变量绑定）：绑定模式下代码读 vars["别名"]
            f.write("vars = json.loads(%r)\n\n"
                    % json.dumps(req.vars, ensure_ascii=False))
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
                 # matplotlib 无头渲染必须 Agg，否则 import pyplot 直接崩
                 "MPLBACKEND": "Agg",
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
            files=collect_files(ws),
        )
    finally:
        shutil.rmtree(ws, ignore_errors=True)


def collect_files(ws: str) -> list[dict]:
    """回收工作区产物文件（png/csv/html）：按修改时间倒序，限数量/单文件/总量，base64 回传。"""
    files: list[dict] = []
    total = 0
    try:
        candidates = sorted(
            (p for p in Path(ws).rglob("*") if p.suffix.lower() in FILE_TYPES and p.is_file()),
            key=lambda p: p.stat().st_mtime, reverse=True)
    except OSError:
        return files
    for p in candidates:
        if len(files) >= MAX_FILES:
            break
        try:
            data = p.read_bytes()
        except OSError:
            continue
        if not data or len(data) > MAX_FILE_BYTES or total + len(data) > MAX_FILES_TOTAL:
            continue
        files.append({
            "name": p.relative_to(ws).as_posix(),
            "contentType": FILE_TYPES[p.suffix.lower()],
            "contentB64": base64.b64encode(data).decode(),
        })
        total += len(data)
    return files


@app.exception_handler(Exception)
def on_error(request, exc):  # noqa: ANN001, ANN201
    from fastapi.responses import JSONResponse
    return JSONResponse(status_code=500, content={"detail": f"沙箱内部错误：{exc}"})
