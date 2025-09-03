#!/bin/bash
# =====================================================
# create-installer.sh
# 创建 RTK 数据转发服务的自解压安装包
# 支持 Ubuntu/Debian/CentOS/RHEL
# =====================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 版本信息
VERSION="1.0.0"
BUILD_DATE=$(date +%Y%m%d)
INSTALLER_NAME="rtk-data-relay-installer-${VERSION}-${BUILD_DATE}.run"

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# 创建安装包目录结构
create_package_structure() {
    log_step "创建安装包目录结构..."

    # 清理旧的构建目录
    rm -rf installer-build
    mkdir -p installer-build/{bin,config,scripts,service}

    log_info "目录结构创建完成"
}

# 构建项目
build_project() {
    log_step "构建项目..."

    if [[ ! -f "pom.xml" ]]; then
        log_error "未找到 pom.xml，请在项目根目录运行"
        exit 1
    fi

    mvn clean package -DskipTests

    # 查找生成的JAR文件
    JAR_FILE=$(find target -name "rtk-data-relay-*.jar" -type f | head -1)
    if [[ -z "$JAR_FILE" ]]; then
        log_error "构建失败，未找到JAR文件"
        exit 1
    fi

    # 复制JAR文件
    cp "$JAR_FILE" installer-build/bin/rtk-data-relay.jar

    log_info "项目构建完成"
}

# 准备配置文件
prepare_configs() {
    log_step "准备配置文件..."

    # 复制配置文件
    cp src/main/resources/application.yml installer-build/config/

    # 创建环境配置文件
    cat > installer-build/config/rtk-env.conf << 'EOF'
# RTK Data Relay Service Environment Configuration
RTK_HOME=/opt/rtk-data-relay
RTK_USER=rtk
RTK_GROUP=rtk
RTK_PORT_API=8899
RTK_PORT_BASE=9003
RTK_PORT_ROVER=9002
JVM_OPTS="-Xms512m -Xmx1024m -server"
EOF

    log_info "配置文件准备完成"
}

# 创建服务脚本
create_service_scripts() {
    log_step "创建服务脚本..."

    # systemd service 文件
    cat > installer-build/service/rtk-data-relay.service << 'EOF'
[Unit]
Description=RTK Data Relay Service
After=network.target

[Service]
Type=simple
User=rtk
Group=rtk
WorkingDirectory=/opt/rtk-data-relay
EnvironmentFile=/opt/rtk-data-relay/config/rtk-env.conf
ExecStart=/usr/bin/java $JVM_OPTS -jar /opt/rtk-data-relay/bin/rtk-data-relay.jar
ExecStop=/bin/kill -TERM $MAINPID
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=rtk-data-relay
LimitNOFILE=65536
LimitNPROC=32768

[Install]
WantedBy=multi-user.target
EOF

    # init.d 脚本（用于旧系统）
    cat > installer-build/service/rtk-data-relay.init << 'EOF'
#!/bin/bash
# chkconfig: 2345 85 15
# description: RTK Data Relay Service

### BEGIN INIT INFO
# Provides:          rtk-data-relay
# Required-Start:    $network $local_fs
# Required-Stop:     $network $local_fs
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: RTK Data Relay Service
### END INIT INFO

PROG="rtk-data-relay"
RTK_HOME="/opt/rtk-data-relay"
PID_FILE="/var/run/rtk-data-relay.pid"
LOG_FILE="/var/log/rtk-data-relay.log"

start() {
    echo "Starting $PROG..."
    if [ -f $PID_FILE ]; then
        echo "$PROG is already running."
        exit 1
    fi

    cd $RTK_HOME
    nohup java -jar $RTK_HOME/bin/rtk-data-relay.jar > $LOG_FILE 2>&1 &
    echo $! > $PID_FILE
    echo "$PROG started."
}

stop() {
    echo "Stopping $PROG..."
    if [ ! -f $PID_FILE ]; then
        echo "$PROG is not running."
        exit 1
    fi

    kill $(cat $PID_FILE)
    rm -f $PID_FILE
    echo "$PROG stopped."
}

status() {
    if [ -f $PID_FILE ]; then
        PID=$(cat $PID_FILE)
        if ps -p $PID > /dev/null; then
            echo "$PROG is running (PID: $PID)"
        else
            echo "$PROG is not running (stale PID file)"
        fi
    else
        echo "$PROG is not running"
    fi
}

case "$1" in
    start)   start ;;
    stop)    stop ;;
    restart) stop && start ;;
    status)  status ;;
    *)       echo "Usage: $0 {start|stop|restart|status}" ;;
esac
EOF

    chmod +x installer-build/service/rtk-data-relay.init

    log_info "服务脚本创建完成"
}

# 创建主安装脚本
create_install_script() {
    log_step "创建安装脚本..."

    cat > installer-build/scripts/install.sh << 'INSTALL_SCRIPT'
#!/bin/bash

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 安装目录
INSTALL_DIR="/opt/rtk-data-relay"
SERVICE_NAME="rtk-data-relay"

# 日志函数
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

# 检测系统类型
detect_system() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$ID
        OS_VERSION=$VERSION_ID
    elif [ -f /etc/redhat-release ]; then
        OS="centos"
    elif [ -f /etc/debian_version ]; then
        OS="debian"
    else
        log_error "不支持的操作系统"
        exit 1
    fi

    log_info "检测到系统: $OS"
}

# 检查权限
check_root() {
    if [ "$EUID" -ne 0 ]; then
        log_error "请使用 root 权限运行"
        exit 1
    fi
}

# 安装Java
install_java() {
    log_step "检查Java环境..."

    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1)
        log_info "发现 Java: $JAVA_VERSION"
        return
    fi

    log_info "安装 Java..."

    case "$OS" in
        ubuntu|debian)
            apt-get update
            apt-get install -y openjdk-17-jdk
            ;;
        centos|rhel|fedora)
            yum install -y java-17-openjdk
            ;;
        *)
            log_error "不支持的系统: $OS"
            exit 1
            ;;
    esac

    log_info "Java 安装完成"
}

# 创建用户
create_user() {
    log_step "创建服务用户..."

    if ! id "rtk" &>/dev/null; then
        useradd -r -s /bin/false rtk
        log_info "创建用户 rtk"
    else
        log_info "用户 rtk 已存在"
    fi
}

# 安装文件
install_files() {
    log_step "安装程序文件..."

    # 创建目录
    mkdir -p $INSTALL_DIR/{bin,config,logs,data,backup}

    # 复制文件
    cp -r bin/* $INSTALL_DIR/bin/
    cp -r config/* $INSTALL_DIR/config/

    # 设置权限
    chown -R rtk:rtk $INSTALL_DIR
    chmod 755 $INSTALL_DIR/bin/*

    log_info "文件安装完成"
}

# 配置服务
configure_service() {
    log_step "配置系统服务..."

    # 检测 systemd
    if command -v systemctl &> /dev/null; then
        # 使用 systemd
        cp service/rtk-data-relay.service /etc/systemd/system/
        systemctl daemon-reload
        systemctl enable $SERVICE_NAME
        SERVICE_TYPE="systemd"
    else
        # 使用 init.d
        cp service/rtk-data-relay.init /etc/init.d/$SERVICE_NAME
        chmod +x /etc/init.d/$SERVICE_NAME

        if command -v chkconfig &> /dev/null; then
            chkconfig --add $SERVICE_NAME
            chkconfig $SERVICE_NAME on
        elif command -v update-rc.d &> /dev/null; then
            update-rc.d $SERVICE_NAME defaults
        fi
        SERVICE_TYPE="init.d"
    fi

    log_info "服务配置完成 (类型: $SERVICE_TYPE)"
}

# 配置防火墙
configure_firewall() {
    log_step "配置防火墙..."

    # firewalld (CentOS/RHEL 7+)
    if command -v firewall-cmd &> /dev/null; then
        firewall-cmd --permanent --add-port=8899/tcp
        firewall-cmd --permanent --add-port=9003/tcp
        firewall-cmd --permanent --add-port=9002/tcp
        firewall-cmd --reload
        log_info "firewalld 规则已添加"

    # ufw (Ubuntu)
    elif command -v ufw &> /dev/null; then
        ufw allow 8899/tcp comment "RTK API"
        ufw allow 9003/tcp comment "RTK Base"
        ufw allow 9002/tcp comment "RTK Rover"
        log_info "ufw 规则已添加"

    # iptables (通用)
    elif command -v iptables &> /dev/null; then
        iptables -I INPUT -p tcp --dport 8899 -j ACCEPT
        iptables -I INPUT -p tcp --dport 9003 -j ACCEPT
        iptables -I INPUT -p tcp --dport 9002 -j ACCEPT

        # 保存规则
        if command -v iptables-save &> /dev/null; then
            iptables-save > /etc/iptables/rules.v4 2>/dev/null || \
            iptables-save > /etc/sysconfig/iptables 2>/dev/null
        fi
        log_info "iptables 规则已添加"
    else
        log_warn "未检测到防火墙，请手动开放端口 8899, 9003, 9002"
    fi
}

# 启动服务
start_service() {
    log_step "启动服务..."

    if [ "$SERVICE_TYPE" = "systemd" ]; then
        systemctl start $SERVICE_NAME
        sleep 3
        if systemctl is-active --quiet $SERVICE_NAME; then
            log_info "服务启动成功"
            systemctl status $SERVICE_NAME --no-pager
        else
            log_error "服务启动失败"
            exit 1
        fi
    else
        service $SERVICE_NAME start
        log_info "服务已启动"
    fi
}

# 显示安装信息
show_info() {
    echo ""
    echo "========================================="
    echo "   RTK 数据转发服务安装成功！"
    echo "========================================="
    echo ""
    echo "安装目录: $INSTALL_DIR"
    echo ""
    echo "服务端口:"
    echo "  - Web API: 8899"
    echo "  - 基站接入: 9003"
    echo "  - 移动站接入: 9002"
    echo ""
    echo "服务管理:"
    if [ "$SERVICE_TYPE" = "systemd" ]; then
        echo "  启动: systemctl start $SERVICE_NAME"
        echo "  停止: systemctl stop $SERVICE_NAME"
        echo "  重启: systemctl restart $SERVICE_NAME"
        echo "  状态: systemctl status $SERVICE_NAME"
        echo "  日志: journalctl -u $SERVICE_NAME -f"
    else
        echo "  启动: service $SERVICE_NAME start"
        echo "  停止: service $SERVICE_NAME stop"
        echo "  重启: service $SERVICE_NAME restart"
        echo "  状态: service $SERVICE_NAME status"
    fi
    echo ""
    echo "Web监控: http://$(hostname -I | awk '{print $1}'):8899/api/monitor/status"
    echo ""
    echo "========================================="
}

# 主函数
main() {
    echo ""
    echo "RTK 数据转发服务安装程序"
    echo "版本: 1.0.0"
    echo ""

    check_root
    detect_system
    install_java
    create_user
    install_files
    configure_service
    configure_firewall
    start_service
    show_info

    log_info "安装完成！"
}

main "$@"
INSTALL_SCRIPT

    chmod +x installer-build/scripts/install.sh

    # 创建卸载脚本
    cat > installer-build/scripts/uninstall.sh << 'UNINSTALL_SCRIPT'
#!/bin/bash

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}[警告]${NC} 即将卸载 RTK 数据转发服务"
read -p "确认卸载? (y/n): " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "取消卸载"
    exit 0
fi

# 停止服务
if systemctl list-units --full -all | grep -q "rtk-data-relay.service"; then
    systemctl stop rtk-data-relay
    systemctl disable rtk-data-relay
    rm -f /etc/systemd/system/rtk-data-relay.service
    systemctl daemon-reload
elif [ -f /etc/init.d/rtk-data-relay ]; then
    service rtk-data-relay stop
    chkconfig --del rtk-data-relay 2>/dev/null || update-rc.d -f rtk-data-relay remove
    rm -f /etc/init.d/rtk-data-relay
fi

# 删除文件
rm -rf /opt/rtk-data-relay

# 删除用户（可选）
read -p "是否删除 rtk 用户? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    userdel rtk 2>/dev/null || true
fi

echo -e "${GREEN}[完成]${NC} RTK 数据转发服务已卸载"
UNINSTALL_SCRIPT

    chmod +x installer-build/scripts/uninstall.sh

    log_info "安装脚本创建完成"
}

# 创建自解压安装包
create_self_extracting_package() {
    log_step "创建自解压安装包..."

    # 创建压缩包
    cd installer-build
    tar czf ../installer-payload.tar.gz *
    cd ..

    # 创建自解压脚本头
    cat > installer-header.sh << 'HEADER'
#!/bin/bash

# RTK Data Relay Service Installer
# Self-extracting archive

set -e

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo ""
echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}     RTK 数据转发服务 - 自动安装程序${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# 创建临时目录
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

echo -e "${GREEN}[1/3]${NC} 解压安装文件..."

# 获取自身大小
ARCHIVE_LINE=$(awk '/^__ARCHIVE_BELOW__/ {print NR + 1; exit 0; }' "$0")

# 解压数据
tail -n +$ARCHIVE_LINE "$0" | tar xz -C "$TEMP_DIR"

echo -e "${GREEN}[2/3]${NC} 执行安装程序..."
cd "$TEMP_DIR"

# 运行安装脚本
bash scripts/install.sh

echo -e "${GREEN}[3/3]${NC} 清理临时文件..."

exit 0

__ARCHIVE_BELOW__
HEADER

    # 合并成最终安装包
    cat installer-header.sh installer-payload.tar.gz > "$INSTALLER_NAME"
    chmod +x "$INSTALLER_NAME"

    # 清理临时文件
    rm -f installer-header.sh installer-payload.tar.gz
    rm -rf installer-build

    log_info "安装包创建成功: $INSTALLER_NAME"
    log_info "文件大小: $(du -h $INSTALLER_NAME | cut -f1)"
}

# 主函数
main() {
    log_info "开始创建 RTK 安装包..."

    create_package_structure
    build_project
    prepare_configs
    create_service_scripts
    create_install_script
    create_self_extracting_package

    echo ""
    echo "========================================="
    echo "   安装包创建成功！"
    echo "========================================="
    echo ""
    echo "文件名: $INSTALLER_NAME"
    echo "大小: $(du -h $INSTALLER_NAME | cut -f1)"
    echo ""
    echo "使用方法:"
    echo "  1. 上传到目标服务器:"
    echo "     scp $INSTALLER_NAME user@server:/tmp/"
    echo ""
    echo "  2. 在服务器上执行:"
    echo "     sudo bash /tmp/$INSTALLER_NAME"
    echo ""
    echo "支持的系统:"
    echo "  - Ubuntu 18.04+"
    echo "  - Debian 10+"
    echo "  - CentOS 7+"
    echo "  - RHEL 7+"
    echo ""
    echo "========================================="
}

main "$@"