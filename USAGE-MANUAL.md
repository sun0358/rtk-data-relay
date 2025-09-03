# RTK数据转发服务使用手册

## 🎯 服务启动和验证

### 1. 启动服务

```bash
# 启动RTK数据转发服务
sudo systemctl start rtk-data-relay

# 检查服务状态
sudo systemctl status rtk-data-relay

# 查看启动日志
sudo journalctl -u rtk-data-relay --since "5 minutes ago"
```

### 2. 验证服务正常运行

```bash
# 检查端口监听
sudo netstat -tlnp | grep -E ':(8080|9001|9002)'
# 应该看到三个端口都在监听状态

# 测试Web API
curl http://localhost:8080/api/monitor/status
# 应该返回JSON格式的状态信息

# 测试健康检查
curl http://localhost:8080/actuator/health
# 应该返回 {"status":"UP"}
```

## 🔌 设备连接配置

### 基站连接配置

1. **网络设置**
   - 连接类型: TCP客户端
   - 服务器IP: frp服务器公网IP
   - 服务器端口: 19001 (映射到内网9001)
   - 自动重连: 启用
   - 重连间隔: 5秒

2. **数据格式设置**
   - 输出格式: RTCM 3.x
   - 数据频率: 1Hz (根据需要调整)
   - 消息类型: 包含位置、观测值、星历等

3. **连接验证**
   ```bash
   # 查看基站连接状态
   curl http://localhost:8080/api/monitor/connections/base-stations
   ```

### 移动站连接配置

1. **差分数据源设置**
   - 数据源: 网络(TCP)
   - 服务器IP: frp服务器公网IP
   - 服务器端口: 19002 (映射到内网9002)
   - 自动重连: 启用
   - 超时时间: 30秒

2. **连接验证**
   ```bash
   # 查看移动站连接状态
   curl http://localhost:8080/api/monitor/connections/mobile-stations
   ```

## 📊 监控和管理

### 1. 实时状态监控

```bash
# 查看服务整体状态
curl http://localhost:8080/api/monitor/status | jq

# 查看所有连接信息
curl http://localhost:8080/api/monitor/connections | jq

# 查看详细统计信息
curl http://localhost:8080/api/monitor/statistics | jq
```

### 2. 日志监控

```bash
# 实时查看应用日志
tail -f /opt/rtk-data-relay/logs/rtk-relay.log

# 实时查看系统日志
sudo journalctl -u rtk-data-relay -f

# 查看错误日志
sudo journalctl -u rtk-data-relay | grep -E "(ERROR|WARN)"

# 查看连接日志
sudo journalctl -u rtk-data-relay | grep -E "(连接建立|连接断开)"
```

### 3. 性能监控

```bash
# 查看进程资源使用
top -p $(pgrep -f rtk-data-relay)

# 查看网络连接数
sudo ss -tln | grep -E ':(9001|9002)' | wc -l

# 查看数据传输统计
curl -s http://localhost:8080/api/monitor/statistics | \
jq '{receivedMB: (.totalReceivedBytes/1024/1024), sentMB: (.totalSentBytes/1024/1024)}'
```

## 🔧 日常维护操作

### 1. 定期检查项目

**每日检查 (建议设置cron任务):**
```bash
#!/bin/bash
# daily_check.sh

echo "=== RTK服务日常检查 $(date) ==="

# 检查服务状态
if systemctl is-active --quiet rtk-data-relay; then
    echo "✅ 服务状态: 正常运行"
else
    echo "❌ 服务状态: 异常"
    sudo systemctl restart rtk-data-relay
fi

# 检查连接数
CONNECTIONS=$(curl -s http://localhost:8080/api/monitor/status | jq -r '.activeMobileStations')
echo "📱 当前移动站连接数: $CONNECTIONS"

# 检查错误日志
ERROR_COUNT=$(sudo journalctl -u rtk-data-relay --since "24 hours ago" | grep -c ERROR || true)
echo "🚨 24小时内错误数: $ERROR_COUNT"

# 检查磁盘空间
DISK_USAGE=$(df -h /opt/rtk-data-relay | tail -1 | awk '{print $5}')
echo "💾 磁盘使用率: $DISK_USAGE"

echo "=== 检查完成 ==="
```

**设置每日自动检查:**
```bash
# 创建检查脚本
sudo vim /opt/rtk-data-relay/daily_check.sh
# 复制上面的脚本内容

# 设置执行权限
sudo chmod +x /opt/rtk-data-relay/daily_check.sh

# 添加到crontab
sudo crontab -e
# 添加: 0 9 * * * /opt/rtk-data-relay/daily_check.sh >> /opt/rtk-data-relay/logs/daily_check.log 2>&1
```

### 2. 日志清理

```bash
# 清理超过30天的日志
find /opt/rtk-data-relay/logs -name "*.log.*" -mtime +30 -delete

# 压缩大于100MB的日志文件
find /opt/rtk-data-relay/logs -name "*.log" -size +100M -exec gzip {} \;
```

### 3. 配置备份

```bash
# 创建配置备份
sudo cp /opt/rtk-data-relay/config/application.yml \
        /opt/rtk-data-relay/config/application.yml.bak.$(date +%Y%m%d)

# 备份系统服务文件
sudo cp /etc/systemd/system/rtk-data-relay.service \
        /opt/rtk-data-relay/rtk-data-relay.service.bak.$(date +%Y%m%d)
```

## 🎛️ 高级配置

### 1. 自定义端口配置

如需修改默认端口，编辑配置文件：

```bash
sudo vim /opt/rtk-data-relay/config/application.yml
```

修改以下配置：
```yaml
rtk:
  server1:
    port: 9001  # 基站接入端口
  server2:
    port: 9002  # 移动站接入端口
```

修改后重启服务：
```bash
sudo systemctl restart rtk-data-relay
```

### 2. 连接数限制调整

```yaml
rtk:
  server2:
    max-connections: 10  # 最大移动站连接数，可根据需要调整
```

### 3. 超时时间调整

```yaml
rtk:
  server1:
    timeout: 30             # 基站连接超时时间（秒）
    heartbeat-interval: 10  # 心跳检测间隔（秒）
  server2:
    timeout: 30             # 移动站连接超时时间（秒）
    heartbeat-interval: 10  # 心跳检测间隔（秒）
```

## 🚨 应急处理流程

### 1. 服务异常停止

```bash
# 立即重启服务
sudo systemctl restart rtk-data-relay

# 查看重启后状态
sudo systemctl status rtk-data-relay

# 如果重启失败，查看错误日志
sudo journalctl -u rtk-data-relay --since "5 minutes ago"
```

### 2. 端口冲突

```bash
# 查找占用端口的进程
sudo netstat -tlnp | grep -E ':(9001|9002)'

# 如果有其他进程占用，可以终止或修改配置
sudo kill -9 <PID>

# 或者修改服务端口配置
sudo vim /opt/rtk-data-relay/config/application.yml
```

### 3. 内存不足

```bash
# 查看内存使用情况
free -h

# 临时释放内存
sudo sync && echo 3 | sudo tee /proc/sys/vm/drop_caches

# 调整JVM内存参数（长期解决方案）
sudo vim /etc/systemd/system/rtk-data-relay.service
```

## 📱 移动端监控

可以通过手机浏览器访问监控接口：
- 服务状态: `http://<frp公网IP>:18080/api/monitor/status`
- 连接信息: `http://<frp公网IP>:18080/api/monitor/connections`

## 🎓 最佳实践

1. **定期备份**: 每周备份配置文件和重要日志
2. **监控告警**: 设置服务异常时的邮件或短信告警
3. **性能基线**: 记录正常运行时的性能指标作为基线
4. **文档更新**: 记录所有配置变更和问题解决过程
5. **测试验证**: 定期测试故障恢复和重启流程

---

**支持信息:**
- 服务版本: 1.0.0
- 技术栈: Java 8 + Spring Boot + Netty
- 兼容系统: Ubuntu 24.04 LTS
- 最大连接数: 1个基站 + 10个移动站
