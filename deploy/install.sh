#!/bin/bash

# RTK数据转发服务安装脚本
# 适用于Ubuntu 24.04系统

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# 检查是否为root用户
check_root() {
    if [[ $EUID -ne 0 ]]; then
        log_error "此脚本需要root权限运行"
        echo "请使用: sudo $0"
        exit 1
    fi
}

# 检查Java环境
check_java() {
    log_step "检查Java环境..."
    
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1 | awk -F '"' '{print $2}')
        log_info "发现Java版本: $JAVA_VERSION"
        
        # 检查Java版本是否为8或更高
        if java -version 2>&1 | grep -q "1.8\|11\|17\|21"; then
            log_info "Java版本符合要求"
        else
            log_warn "Java版本可能不兼容，建议使用Java 8+"
        fi
    else
        log_warn "未发现Java环境，正在安装OpenJDK 8..."
        apt update
        apt install -y openjdk-8-jdk
        
        # 设置JAVA_HOME
        echo 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64' >> /etc/environment
        source /etc/environment
        
        log_info "Java环境安装完成"
    fi
}

# 创建用户和目录
setup_user_and_directories() {
    log_step "创建用户和目录..."
    
    # 创建rtk用户（如果不存在）
    if ! id "rtk" &>/dev/null; then
        useradd -r -s /bin/false rtk
        log_info "创建用户 rtk"
    else
        log_info "用户 rtk 已存在"
    fi
    
    # 创建应用目录
    mkdir -p /opt/rtk-data-relay
    mkdir -p /opt/rtk-data-relay/logs
    mkdir -p /opt/rtk-data-relay/config
    
    # 设置目录权限
    chown -R rtk:rtk /opt/rtk-data-relay
    chmod 755 /opt/rtk-data-relay
    chmod 755 /opt/rtk-data-relay/logs
    
    log_info "目录创建完成"
}

# 全局变量：JAR文件路径
JAR_FILE=""

# 复制应用文件
copy_application_files() {
    log_step "复制应用文件..."
    
    # 检查jar文件是否存在
    JAR_FILE=$(find . -name "rtk-data-relay-1.0.0-*.jar" -type f | head -1)
    if [[ -z "$JAR_FILE" ]]; then
        log_error "未找到应用jar文件: rtk-data-relay-1.0.0-*.jar"
        log_error "请确保在包含jar文件的目录中运行此脚本"
        exit 1
    fi
    
    # 复制jar文件
    cp "$JAR_FILE" /opt/rtk-data-relay/
    chown rtk:rtk "/opt/rtk-data-relay/$(basename "$JAR_FILE")"
    chmod 644 "/opt/rtk-data-relay/$(basename "$JAR_FILE")"
    
    # 复制配置文件（如果存在）
    if [[ -f "application.yml" ]]; then
        cp application.yml /opt/rtk-data-relay/config/
        chown rtk:rtk /opt/rtk-data-relay/config/application.yml
        chmod 644 /opt/rtk-data-relay/config/application.yml
        log_info "配置文件已复制"
    fi
    
    log_info "应用文件复制完成"
}

# 安装系统服务
install_service() {
    log_step "安装系统服务..."
    
    # 检查服务文件是否存在
    if [[ ! -f "rtk-data-relay.service" ]]; then
        log_error "未找到服务文件: rtk-data-relay.service"
        exit 1
    fi
    
    # 动态生成服务文件，替换JAR文件名
    JAR_NAME=$(basename "$JAR_FILE")
    sed "s/rtk-data-relay-1.0.0-\*.jar/$JAR_NAME/g" rtk-data-relay.service > /etc/systemd/system/rtk-data-relay.service
    
    # 重新加载systemd
    systemctl daemon-reload
    
    # 启用服务（开机自启动）
    systemctl enable rtk-data-relay.service
    
    log_info "系统服务安装完成"
}

# 配置防火墙
configure_firewall() {
    log_step "配置防火墙..."
    
    # 检查ufw是否安装
    if command -v ufw &> /dev/null; then
        # 开放端口
        ufw allow 8899/tcp comment "RTK Relay Web API"
        ufw allow 9001/tcp comment "RTK Server1 - Base Station"
        ufw allow 9002/tcp comment "RTK Server2 - Mobile Station"
        
        log_info "防火墙规则已配置"
        log_info "已开放端口: 8899(Web API), 9001(基站接入), 9002(移动站接入)"
    else
        log_warn "未检测到ufw防火墙，请手动配置防火墙规则"
        log_warn "需要开放端口: 8899, 9001, 9002"
    fi
}

# 启动服务
start_service() {
    log_step "启动服务..."
    
    # 启动服务
    systemctl start rtk-data-relay.service
    
    # 等待服务启动
    sleep 3
    
    # 检查服务状态
    if systemctl is-active --quiet rtk-data-relay.service; then
        log_info "RTK数据转发服务启动成功"
        
        # 显示服务状态
        systemctl status rtk-data-relay.service --no-pager
        
        log_info "服务监控地址: http://localhost:8899/api/monitor/status"
        log_info "服务健康检查: http://localhost:8899/actuator/health"
    else
        log_error "RTK数据转发服务启动失败"
        log_error "请检查日志: journalctl -u rtk-data-relay.service -f"
        exit 1
    fi
}

# 显示使用说明
show_usage_info() {
    log_step "安装完成！"
    
    echo ""
    echo "=== RTK数据转发服务安装完成 ==="
    echo ""
    echo "服务管理命令："
    echo "  启动服务: sudo systemctl start rtk-data-relay"
    echo "  停止服务: sudo systemctl stop rtk-data-relay"
    echo "  重启服务: sudo systemctl restart rtk-data-relay"
    echo "  查看状态: sudo systemctl status rtk-data-relay"
    echo "  查看日志: sudo journalctl -u rtk-data-relay -f"
    echo ""
    echo "服务配置："
    echo "  Web API端口: 8899"
    echo "  基站接入端口: 9001"
    echo "  移动站接入端口: 9002"
    echo ""
    echo "监控地址："
    echo "  服务状态: http://localhost:8899/api/monitor/status"
    echo "  连接信息: http://localhost:8899/api/monitor/connections"
    echo "  统计信息: http://localhost:8899/api/monitor/statistics"
    echo "  健康检查: http://localhost:8899/actuator/health"
    echo ""
    echo "日志文件："
    echo "  应用日志: /opt/rtk-data-relay/logs/rtk-relay.log"
    echo "  系统日志: journalctl -u rtk-data-relay"
    echo ""
}

# 主函数
main() {
    log_info "开始安装RTK数据转发服务..."
    
    check_root
    check_java
    setup_user_and_directories
    copy_application_files
    install_service
    configure_firewall
    start_service
    show_usage_info
    
    log_info "RTK数据转发服务安装完成！"
}

# 执行主函数
main "$@"
