#!/bin/bash

# RTK数据转发服务快速启动脚本
# 用于本地开发测试

set -e

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== RTK数据转发服务快速启动 ===${NC}"

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "❌ 未找到Java环境，请先安装Java 8+"
    exit 1
fi

echo -e "${GREEN}✅ Java环境检查通过${NC}"

# 检查Maven环境
if ! command -v mvn &> /dev/null; then
    echo "❌ 未找到Maven环境，请先安装Maven"
    exit 1
fi

echo -e "${GREEN}✅ Maven环境检查通过${NC}"

# 构建项目
echo -e "${BLUE}📦 正在构建项目...${NC}"
mvn clean package -DskipTests

# 检查构建结果
JAR_FILE=$(find target -name "rtk-data-relay-1.0.0-*.jar" -type f | head -1)
if [[ -z "$JAR_FILE" ]]; then
    echo "❌ 构建失败，未找到jar文件"
    exit 1
fi

echo -e "${GREEN}✅ 项目构建成功${NC}"

# 启动服务
echo -e "${BLUE}🚀 正在启动RTK数据转发服务...${NC}"
echo "服务将在以下端口启动："
echo "  - Web API: 8080"
echo "  - 基站接入: 9001"
echo "  - 移动站接入: 9002"
echo ""
echo "按 Ctrl+C 停止服务"
echo ""

# 启动服务时抑制Spring Boot版本警告
java -Dspring.main.banner-mode=off \
     -Djava.awt.headless=true \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar "$JAR_FILE"
