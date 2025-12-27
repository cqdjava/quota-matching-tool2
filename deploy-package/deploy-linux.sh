#!/bin/bash

# Linux服务器部署脚本
# 使用方法: ./deploy-linux.sh

set -e  # 遇到错误立即退出

echo "=========================================="
echo "企业定额管理系统 - Linux部署脚本"
echo "=========================================="

# 配置变量（根据实际情况修改）
APP_NAME="quota-matching-tool"
APP_VERSION="1.0.0"
JAR_FILE="target/${APP_NAME}-${APP_VERSION}.jar"
DEPLOY_DIR="/opt/${APP_NAME}"
SERVICE_USER="${APP_NAME}"
LOG_DIR="/var/log/${APP_NAME}"
SERVICE_FILE="/etc/systemd/system/${APP_NAME}.service"

# 检查JAR文件是否存在
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: JAR文件不存在: $JAR_FILE"
    echo "请先运行打包脚本: ./package.sh 或 mvn clean package -DskipTests"
    exit 1
fi

echo ""
echo "1. 检查系统环境..."
echo "=========================================="

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java，请先安装Java 8或更高版本"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1)
echo "✓ Java环境: $JAVA_VERSION"

# 检查是否为root用户（某些操作需要root权限）
if [ "$EUID" -ne 0 ]; then 
    echo "警告: 当前不是root用户，某些操作可能需要sudo权限"
    SUDO_CMD="sudo"
else
    SUDO_CMD=""
fi

echo ""
echo "2. 创建部署目录..."
echo "=========================================="

$SUDO_CMD mkdir -p $DEPLOY_DIR
$SUDO_CMD mkdir -p $LOG_DIR
echo "✓ 部署目录: $DEPLOY_DIR"
echo "✓ 日志目录: $LOG_DIR"

echo ""
echo "3. 创建应用用户（如果不存在）..."
echo "=========================================="

if id "$SERVICE_USER" &>/dev/null; then
    echo "✓ 用户 $SERVICE_USER 已存在"
else
    $SUDO_CMD useradd -r -s /bin/false $SERVICE_USER
    echo "✓ 已创建用户: $SERVICE_USER"
fi

echo ""
echo "4. 复制JAR文件..."
echo "=========================================="

$SUDO_CMD cp $JAR_FILE $DEPLOY_DIR/${APP_NAME}.jar
$SUDO_CMD chown $SERVICE_USER:$SERVICE_USER $DEPLOY_DIR/${APP_NAME}.jar
$SUDO_CMD chmod 755 $DEPLOY_DIR/${APP_NAME}.jar
echo "✓ JAR文件已复制到: $DEPLOY_DIR/${APP_NAME}.jar"

echo ""
echo "5. 创建启动脚本..."
echo "=========================================="

cat > /tmp/start.sh << EOF
#!/bin/bash
cd $DEPLOY_DIR

# 设置Java环境变量（根据实际情况修改）
# export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
# export PATH=\$JAVA_HOME/bin:\$PATH

# 设置生产环境配置
export SPRING_PROFILES_ACTIVE=prod

# 设置数据库连接（根据实际情况修改）
# export DB_USERNAME=root
# export DB_PASSWORD=your_password

# 设置日志路径
export LOG_PATH=$LOG_DIR/application.log

# 启动应用（4核4G服务器优化参数）
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
     -jar ${APP_NAME}.jar
EOF

$SUDO_CMD mv /tmp/start.sh $DEPLOY_DIR/start.sh
$SUDO_CMD chmod +x $DEPLOY_DIR/start.sh
$SUDO_CMD chown $SERVICE_USER:$SERVICE_USER $DEPLOY_DIR/start.sh
echo "✓ 启动脚本已创建: $DEPLOY_DIR/start.sh"

echo ""
echo "6. 创建停止脚本..."
echo "=========================================="

cat > /tmp/stop.sh << EOF
#!/bin/bash
PID=\$(ps -ef | grep ${APP_NAME}.jar | grep -v grep | awk '{print \$2}')

if [ -z "\$PID" ]; then
    echo "应用未运行"
else
    echo "正在停止应用，PID: \$PID"
    kill \$PID
    sleep 3
    
    # 如果还在运行，强制杀死
    if ps -p \$PID > /dev/null 2>&1; then
        echo "强制停止应用"
        kill -9 \$PID
    fi
    
    echo "应用已停止"
fi
EOF

$SUDO_CMD mv /tmp/stop.sh $DEPLOY_DIR/stop.sh
$SUDO_CMD chmod +x $DEPLOY_DIR/stop.sh
$SUDO_CMD chown $SERVICE_USER:$SERVICE_USER $DEPLOY_DIR/stop.sh
echo "✓ 停止脚本已创建: $DEPLOY_DIR/stop.sh"

echo ""
echo "7. 创建Systemd服务文件..."
echo "=========================================="

cat > /tmp/${APP_NAME}.service << EOF
[Unit]
Description=Enterprise Quota Matching Tool
After=network.target mysql.service
Wants=network.target

[Service]
Type=simple
User=$SERVICE_USER
WorkingDirectory=$DEPLOY_DIR
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="JAVA_OPTS=-Xms2g -Xmx3g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:ParallelGCThreads=4 -XX:ConcGCThreads=2 -XX:+UseStringDeduplication -XX:+OptimizeStringConcat -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"
Environment="LOG_PATH=$LOG_DIR/application.log"
# 数据库配置（根据实际情况修改）
# Environment="DB_USERNAME=root"
# Environment="DB_PASSWORD=your_password"
ExecStart=/usr/bin/java \$JAVA_OPTS -jar $DEPLOY_DIR/${APP_NAME}.jar
ExecStop=/bin/kill -15 \$MAINPID
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=${APP_NAME}

[Install]
WantedBy=multi-user.target
EOF

$SUDO_CMD mv /tmp/${APP_NAME}.service $SERVICE_FILE
$SUDO_CMD chmod 644 $SERVICE_FILE
echo "✓ Systemd服务文件已创建: $SERVICE_FILE"

echo ""
echo "8. 设置目录权限..."
echo "=========================================="

$SUDO_CMD chown -R $SERVICE_USER:$SERVICE_USER $DEPLOY_DIR
$SUDO_CMD chown -R $SERVICE_USER:$SERVICE_USER $LOG_DIR
$SUDO_CMD chmod 755 $DEPLOY_DIR
$SUDO_CMD chmod 755 $LOG_DIR
echo "✓ 目录权限已设置"

echo ""
echo "9. 重载Systemd配置..."
echo "=========================================="

$SUDO_CMD systemctl daemon-reload
echo "✓ Systemd配置已重载"

echo ""
echo "=========================================="
echo "部署完成！"
echo "=========================================="
echo ""
echo "下一步操作："
echo ""
echo "1. 编辑服务文件，配置数据库连接："
echo "   $SUDO_CMD nano $SERVICE_FILE"
echo ""
echo "2. 启动服务："
echo "   $SUDO_CMD systemctl start ${APP_NAME}"
echo ""
echo "3. 设置开机自启："
echo "   $SUDO_CMD systemctl enable ${APP_NAME}"
echo ""
echo "4. 查看服务状态："
echo "   $SUDO_CMD systemctl status ${APP_NAME}"
echo ""
echo "5. 查看日志："
echo "   $SUDO_CMD journalctl -u ${APP_NAME} -f"
echo "   或"
echo "   tail -f $LOG_DIR/application.log"
echo ""
echo "6. 检查端口："
echo "   netstat -tlnp | grep 8080"
echo ""
echo "=========================================="

