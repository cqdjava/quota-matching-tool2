#!/bin/bash

# 构建脚本
# 使用方法: ./build.sh

echo "开始构建应用..."

# 清理并打包
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "构建成功!"
    echo "JAR文件位置: target/quota-matching-tool-1.0.0.jar"
    ls -lh target/quota-matching-tool-1.0.0.jar
else
    echo "构建失败!"
    exit 1
fi

