#!/bin/bash

# 企业定额自动套用工具停止脚本
# 使用方法: ./stop.sh

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="quota-matching-tool"
PID_FILE="${APP_DIR}/${APP_NAME}.pid"

# 检查PID文件是否存在
if [ ! -f "$PID_FILE" ]; then
    echo "应用未运行或PID文件不存在"
    exit 1
fi

PID=$(cat "$PID_FILE")

# 检查进程是否存在
if ! ps -p "$PID" > /dev/null 2>&1; then
    echo "应用未运行 (PID: $PID)"
    rm -f "$PID_FILE"
    exit 1
fi

# 停止应用
echo "正在停止应用 (PID: $PID)..."
kill "$PID"

# 等待进程结束
WAIT_TIME=0
MAX_WAIT=30
while ps -p "$PID" > /dev/null 2>&1 && [ $WAIT_TIME -lt $MAX_WAIT ]; do
    sleep 1
    WAIT_TIME=$((WAIT_TIME + 1))
done

# 如果进程仍在运行，强制杀死
if ps -p "$PID" > /dev/null 2>&1; then
    echo "应用未正常停止，强制终止..."
    kill -9 "$PID"
    sleep 1
fi

# 删除PID文件
rm -f "$PID_FILE"

if ps -p "$PID" > /dev/null 2>&1; then
    echo "停止失败"
    exit 1
else
    echo "应用已停止"
fi

