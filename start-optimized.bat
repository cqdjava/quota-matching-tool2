@echo off
REM 企业定额匹配系统 - 优化启动脚本（Windows，4核4G服务器）
REM 使用方法: start-optimized.bat

set JAR_FILE=target\quota-matching-tool-1.0.0.jar

if not exist "%JAR_FILE%" (
    echo 错误: JAR文件不存在: %JAR_FILE%
    echo 请先运行打包命令: mvn clean package -DskipTests
    pause
    exit /b 1
)

echo ==========================================
echo 启动企业定额匹配系统（性能优化版）
echo ==========================================
echo 服务器配置: 4核CPU, 4G内存
echo JAR文件: %JAR_FILE%
echo.

REM JVM优化参数（针对4核4G服务器）
REM -Xms2g: 初始堆内存2G
REM -Xmx3g: 最大堆内存3G（为系统保留1G）
REM -XX:+UseG1GC: 使用G1垃圾回收器（适合多核服务器）
REM -XX:MaxGCPauseMillis=200: 最大GC暂停时间200ms
REM -XX:ParallelGCThreads=4: GC并行线程数（等于CPU核心数）
REM -XX:ConcGCThreads=2: 并发GC线程数
REM -XX:+UseStringDeduplication: 字符串去重（节省内存）
REM -XX:+OptimizeStringConcat: 优化字符串连接
REM -XX:+UseCompressedOops: 使用压缩指针（节省内存）
REM -XX:+UseCompressedClassPointers: 使用压缩类指针
REM -Djava.awt.headless=true: 无头模式（服务器环境）
REM -Dfile.encoding=UTF-8: 文件编码
REM -Duser.timezone=Asia/Shanghai: 时区设置

java -Xms2g ^
     -Xmx3g ^
     -XX:+UseG1GC ^
     -XX:MaxGCPauseMillis=200 ^
     -XX:ParallelGCThreads=4 ^
     -XX:ConcGCThreads=2 ^
     -XX:+UseStringDeduplication ^
     -XX:+OptimizeStringConcat ^
     -XX:+UseCompressedOops ^
     -XX:+UseCompressedClassPointers ^
     -Djava.awt.headless=true ^
     -Dfile.encoding=UTF-8 ^
     -Duser.timezone=Asia/Shanghai ^
     -jar "%JAR_FILE%" ^
     --spring.profiles.active=prod

echo.
echo 应用已启动
echo 访问地址: http://localhost:8080
pause

