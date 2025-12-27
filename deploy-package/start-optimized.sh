#!/bin/bash

# 企业定额匹配系统 - 优化启动脚本（4核4G服务器）
# 使用方法: ./start-optimized.sh

JAR_FILE="target/quota-matching-tool-1.0.0.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "错误: JAR文件不存在: $JAR_FILE"
    echo "请先运行打包命令: mvn clean package -DskipTests"
    exit 1
fi

echo "=========================================="
echo "启动企业定额匹配系统（性能优化版）"
echo "=========================================="
echo "服务器配置: 4核CPU, 4G内存"
echo "JAR文件: $JAR_FILE"
echo ""

# JVM优化参数（针对4核4G服务器）
# -Xms2g: 初始堆内存2G
# -Xmx3g: 最大堆内存3G（为系统保留1G）
# -XX:+UseG1GC: 使用G1垃圾回收器（适合多核服务器）
# -XX:MaxGCPauseMillis=200: 最大GC暂停时间200ms
# -XX:ParallelGCThreads=4: GC并行线程数（等于CPU核心数）
# -XX:ConcGCThreads=2: 并发GC线程数
# -XX:+UseStringDeduplication: 字符串去重（节省内存）
# -XX:+OptimizeStringConcat: 优化字符串连接
# -XX:+UseCompressedOops: 使用压缩指针（节省内存）
# -XX:+UseCompressedClassPointers: 使用压缩类指针
# -Djava.awt.headless=true: 无头模式（服务器环境）
# -Dfile.encoding=UTF-8: 文件编码
# -Duser.timezone=Asia/Shanghai: 时区设置

java -Xms2g \
     -Xmx3g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:ParallelGCThreads=4 \
     -XX:ConcGCThreads=2 \
     -XX:+UseStringDeduplication \
     -XX:+OptimizeStringConcat \
     -XX:+UseCompressedOops \
     -XX:+UseCompressedClassPointers \
     -Djava.awt.headless=true \
     -Dfile.encoding=UTF-8 \
     -Duser.timezone=Asia/Shanghai \
     -jar "$JAR_FILE" \
     --spring.profiles.active=prod

echo ""
echo "应用已启动"
echo "访问地址: http://localhost:8080"

