#!/bin/bash

# 企业定额自动套用工具启动脚本
# 使用方法: ./start.sh

# 获取脚本所在目录
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="quota-matching-tool"
JAR_FILE="${APP_DIR}/target/${APP_NAME}-1.0.0.jar"
PID_FILE="${APP_DIR}/${APP_NAME}.pid"
LOG_DIR="${APP_DIR}/logs"

# 检查Java环境
if [ -z "$JAVA_HOME" ]; then
    JAVA_CMD="java"
else
    JAVA_CMD="${JAVA_HOME}/bin/java"
fi

# 检查JAR文件是否存在
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: JAR文件不存在: $JAR_FILE"
    echo "请先执行 mvn clean package 打包应用"
    exit 1
fi

# 检查应用是否已经运行
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "应用已经在运行中 (PID: $PID)"
        exit 1
    else
        rm -f "$PID_FILE"
    fi
fi

# 创建日志目录
mkdir -p "$LOG_DIR"

# JVM参数配置
JVM_OPTS="-Xms512m -Xmx1024m"
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"
JVM_OPTS="$JVM_OPTS -XX:MaxGCPauseMillis=200"
JVM_OPTS="$JVM_OPTS -XX:+HeapDumpOnOutOfMemoryError"
JVM_OPTS="$JVM_OPTS -XX:HeapDumpPath=${LOG_DIR}/heap_dump.hprof"
JVM_OPTS="$JVM_OPTS -Djava.awt.headless=true"
JVM_OPTS="$JVM_OPTS -Dfile.encoding=UTF-8"
JVM_OPTS="$JVM_OPTS -Duser.timezone=Asia/Shanghai"

# Spring Boot参数
SPRING_OPTS="--spring.profiles.active=prod"
SPRING_OPTS="$SPRING_OPTS --server.port=8080"

# 数据库配置（可通过环境变量覆盖）
if [ -n "$DB_HOST" ]; then
    SPRING_OPTS="$SPRING_OPTS --spring.datasource.url=jdbc:mysql://${DB_HOST}:${DB_PORT:-3306}/${DB_NAME:-quota_db}?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=Asia/Shanghai"
fi
if [ -n "$DB_USERNAME" ]; then
    SPRING_OPTS="$SPRING_OPTS --spring.datasource.username=${DB_USERNAME}"
fi
if [ -n "$DB_PASSWORD" ]; then
    SPRING_OPTS="$SPRING_OPTS --spring.datasource.password=${DB_PASSWORD}"
fi

# 日志配置
SPRING_OPTS="$SPRING_OPTS --logging.file.name=${LOG_DIR}/application.log"

# 启动应用
echo "正在启动应用..."
nohup $JAVA_CMD $JVM_OPTS -jar "$JAR_FILE" $SPRING_OPTS > "${LOG_DIR}/startup.log" 2>&1 &

# 保存PID
echo $! > "$PID_FILE"

# 等待启动
sleep 3

# 检查是否启动成功
if ps -p $(cat "$PID_FILE") > /dev/null 2>&1; then
    echo "应用启动成功!"
    echo "PID: $(cat $PID_FILE)"
    echo "日志文件: ${LOG_DIR}/application.log"
    echo "访问地址: http://localhost:8080"
else
    echo "应用启动失败，请查看日志: ${LOG_DIR}/startup.log"
    rm -f "$PID_FILE"
    exit 1
fi

