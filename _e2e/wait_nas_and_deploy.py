# -*- coding: utf-8 -*-
"""等 NAS 恢复后自动补推 Gitea 并部署 v4.5.7（启动回放修复）。"""
import socket
import subprocess
import time

HOST = '192.168.5.250'
DEADLINE = time.time() + 3600  # 最长等 60 分钟

def probe(port):
    s = socket.socket()
    s.settimeout(4)
    try:
        s.connect((HOST, port))
        return True
    except Exception:
        return False
    finally:
        s.close()

while time.time() < DEADLINE:
    if probe(18787) and probe(13306):
        print(time.strftime('%H:%M:%S'), 'NAS 恢复，开始补推 Gitea + 部署')
        time.sleep(10)
        r1 = subprocess.run(['git', 'push', 'gitea', 'main'], cwd='Y:/面试/lemon-agent-common',
                            capture_output=True, text=True)
        print('gitea push:', r1.stdout.strip()[-80:] or r1.stderr.strip()[-80:])
        r2 = subprocess.run(['python', 'deploy_nas.py'], cwd='Y:/面试/lemon-agent-common/_e2e',
                            capture_output=True, text=True)
        print(r2.stdout[-400:])
        print('DEPLOY DONE' if 'FINAL: done' in r2.stdout else 'DEPLOY MAYBE FAILED')
        break
    time.sleep(60)
else:
    print('等待 60 分钟仍未恢复，退出')
