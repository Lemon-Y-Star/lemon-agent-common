# -*- coding: utf-8 -*-
"""知识库种子导入脚本（BUILD-SPEC §3.3 一次性导入）：
读 backend/knowledge-seed/*.md → 固定窗口切分（500 字 / 50 重叠）→
调用 OpenAI 兼容 /embeddings → 生成 SQL → 上传 NAS → psql 写入 doc_chunk。

用法（环境变量）：
  LEMON_EMBEDDING_BASE_URL  如 https://api.siliconflow.cn/v1
  LEMON_EMBEDDING_API_KEY   凭据
  LEMON_EMBEDDING_MODEL     1024 维模型（与 doc_chunk vector(1024) 锁定）
  NAS_HOST / NAS_USER / NAS_PASSWORD  默认 admin / 见配置

幂等：按 doc_id 先删后插，可重复执行。
"""
import glob
import json
import os
import sys
import urllib.request

import paramiko

SEED_DIR = os.path.join(os.path.dirname(__file__), "..", "knowledge-seed")
CHUNK_SIZE = 500
OVERLAP = 50

BASE_URL = os.environ.get("LEMON_EMBEDDING_BASE_URL", "")
API_KEY = os.environ.get("LEMON_EMBEDDING_API_KEY", "")
MODEL = os.environ.get("LEMON_EMBEDDING_MODEL", "")
NAS_HOST = os.environ.get("NAS_HOST", "192.168.5.250")
NAS_USER = os.environ.get("NAS_USER", "admin")
NAS_PASSWORD = os.environ.get("NAS_PASSWORD", "jq<41GMMg@")

if not (BASE_URL and API_KEY and MODEL):
    print("缺少 embedding 配置（LEMON_EMBEDDING_BASE_URL / API_KEY / MODEL）")
    sys.exit(1)


def embed(text: str) -> list[float]:
    req = urllib.request.Request(
        BASE_URL.rstrip("/") + "/embeddings",
        method="POST",
        data=json.dumps({"model": MODEL, "input": text}).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + API_KEY,
        },
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    vec = data["data"][0]["embedding"]
    if len(vec) != 1024:
        raise SystemExit(f"embedding 维度 {len(vec)} != 1024，与 doc_chunk 表锁定维度不符")
    return vec


def chunk_text(text: str) -> list[str]:
    text = text.strip()
    chunks = []
    step = CHUNK_SIZE - OVERLAP
    i = 0
    while i < len(text):
        chunks.append(text[i:i + CHUNK_SIZE])
        i += step
    return chunks if chunks else [text]


def esc(s: str) -> str:
    return s.replace("'", "''").replace("\\", "\\\\")


statements = ["BEGIN;"]
total_chunks = 0
for path in sorted(glob.glob(os.path.join(SEED_DIR, "*.md"))):
    doc_id = os.path.splitext(os.path.basename(path))[0]
    title = open(path, encoding="utf-8").readline().lstrip("# ").strip()
    content = open(path, encoding="utf-8").read()
    statements.append(f"DELETE FROM doc_chunk WHERE doc_id = '{esc(doc_id)}';")
    for no, chunk in enumerate(chunk_text(content)):
        vec = embed(chunk)
        vec_literal = "[" + ",".join(f"{v:.6f}" for v in vec) + "]"
        statements.append(
            "INSERT INTO doc_chunk (doc_id, title, content, chunk_no, embedding) VALUES ("
            f"'{esc(doc_id)}', '{esc(title)}', '{esc(chunk)}', {no}, '{vec_literal}');"
        )
        total_chunks += 1
        print(f"  embedded {doc_id}#{no} ({len(chunk)} 字)")
statements.append("COMMIT;")
print(f"共 {total_chunks} 个片段，上传 NAS 执行…")

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(NAS_HOST, username=NAS_USER, password=NAS_PASSWORD,
               look_for_keys=False, allow_agent=False, timeout=15)
sftp = client.open_sftp()
with sftp.file("/tmp/kb_seed.sql", "w") as f:
    f.write("\n".join(statements))
sftp.close()

cmd = "sudo -S -p '' sh -c 'docker exec -i middleware-pgvector psql -U postgres -d lemon_agent -f - < /tmp/kb_seed.sql'"
stdin, stdout, stderr = client.exec_command(cmd, timeout=120)
stdin.write(NAS_PASSWORD + "\n")
stdin.flush()
print(stdout.read().decode("utf-8", "replace"))
client.close()
print("IMPORT_DONE")
