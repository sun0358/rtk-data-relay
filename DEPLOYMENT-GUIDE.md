# RTK数据转发服务完整部署指南

## 🎯 项目概述

RTK数据转发服务是一个专门为RTK差分定位设计的高性能TCP数据转发系统。该服务实现了基站到移动站的实时数据转发，具备自动故障恢复、连接管理、实时监控等企业级特性。

### 核心功能
- **双端口TCP服务**: Server1(9001)接收基站数据，Server2(9002)转发给移动站
- **高并发支持**: 基于Netty框架，支持最多10个移动站同时连接
- **自动故障恢复**: 连接断开自动重连，异常自动恢复
- **实时监控**: Web API监控接口，实时查看连接状态和统计信息
- **系统服务**: 支持systemd管理，开机自启动，进程守护

## 🚀 完整部署流程

### 第一步：MacBook开发环境准备

```bash
# 1. 确认Java环境
java -version
# 确保Java 8+

# 2. 确认Maven环境
mvn -version
# 确保Maven 3.6+

# 3. 进入项目目录
cd ~/company/prj/Java/rtk-data-relay

# 4. 构建项目
mvn clean package -DskipTests

# 5. 验证构建结果
ls -la target/rtk-data-relay-1.0.0.jar
```

### 第二步：Ubuntu服务器环境准备

在Ubuntu 24.04服务器上执行：

```bash
# 1. 更新系统
sudo apt update && sudo apt upgrade -y

# 2. 安装Java环境（如果未安装）
sudo apt install -y openjdk-8-jdk

# 3. 验证Java安装
java -version

# 4. 创建部署目录
mkdir -p ~/rtk-deploy
```

### 第三步：自动化部署

**推荐方式：使用自动部署脚本**

```bash
# 在MacBook的项目根目录执行
./deploy/build-and-deploy.sh <Ubuntu服务器IP> <用户名> [SSH私钥路径]

# 示例
./deploy/build-and-deploy.sh 192.168.1.100 ubuntu
./deploy/build-and-deploy.sh 192.168.1.100 ubuntu ~/.ssh/id_rsa
```

**手动部署方式：**

```bash
# 1. 上传文件到Ubuntu服务器
scp target/rtk-data-relay-1.0.0.jar ubuntu@192.168.1.100:~/rtk-deploy/
scp deploy/rtk-data-relay.service ubuntu@192.168.1.100:~/rtk-deploy/
scp deploy/install.sh ubuntu@192.168.1.100:~/rtk-deploy/
scp src/main/resources/application.yml ubuntu@192.168.1.100:~/rtk-deploy/

# 2. 连接到Ubuntu服务器
ssh ubuntu@192.168.1.100

# 3. 执行安装
cd ~/rtk-deploy
chmod +x install.sh
sudo ./install.sh
```

### 第四步：服务验证

```bash
# 1. 检查服务状态
sudo systemctl status rtk-data-relay

# 2. 检查端口监听
sudo netstat -tlnp | grep -E ':(8080|9001|9002)'

# 3. 测试API接口
curl http://localhost:8080/api/monitor/status

# 4. 查看服务日志
sudo journalctl -u rtk-data-relay -f
```

## 🔧 设备配置指南

### 基站设备配置

**连接参数:**
```
服务器地址: <frp服务器公网IP>
端口: 19001 (通过frp映射到内网9001)
协议: TCP
模式: 客户端模式
数据格式: RTK差分修正数据（RTCM格式）
```

**配置示例（以某RTK基站为例）:**
```
Network Settings:
- Connection Type: TCP Client
- Server IP: xxx.xxx.xxx.xxx (frp服务器公网IP)
- Server Port: 19001
- Auto Reconnect: Yes
- Reconnect Interval: 5 seconds
```

### 移动站设备配置

**连接参数:**
```
服务器地址: <frp服务器公网IP>
端口: 19002 (通过frp映射到内网9002)
协议: TCP
模式: 客户端模式
```

**配置示例（以某RTK移动站为例）:**
```
Correction Input Settings:
- Source: Network (TCP)
- Server IP: xxx.xxx.xxx.xxx (frp服务器公网IP)
- Server Port: 19002
- Auto Reconnect: Yes
- Timeout: 30 seconds
```

### frpc端口映射配置

在您的frpc配置文件中添加：

```ini
# RTK服务Web API端口映射
[rtk-web-api]
type = tcp
local_ip = 127.0.0.1
local_port = 8080
remote_port = 18080

# RTK基站接入端口映射
[rtk-base-station]
type = tcp
local_ip = 127.0.0.1
local_port = 9001
remote_port = 19001

# RTK移动站接入端口映射
[rtk-mobile-station]
type = tcp
local_ip = 127.0.0.1
local_port = 9002
remote_port = 19002
```

## 📊 监控和维护

### Web监控界面

访问以下URL查看服务状态：

| 监控项目 | URL | 说明 |
|---------|-----|------|
| 服务状态 | `http://<服务器IP>:18080/api/monitor/status` | 基本状态信息 |
| 连接信息 | `http://<服务器IP>:18080/api/monitor/connections` | 所有连接详情 |
| 统计信息 | `http://<服务器IP>:18080/api/monitor/statistics` | 数据传输统计 |
| 健康检查 | `http://<服务器IP>:18080/actuator/health` | 系统健康状态 |

### 服务管理命令

```bash
# 服务控制
sudo systemctl start rtk-data-relay      # 启动服务
sudo systemctl stop rtk-data-relay       # 停止服务
sudo systemctl restart rtk-data-relay    # 重启服务
sudo systemctl status rtk-data-relay     # 查看状态

# 日志查看
sudo journalctl -u rtk-data-relay -f     # 实时日志
sudo journalctl -u rtk-data-relay --since "1 hour ago"  # 最近1小时日志
tail -f /opt/rtk-data-relay/logs/rtk-relay.log          # 应用日志文件

# 性能监控
top -p $(pgrep -f rtk-data-relay)        # CPU和内存使用
sudo ss -tln | grep -E ':(9001|9002)'    # 端口连接数
```

## 🛠️ 故障排除

### 常见问题及解决方案

1. **服务无法启动**
   ```bash
   # 检查Java环境
   java -version
   
   # 检查端口占用
   sudo netstat -tlnp | grep -E ':(8080|9001|9002)'
   
   # 查看详细错误
   sudo journalctl -u rtk-data-relay --since "5 minutes ago"
   ```

2. **基站无法连接**
   ```bash
   # 测试端口连通性
   telnet <服务器IP> 9001
   
   # 检查防火墙
   sudo ufw status
   
   # 查看连接日志
   curl http://localhost:8080/api/monitor/connections/base-stations
   ```

3. **移动站接收不到数据**
   ```bash
   # 检查移动站连接
   curl http://localhost:8080/api/monitor/connections/mobile-stations
   
   # 查看转发统计
   curl http://localhost:8080/api/monitor/statistics
   
   # 检查错误日志
   sudo journalctl -u rtk-data-relay | grep ERROR
   ```

4. **内存使用过高**
   ```bash
   # 调整JVM内存参数
   sudo vim /etc/systemd/system/rtk-data-relay.service
   # 修改 -Xms512m -Xmx1024m 参数
   
   sudo systemctl daemon-reload
   sudo systemctl restart rtk-data-relay
   ```

### 性能优化建议

1. **JVM参数优化**
   ```bash
   # 编辑服务文件
   sudo vim /etc/systemd/system/rtk-data-relay.service
   
   # 优化JVM参数
   ExecStart=/usr/bin/java -jar \
     -Xms512m -Xmx1024m \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=100 \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/opt/rtk-data-relay/logs/ \
     rtk-data-relay-1.0.0.jar
   ```

2. **系统网络参数优化**
   ```bash
   # 编辑系统参数
   sudo vim /etc/sysctl.conf
   
   # 添加网络优化参数
   net.core.somaxconn = 1024
   net.core.netdev_max_backlog = 5000
   net.ipv4.tcp_max_syn_backlog = 1024
   
   # 应用配置
   sudo sysctl -p
   ```

## 📈 运行状态示例

### 正常运行状态

```json
{
  "serverRunning": true,
  "activeBaseStations": 1,
  "activeMobileStations": 3,
  "timestamp": 1672531200000,
  "message": "RTK数据转发服务运行正常"
}
```

### 统计信息示例

```json
{
  "startTime": "2024-01-01T08:00:00",
  "currentBaseStationConnections": 1,
  "currentMobileStationConnections": 3,
  "totalBaseStationConnections": 5,
  "totalMobileStationConnections": 12,
  "totalReceivedBytes": 1048576,
  "totalSentBytes": 3145728,
  "totalReceivedMessages": 1000,
  "totalSentMessages": 3000,
  "connectionErrors": 0,
  "relayErrors": 0
}
```

## 🔄 服务更新流程

```bash
# 1. 在MacBook上构建新版本
cd ~/company/prj/Java/rtk-data-relay
mvn clean package -DskipTests

# 2. 停止Ubuntu服务器上的服务
ssh ubuntu@<服务器IP>
sudo systemctl stop rtk-data-relay

# 3. 备份当前版本
sudo cp /opt/rtk-data-relay/rtk-data-relay-1.0.0.jar \
        /opt/rtk-data-relay/rtk-data-relay-1.0.0.jar.bak.$(date +%Y%m%d_%H%M%S)

# 4. 上传新版本
exit
scp target/rtk-data-relay-1.0.0.jar ubuntu@<服务器IP>:/tmp/

# 5. 替换文件并启动
ssh ubuntu@<服务器IP>
sudo cp /tmp/rtk-data-relay-1.0.0.jar /opt/rtk-data-relay/
sudo chown rtk:rtk /opt/rtk-data-relay/rtk-data-relay-1.0.0.jar
sudo systemctl start rtk-data-relay

# 6. 验证更新
curl http://localhost:8080/api/monitor/status
```

## 📋 部署检查清单

### 部署前检查
- [ ] MacBook Java环境正常（Java 8+）
- [ ] Maven构建工具正常
- [ ] 项目编译无错误
- [ ] Ubuntu服务器网络连通
- [ ] SSH连接正常
- [ ] Ubuntu服务器有sudo权限

### 部署后检查
- [ ] 服务状态正常 (`systemctl status rtk-data-relay`)
- [ ] 端口监听正常 (`netstat -tlnp`)
- [ ] Web API响应正常 (`curl http://localhost:8080/api/monitor/status`)
- [ ] 日志无ERROR级别错误
- [ ] frpc端口映射配置正确
- [ ] 防火墙规则配置正确

### 功能测试检查
- [ ] 基站能够成功连接到Server1(9001)
- [ ] 移动站能够成功连接到Server2(9002)
- [ ] 数据转发功能正常
- [ ] 连接断开后能够自动重连
- [ ] 监控接口显示正确的连接和统计信息

## 🔐 安全配置建议

### 1. 防火墙配置
```bash
# 只允许必要的端口
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow ssh
sudo ufw allow 8080/tcp
sudo ufw allow 9001/tcp
sudo ufw allow 9002/tcp
sudo ufw enable
```

### 2. 用户权限配置
```bash
# 服务使用专用用户运行，权限最小化
sudo usermod -s /bin/false rtk  # 禁止shell登录
sudo chmod 750 /opt/rtk-data-relay  # 限制目录权限
```

### 3. 日志安全
```bash
# 设置日志轮转，防止磁盘空间耗尽
sudo vim /etc/logrotate.d/rtk-data-relay

# 添加以下内容：
/opt/rtk-data-relay/logs/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 644 rtk rtk
}
```

## 📞 技术支持

### 联系方式
- 技术支持: [联系信息]
- 问题反馈: [反馈渠道]

### 紧急故障处理
1. 立即查看服务状态和日志
2. 尝试重启服务
3. 检查网络连接和防火墙
4. 如问题持续，联系技术支持

---

**注意事项:**
- 部署前请仔细阅读本文档
- 建议在测试环境先验证部署流程
- 生产环境部署建议安排维护窗口
- 定期备份配置文件和日志
