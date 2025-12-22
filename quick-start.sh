#!/bin/bash

# 快速启动脚本 - 适用于Linux服务器
# 使用方法: ./quick-start.sh

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="${APP_DIR}/target/quota-matching-tool-1.0.0.jar"

echo "========================================"
echo "企业定额自动套用工具 - 快速启动"
echo "========================================"
echo ""

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 未找到Java环境"
    echo ""
    echo "请先安装Java:"
    echo "  CentOS/RHEL: yum install java-1.8.0-openjdk"
    echo "  Ubuntu/Debian: apt-get install openjdk-8-jdk"
    exit 1
fi

echo "✅ Java环境检查通过"
java -version 2>&1 | head -n 1
echo ""

# 检查JAR文件
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ 错误: JAR文件不存在: $JAR_FILE"
    echo ""
    echo "请先上传JAR文件到服务器"
    exit 1
fi

echo "✅ JAR文件检查通过: $JAR_FILE"
echo ""

# 检查数据库配置
if [ -z "$DB_PASSWORD" ]; then
    echo "⚠️  警告: 未设置数据库密码环境变量"
    echo ""
    echo "请设置数据库密码（如果使用生产环境）:"
    echo "  export DB_PASSWORD=your_password"
    echo ""
    echo "或使用开发环境（H2内存数据库）:"
    echo "  ./run.sh dev"
    echo ""
    read -p "是否继续使用生产环境配置？(y/n): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "已取消"
        exit 0
    fi
fi

# 创建日志目录
mkdir -p "${APP_DIR}/logs"
mkdir -p /var/log/quota-matching-tool 2>/dev/null || true

# JVM参数
JVM_OPTS="-Xms512m -Xmx1024m"
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"
JVM_OPTS="$JVM_OPTS -Djava.awt.headless=true"
JVM_OPTS="$JVM_OPTS -Dfile.encoding=UTF-8"
JVM_OPTS="$JVM_OPTS -Duser.timezone=Asia/Shanghai"

# Spring Boot参数
SPRING_OPTS="--spring.profiles.active=prod"
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
SPRING_OPTS="$SPRING_OPTS --logging.file.name=${APP_DIR}/logs/application.log"

echo "========================================"
echo "启动配置"
echo "========================================"
echo "运行模式: 生产环境"
echo "JAR文件: $JAR_FILE"
echo "日志文件: ${APP_DIR}/logs/application.log"
echo "========================================"
echo ""
echo "提示: 按 Ctrl+C 可以停止应用"
echo ""

# 启动应用
java $JVM_OPTS -jar "$JAR_FILE" $SPRING_OPTS

