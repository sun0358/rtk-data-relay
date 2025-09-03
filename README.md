# RTK数据转发服务

## 📋 项目简介

RTK数据转发服务是一个专为RTK差分定位系统设计的高性能TCP数据转发系统。该服务实现了基站到移动站的实时数据转发，具备企业级的稳定性和可靠性。

### 🎯 核心功能
- **双端口TCP服务**：
  - **Server1 (端口9003)**：接收基站的RTK差分修正数据
  - **Server2 (端口9002)**：将数据转发给移动站
- **Web监控API (端口8899)**：实时监控和管理接口

## ✨ 功能特性

- ✅ **高性能数据转发**：基于Netty框架，支持高并发连接，零延迟数据透传
- ✅ **智能连接管理**：支持1个基站 + 最多10个移动站同时连接
- ✅ **自动故障恢复**：连接断开自动重连，异常自动恢复，定时健康检查
- ✅ **实时监控统计**：Web API监控接口，实时查看连接状态和数据传输统计
- ✅ **心跳保活机制**：25秒间隔心跳包，保持长连接稳定（适配frp环境）
- ✅ **系统服务集成**：systemd服务管理，开机自启动，进程守护
- ✅ **完善日志系统**：详细的运行日志，支持日志轮转和分级记录
- ✅ **一键部署**：自动化部署脚本，支持快速安装和更新

## 🏗️ 技术架构

```
基站设备 → frp(19001) → Server1(9003) → 数据转发服务 → Server2(9002) → frp(19002) → 移动站1
                                                                                    ├─→ 移动站2  
                                                                                    ├─→ 移动站3
                                                                                    └─→ ...
                    Web监控 ← frp(18080) ← Web API(8899) ←
```

### 🛠️ 技术栈
- **Java 8**: 核心开发语言，兼容性考虑
- **Spring Boot 2.7.18**: 稳定的企业级应用框架
- **Netty 4.1.104**: 高性能异步网络通信框架
- **Maven 3.6+**: 项目构建和依赖管理
- **Systemd**: Linux系统服务管理
- **Hutool**: 实用工具库
- **Lombok**: 简化代码编写

## 🚀 快速开始

### 1. 环境要求

**MacBook开发环境：**
- Java 8+ (推荐OpenJDK 8/11/17)
- Maven 3.6+
- IntelliJ IDEA (推荐)

**Ubuntu部署环境：**
- Ubuntu 24.04 LTS
- Java 8+ (OpenJDK)
- 网络端口：8899, 9003, 9002
- frp内网穿透工具（可选）

### 2. 快速本地测试

使用快速启动脚本进行本地测试：

```bash
# 进入项目目录
cd ~/company/prj/Java/rtk-data-relay

# 一键启动（自动构建并运行）
./quick-start.sh
```

启动成功后，服务将运行在：
- **Web API**: http://localhost:8899
- **基站接入**: localhost:9003
- **移动站接入**: localhost:9002

### 3. 验证服务运行

```bash
# 检查服务状态
curl http://localhost:8899/api/monitor/status

# 检查健康状态
curl http://localhost:8899/actuator/health

# 查看连接信息
curl http://localhost:8899/api/monitor/connections
```

### 4. 生产环境部署

**🎯 推荐方式：一键自动部署**

```bash
# 在项目根目录执行
./deploy/build-and-deploy.sh <Ubuntu服务器IP> <用户名> [SSH私钥路径]

# 示例
./deploy/build-and-deploy.sh 192.168.5.15 sun
./deploy/build-and-deploy.sh 192.168.5.15 sun ~/.ssh/id_rsa
```

**📋 手动部署步骤：**

```bash
# 1. 构建项目
mvn clean package -DskipTests

# 2. 上传文件到Ubuntu服务器  
scp target/rtk-data-relay-*.jar ubuntu@<服务器IP>:~/
scp deploy/install.sh ubuntu@<服务器IP>:~/
scp src/main/resources/application.yml ubuntu@<服务器IP>:~/

# 3. 在Ubuntu服务器上安装
ssh ubuntu@<服务器IP>
chmod +x install.sh
sudo ./install.sh rtk-data-relay-*.jar
```

## ⚙️ 配置说明

### 应用配置文件 (application.yml)

```yaml
# Web服务端口
server:
  port: 8899

# RTK服务配置
rtk:
  server1:                  # 基站接入服务
    port: 9003              # 基站接入端口（注意：实际配置是9003）
    timeout: 30             # 连接超时时间（秒）
    heartbeat-interval: 10  # 心跳检测间隔（秒）
    
  server2:                  # 移动站接入服务
    port: 9002              # 移动站接入端口
    max-connections: 10     # 最大移动站连接数
    timeout: 30             # 连接超时时间（秒）
    heartbeat-interval: 10  # 心跳检测间隔（秒）
    
  relay:                    # 数据转发配置
    buffer-size: 8192                    # 数据缓冲区大小（字节）
    statistics-retention-hours: 24       # 统计数据保留时间（小时）
    reconnect-interval: 5                # 自动重连间隔（秒）
    max-reconnect-attempts: 10           # 最大重连次数
```

### frp端口映射配置

在frpc配置文件中添加以下映射：

```ini
# RTK服务Web API端口映射
[rtk-web-api]
type = tcp
local_ip = 127.0.0.1
local_port = 8899
remote_port = 18080

# RTK基站接入端口映射
[rtk-base-station]
type = tcp
local_ip = 127.0.0.1
local_port = 9003
remote_port = 19001

# RTK移动站接入端口映射  
[rtk-mobile-station]
type = tcp
local_ip = 127.0.0.1
local_port = 9002
remote_port = 19002
```

### 基站设备配置

基站设备需要配置以下参数：
- **服务器地址**: frp服务器公网IP
- **端口**: 19001 (通过frp映射到内网9003)
- **协议**: TCP客户端模式
- **数据格式**: RTCM 3.x差分修正数据
- **自动重连**: 启用
- **重连间隔**: 5秒

### 移动站设备配置

移动站设备需要配置以下参数：
- **服务器地址**: frp服务器公网IP
- **端口**: 19002 (通过frp映射到内网9002)
- **协议**: TCP客户端模式
- **自动重连**: 启用
- **超时时间**: 30秒

## 📊 监控和管理

### Web API接口

| 接口路径 | 方法 | 功能说明 | 示例响应 |
|---------|------|---------|---------|
| `/api/monitor/status` | GET | 获取服务整体状态 | 服务运行状态、连接数统计 |
| `/api/monitor/statistics` | GET | 获取详细统计信息 | 数据传输量、消息计数、错误统计 |
| `/api/monitor/connections` | GET | 获取所有连接信息 | 基站和移动站连接详情 |
| `/api/monitor/connections/base-stations` | GET | 获取基站连接信息 | 基站连接状态和统计 |
| `/api/monitor/connections/mobile-stations` | GET | 获取移动站连接信息 | 移动站连接状态和统计 |
| `/actuator/health` | GET | 系统健康检查 | Spring Boot健康状态 |
| `/api/monitor/ping` | GET | 服务可用性检测 | 简单的ping响应 |

### 监控访问示例

```bash
# 本地访问
curl http://localhost:8899/api/monitor/status

# 通过frp公网访问
curl http://<frp公网IP>:18080/api/monitor/status

# 获取JSON格式化输出
curl -s http://localhost:8899/api/monitor/statistics | jq
```

### 服务管理命令

```bash
# 服务状态管理
sudo systemctl status rtk-data-relay     # 查看服务状态
sudo systemctl start rtk-data-relay      # 启动服务
sudo systemctl stop rtk-data-relay       # 停止服务  
sudo systemctl restart rtk-data-relay    # 重启服务
sudo systemctl enable rtk-data-relay     # 设置开机自启
sudo systemctl disable rtk-data-relay    # 取消开机自启

# 日志查看
sudo journalctl -u rtk-data-relay -f                    # 实时日志
sudo journalctl -u rtk-data-relay --since "1 hour ago" # 最近1小时日志
sudo journalctl -u rtk-data-relay | grep ERROR         # 错误日志

# 应用日志文件
tail -f /opt/rtk-data-relay/logs/rtk-relay.log         # 实时应用日志
less /opt/rtk-data-relay/logs/rtk-relay.log            # 分页查看日志
```

### 性能监控

```bash
# 系统资源监控
top -p $(pgrep -f rtk-data-relay)        # CPU和内存使用
htop -p $(pgrep -f rtk-data-relay)       # 更友好的资源监控

# 网络连接监控  
sudo ss -tlnp | grep -E ':(8899|9003|9002)'  # 端口监听状态
sudo ss -tp | grep java                       # Java进程网络连接

# 服务状态监控脚本
watch -n 5 "curl -s http://localhost:8899/api/monitor/status | jq"
```

## 🔧 故障排除

### 常见问题及解决方案

#### 1. 服务启动失败

```bash
# 查看详细错误信息
sudo journalctl -u rtk-data-relay --since "5 minutes ago" -n 50

# 检查Java环境
java -version

# 检查JAR文件完整性
ls -la /opt/rtk-data-relay/rtk-data-relay-*.jar

# 检查配置文件语法
sudo vim /opt/rtk-data-relay/config/application.yml

# 检查文件权限
sudo chown -R rtk:rtk /opt/rtk-data-relay/
```

#### 2. 端口被占用

```bash
# 检查端口占用情况
sudo ss -tlnp | grep -E ':(8899|9003|9002)'

# 查找占用进程
sudo lsof -i :8899
sudo lsof -i :9003  
sudo lsof -i :9002

# 修改配置文件端口（如需要）
sudo vim /opt/rtk-data-relay/config/application.yml
sudo systemctl restart rtk-data-relay
```

#### 3. 基站/移动站连接问题

```bash
# 测试端口连通性
nc -zv <服务器IP> 9003  # 测试基站端口
nc -zv <服务器IP> 9002  # 测试移动站端口

# 检查防火墙设置
sudo ufw status
sudo iptables -L -n

# 查看连接状态
curl -s http://localhost:8899/api/monitor/connections | jq

# 查看实时日志中的连接信息
sudo journalctl -u rtk-data-relay -f | grep -E "(连接建立|连接断开|Connection)"
```

#### 4. 数据转发异常

```bash
# 检查统计信息
curl -s http://localhost:8899/api/monitor/statistics | jq

# 查看转发错误日志
sudo journalctl -u rtk-data-relay | grep -E "(转发失败|relay.*fail)"

# 检查基站数据是否正常接收
sudo journalctl -u rtk-data-relay -f | grep "基站数据"
```

#### 5. 内存/性能问题

```bash
# 查看内存使用
free -h
ps aux | grep rtk-data-relay

# 调整JVM参数
sudo vim /etc/systemd/system/rtk-data-relay.service
# 修改 ExecStart 行：-Xms512m -Xmx2048m

# 重新加载配置并重启
sudo systemctl daemon-reload
sudo systemctl restart rtk-data-relay
```

### 性能调优建议

#### JVM参数优化

```bash
# 编辑systemd服务文件
sudo vim /etc/systemd/system/rtk-data-relay.service

# 推荐的JVM参数配置
ExecStart=/usr/bin/java -jar \
  -Xms512m -Xmx2048m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/opt/rtk-data-relay/logs/ \
  -Dspring.profiles.active=prod \
  /opt/rtk-data-relay/rtk-data-relay-*.jar
```

#### 系统网络参数优化

```bash
# 编辑系统参数
sudo vim /etc/sysctl.conf

# 添加网络优化参数
net.core.somaxconn = 1024
net.core.netdev_max_backlog = 5000
net.ipv4.tcp_max_syn_backlog = 2048
net.ipv4.tcp_keepalive_time = 600
net.ipv4.tcp_keepalive_probes = 3
net.ipv4.tcp_keepalive_intvl = 15

# 应用配置
sudo sysctl -p
```

## 🏗️ 项目结构

```
rtk-data-relay/
├── src/main/java/com/rtk/relay/
│   ├── config/                    # 配置类
│   │   ├── RtkDataBuffer.java     # 数据缓冲配置
│   │   ├── RtkProperties.java     # 属性配置
│   │   └── WebSecurityConfig.java # 安全配置
│   ├── controller/                # Web控制器
│   │   └── MonitorController.java # 监控接口控制器
│   ├── entity/                    # 实体类
│   │   ├── ConnectionInfo.java    # 连接信息实体
│   │   ├── ConnectionHistory.java # 连接历史实体
│   │   ├── RelayStatistics.java   # 转发统计实体
│   │   └── HourlyStatistics.java  # 小时统计实体
│   ├── netty/                     # Netty网络处理器
│   │   ├── BaseStationHandler.java    # 基站连接处理器
│   │   └── MobileStationHandler.java  # 移动站连接处理器
│   ├── service/                   # 业务服务层
│   │   ├── ConnectionManager.java     # 连接管理服务
│   │   ├── DataRelayService.java      # 数据转发服务
│   │   ├── TcpServerService.java      # TCP服务器服务
│   │   ├── HealthCheckService.java    # 健康检查服务
│   │   └── DataPersistenceService.java # 数据持久化服务
│   ├── util/                      # 工具类
│   │   └── ConnectionIdGenerator.java # 连接ID生成器
│   ├── exception/                 # 异常处理
│   │   └── RtkRelayException.java # 自定义异常
│   └── RtkDataRelayApplication.java # 应用启动类
├── deploy/                        # 部署相关文件
│   ├── build-and-deploy.sh        # 自动化部署脚本
│   ├── install.sh                 # 安装脚本
│   └── rtk-data-relay.service     # systemd服务配置
├── db/                           # 数据库相关（可选）
├── logs/                         # 日志文件目录
└── docs/                         # 项目文档
    ├── README.md                 # 项目说明
    ├── DEPLOYMENT-GUIDE.md       # 部署指南
    └── USAGE-MANUAL.md           # 使用手册
```

## 🔮 扩展开发

### 功能扩展建议

1. **数据处理增强**
   - RTCM消息解析和验证
   - 数据格式转换（RTCM ↔ NMEA）
   - 数据质量监控和告警

2. **监控和告警**
   - 添加Prometheus指标导出
   - 集成Grafana监控面板
   - 邮件/短信告警通知

3. **高可用性**
   - 主备热切换
   - 负载均衡支持
   - 集群部署方案

4. **安全性增强**
   - TLS加密传输
   - 客户端认证
   - 访问控制和审计

### 开发环境搭建

```bash
# 克隆项目
git clone <repository-url>
cd rtk-data-relay

# 导入到IntelliJ IDEA
# File → Open → 选择项目目录

# 配置JDK（Java 8+）
# File → Project Structure → Project → Project SDK

# 安装依赖并编译
mvn clean compile

# 运行测试
mvn test

# 本地启动
./quick-start.sh
```

## 📄 许可证

本项目为内部开发项目，版权所有。

## 🤝 技术支持

- **项目维护**: RTK开发团队
- **技术文档**: 详见 `docs/` 目录
- **问题反馈**: 通过内部技术支持渠道
- **紧急联系**: 7x24小时技术支持热线

---

**📝 最后更新**: 2024年9月  
**🔄 版本**: v1.0.0  
**✅ 状态**: 生产就绪
