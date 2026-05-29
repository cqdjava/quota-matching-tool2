#!/bin/bash

# 定额匹配工具部署和迁移脚本
# 适用于Linux服务器环境

# 配置变量
APP_NAME="quota-matching-tool"
APP_JAR="target/${APP_NAME}-0.0.1-SNAPSHOT.jar"
MIGRATION_CLASS="com.enterprise.quota.util.DatabaseMigrationTool"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== 定额匹配工具部署脚本 ===${NC}"

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo -e "${RED}错误: 未找到Java环境${NC}"
    exit 1
fi

echo -e "${YELLOW}检查Java版本...${NC}"
java -version

# 编译项目
echo -e "${YELLOW}编译项目...${NC}"
if ! mvn clean package -DskipTests; then
    echo -e "${RED}编译失败${NC}"
    exit 1
fi

# 执行数据库迁移
echo -e "${YELLOW}执行数据库迁移...${NC}"
if java -cp "${APP_JAR}" "${MIGRATION_CLASS}"; then
    echo -e "${GREEN}数据库迁移成功${NC}"
else
    echo -e "${RED}数据库迁移失败${NC}"
    exit 1
fi

# 启动应用程序
echo -e "${YELLOW}启动应用程序...${NC}"
nohup java -jar "${APP_JAR}" > app.log 2>&1 &

# 保存进程ID
echo $! > app.pid

echo -e "${GREEN}应用程序已启动，PID: $(cat app.pid)${NC}"
echo -e "${GREEN}日志文件: app.log${NC}"
echo -e "${GREEN}部署完成！${NC}"