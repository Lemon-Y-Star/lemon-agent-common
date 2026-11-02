# LemonAgent Python 沙箱镜像：预装 numpy/pandas（清华源），运行期零外网依赖。
FROM docker.1ms.run/library/python:3.11-slim

ENV TZ=Asia/Shanghai \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

COPY app.py runner.py ./

# 非 root 运行
RUN useradd -m -u 10001 sandbox && chown -R sandbox:sandbox /app
USER sandbox

EXPOSE 8900
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8900"]
