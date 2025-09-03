# RTK数据转发服务部署指南

## 部署流程概述

本文档详细说明了如何将RTK数据转发服务从MacBook开发环境部署到Ubuntu 24.04生产环境。

## 前置条件

### MacBook开发环境
- [x] Java 8+ (推荐Java 17)
- [x] Maven 3.6+
- [x] IntelliJ IDEA
- [x] SSH客户端

### Ubuntu服务器环境
- [x] Ubuntu 24.04 LTS
- [x] 网络连接正常
- [x] SSH服务已启用
- [x] 用户具有sudo权限

## 部署步骤

### 步骤1：在MacBook上构建项目

```bash
# 进入项目目录
cd ~/company/prj/Java/rtk-data-relay

# 清理并构建项目
mvn clean package -DskipTests

# 验证构建结果
ls -la target/rtk-data-relay-1.0.0.jar
```

### 步骤2：配置部署参数

编辑 `deploy/build-and-deploy.sh` 文件，设置以下变量：

```bash
UBUNTU_SERVER="192.168.1.100"  # Ubuntu服务器IP地址
UBUNTU_USER="ubuntu"           # Ubuntu服务器用户名
UBUNTU_SSH_KEY=""              # SSH私钥路径（可选）
```

### 步骤3：执行自动部署

```bash
# 方式一：使用配置文件中的参数
./deploy/build-and-deploy.sh

# 方式二：使用命令行参数
./deploy/build-and-deploy.sh 192.168.1.100 ubuntu

# 方式三：使用SSH私钥
./deploy/build-and-deploy.sh 192.168.1.100 ubuntu ~/.ssh/id_rsa
```

### 步骤4：验证部署结果

部署完成后，脚本会自动验证：
- 服务状态检查
- 端口监听检查
- API接口测试

## 手动部署（可选）

如果自动部署脚本失败，可以手动执行以下步骤：

### 1. 上传文件

```bash
# 上传jar文件
scp target/rtk-data-relay-1.0.0.jar ubuntu@192.168.1.100:~/

# 上传部署文件
scp deploy/rtk-data-relay.service ubuntu@192.168.1.100:~/
scp deploy/install.sh sun@192.168.5.15:~/rtk-data-relay/
scp src/main/resources/application.yml sun@192.168.5.15:~/rtk-data-relay/
```

### 2. 在Ubuntu服务器上安装

```bash
# 连接到Ubuntu服务器
ssh ubuntu@192.168.1.100

# 设置执行权限
chmod +x install.sh

# 执行安装脚本
sudo ./install.sh
```

## 服务配置

### 端口配置

| 端口 | 用途 | 协议 | 说明 |
|------|------|------|------|
| 8080 | Web API | HTTP | 监控和管理接口 |
| 9001 | 基站接入 | TCP | 接收基站RTK数据 |
| 9002 | 移动站接入 | TCP | 转发数据给移动站 |

### 防火墙配置

```bash
# 开放必要端口
sudo ufw allow 8080/tcp comment "RTK Relay Web API"
sudo ufw allow 9001/tcp comment "RTK Server1 - Base Station"
sudo ufw allow 9002/tcp comment "RTK Server2 - Mobile Station"

# 查看防火墙状态
sudo ufw status
```

### frpc端口映射配置

在您的frpc配置中添加以下配置：

```ini
[rtk-web-api]
type = tcp
local_ip = 127.0.0.1
local_port = 8080
remote_port = 18080

[rtk-base-station]
type = tcp
local_ip = 127.0.0.1
local_port = 9001
remote_port = 19001

[rtk-mobile-station]
type = tcp
local_ip = 127.0.0.1
local_port = 9002
remote_port = 19002
```

## 服务管理

### 基本操作

```bash
# 启动服务
sudo systemctl start rtk-data-relay

# 停止服务
sudo systemctl stop rtk-data-relay

# 重启服务
sudo systemctl restart rtk-data-relay

# 查看状态
sudo systemctl status rtk-data-relay

# 启用开机自启动
sudo systemctl enable rtk-data-relay

# 禁用开机自启动
sudo systemctl disable rtk-data-relay
```

### 日志管理

```bash
# 查看实时日志
sudo journalctl -u rtk-data-relay -f

# 查看最近的日志
sudo journalctl -u rtk-data-relay --since "1 hour ago"

# 查看错误日志
sudo journalctl -u rtk-data-relay | grep ERROR

# 查看应用日志文件
tail -f /opt/rtk-data-relay/logs/rtk-relay.log
```

## 监控和维护

### Web监控接口

```bash
# 服务状态检查
curl http://localhost:8080/api/monitor/status

# 连接信息查看
curl http://localhost:8080/api/monitor/connections

# 统计信息查看
curl http://localhost:8080/api/monitor/statistics

# 健康检查
curl http://localhost:8080/actuator/health
```

### 性能监控

```bash
# 查看CPU和内存使用情况
top -p $(pgrep -f rtk-data-relay)

# 查看网络连接
sudo netstat -tlnp | grep -E ':(8080|9001|9002)'

# 查看端口连接数
sudo ss -tln | grep -E ':(9001|9002)'
```

## 基站和移动站配置

### 基站配置示例

```
服务器地址: <frp服务器公网IP>
端口: 19001 (通过frp映射到内网9001)
协议: TCP
数据格式: RTK差分修正数据
连接模式: 客户端模式
```

### 移动站配置示例

```
服务器地址: <frp服务器公网IP>
端口: 19002 (通过frp映射到内网9002)
协议: TCP
接收模式: 被动接收
数据格式: RTK差分修正数据
```

## 故障排除

### 1. 服务无法启动

```bash
# 检查Java环境
java -version

# 检查端口占用
sudo netstat -tlnp | grep -E ':(8080|9001|9002)'

# 查看详细错误日志
sudo journalctl -u rtk-data-relay --since "5 minutes ago"

# 手动启动测试
cd /opt/rtk-data-relay
sudo -u rtk java -jar rtk-data-relay-1.0.0.jar
```

### 2. 连接问题

```bash
# 测试端口连通性
telnet <服务器IP> 9001
telnet <服务器IP> 9002

# 检查防火墙
sudo ufw status

# 检查连接状态
curl http://localhost:8080/api/monitor/connections
```

### 3. 数据转发异常

```bash
# 查看转发统计
curl http://localhost:8080/api/monitor/statistics

# 查看连接详情
curl http://localhost:8080/api/monitor/connections

# 检查错误日志
sudo journalctl -u rtk-data-relay | grep -E "(ERROR|WARN)"
```

## 性能优化建议

### 1. JVM参数调优

编辑服务文件 `/etc/systemd/system/rtk-data-relay.service`：

```ini
ExecStart=/usr/bin/java -jar \
  -Xms512m -Xmx1024m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/opt/rtk-data-relay/logs/ \
  rtk-data-relay-1.0.0.jar
```

### 2. 系统参数调优

```bash
# 编辑系统参数
sudo vim /etc/sysctl.conf

# 添加以下参数
net.core.somaxconn = 1024
net.core.netdev_max_backlog = 5000
net.ipv4.tcp_max_syn_backlog = 1024
net.ipv4.tcp_keepalive_time = 600
net.ipv4.tcp_keepalive_intvl = 30
net.ipv4.tcp_keepalive_probes = 3

# 应用配置
sudo sysctl -p
```

## 安全建议

1. **网络安全**
   - 使用防火墙限制访问IP范围
   - 定期更新系统补丁
   - 监控异常连接

2. **服务安全**
   - 使用专用用户运行服务
   - 限制文件权限
   - 定期备份配置文件

3. **监控告警**
   - 设置服务状态监控
   - 配置异常情况告警
   - 定期检查日志

## 更新升级

### 更新服务

```bash
# 1. 在MacBook上构建新版本
mvn clean package -DskipTests

# 2. 停止服务
sudo systemctl stop rtk-data-relay

# 3. 备份当前版本
sudo cp /opt/rtk-data-relay/rtk-data-relay-1.0.0.jar /opt/rtk-data-relay/rtk-data-relay-1.0.0.jar.bak

# 4. 上传新版本
scp target/rtk-data-relay-1.0.0.jar ubuntu@<服务器IP>:/tmp/
sudo cp /tmp/rtk-data-relay-1.0.0.jar /opt/rtk-data-relay/
sudo chown rtk:rtk /opt/rtk-data-relay/rtk-data-relay-1.0.0.jar

# 5. 启动服务
sudo systemctl start rtk-data-relay

# 6. 验证更新
curl http://localhost:8080/api/monitor/status
```

## 联系支持

如果在部署过程中遇到问题，请：
1. 查看相关日志文件
2. 检查网络连接和防火墙配置
3. 确认Java环境正确安装
4. 联系技术支持团队
