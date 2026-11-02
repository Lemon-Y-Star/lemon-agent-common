"""受限运行器：用户代码的壳。

先收紧资源上限，再以 __main__ 执行目标文件。任何资源超限都会让
进程收到 SIGXCPU / MemoryError，从而被优雅终止而不是拖垮宿主。
"""

import sys

try:
    import resource
except ImportError:
    resource = None  # Windows 本地开发无 rlimit；生产为 Linux 容器，硬限生效

MEM_BYTES = 256 * 1024 * 1024      # 虚拟内存上限 256MB（numpy/pandas 足够）
CPU_SECONDS = 15                   # CPU 时间上限（秒）
FILE_BYTES = 8 * 1024 * 1024       # 单文件写上限 8MB


def _limit(res, soft, hard=None):
    try:
        resource.setrlimit(res, (soft, hard if hard is not None else soft))
    except (ValueError, OSError):
        pass  # 个别内核/容器环境不允许收紧某项，跳过不阻断


if resource is not None:
    _limit(resource.RLIMIT_AS, MEM_BYTES)
    _limit(resource.RLIMIT_CPU, CPU_SECONDS)
    _limit(resource.RLIMIT_FSIZE, FILE_BYTES)
    _limit(resource.RLIMIT_CORE, 0)

if len(sys.argv) < 2:
    print("runner: missing code file", file=sys.stderr)
    sys.exit(2)

import runpy

runpy.run_path(sys.argv[1], run_name="__main__")
