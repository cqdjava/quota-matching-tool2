#!/bin/bash

# 企业定额自动套用工具 - Linux运行脚本
# 使用方法: ./run.sh [dev|prod]

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="${APP_DIR}/target/quota-matching-tool-1.0.0.jar"
PROFILE="${1:-dev}"

# 检查JAR文件是否存在
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: JAR文件不存在: $JAR_FILE"
    echo "请先执行 mvn clean package 打包应用"
    exit 1
fi

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境，请先安装JDK 1.8或更高版本"
    exit 1
fi

echo "========================================"
echo "企业定额自动套用工具"
echo "========================================"
echo "运行模式: $PROFILE"
echo "JAR文件: $JAR_FILE"
echo "========================================"
echo ""

# JVM参数
JVM_OPTS="-Xms512m -Xmx1024m"
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"
JVM_OPTS="$JVM_OPTS -Djava.awt.headless=true"
JVM_OPTS="$JVM_OPTS -Dfile.encoding=UTF-8"
JVM_OPTS="$JVM_OPTS -Duser.timezone=Asia/Shanghai"

# Spring Boot参数
SPRING_OPTS="--spring.profiles.active=$PROFILE"

# 启动应用
echo "正在启动应用..."
echo ""
java $JVM_OPTS -jar "$JAR_FILE" $SPRING_OPTS

if [ $? -ne 0 ]; then
    echo ""
    echo "应用启动失败！"
    exit 1
fi

