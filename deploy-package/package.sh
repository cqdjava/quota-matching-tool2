#!/bin/bash

# 打包脚本 - 用于云服务器部署
# 使用方法: ./package.sh

echo "=========================================="
echo "开始打包应用..."
echo "=========================================="

# 清理旧的构建
echo "清理旧的构建文件..."
mvn clean

# 打包（跳过测试，加快打包速度）
echo "开始Maven打包..."
mvn package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "=========================================="
    echo "打包成功!"
    echo "=========================================="
    echo "JAR文件位置: target/quota-matching-tool-1.0.0.jar"
    echo ""
    ls -lh target/quota-matching-tool-1.0.0.jar
    echo ""
    echo "文件大小:"
    du -h target/quota-matching-tool-1.0.0.jar
    echo ""
    echo "=========================================="
    echo "打包完成，可以部署到云服务器了！"
    echo "=========================================="
else
    echo ""
    echo "=========================================="
    echo "打包失败，请检查错误信息！"
    echo "=========================================="
    exit 1
fi

