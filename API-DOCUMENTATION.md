# RTK数据转发服务 RESTful API 文档

## 🚀 概述

RTK数据转发服务提供标准的RESTful API，用于监控和统计RTK差分数据转发系统的运行状态。所有API接口均采用JSON格式响应，支持跨域访问，适合其他Web服务集成调用。

**基础URL**: `http://your-server:8899/api/v1`

**API版本**: v1.0.0

## 📋 统一响应格式

所有API接口采用统一的响应格式：

```json
{
  "code": 200,
  "message": "Success",
  "data": {},
  "timestamp": "2025-09-04T10:00:00",
  "path": "/api/v1/system/status"
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | HTTP状态码：200=成功，400=客户端错误，500=服务器错误 |
| message | String | 响应消息描述 |
| data | Object | 具体的响应数据 |
| timestamp | String | 响应时间戳（ISO 8601格式） |
| path | String | 请求路径（可选） |

## 🔍 API接口列表

### 1. 系统状态相关

#### 1.1 健康检查
**GET** `/health`

检查服务是否正常运行。

**响应示例**：
```json
{
  "code": 200,
  "message": "服务运行正常",
  "data": {
    "status": "UP",
    "timestamp": "2025-09-04T10:00:00",
    "service": "RTK Data Relay Service",
    "version": "1.0.0",
    "serverRunning": true
  }
}
```

#### 1.2 系统状态概览
**GET** `/system/status`

获取系统运行状态、连接统计、性能指标等综合信息。

**响应数据结构**：
```json
{
  "serviceRunning": true,
  "startTime": "2025-09-04T08:00:00",
  "lastUpdateTime": "2025-09-04T10:00:00",
  "currentBaseStationConnections": 3,
  "currentMobileStationConnections": 15,
  "totalBaseStationConnections": 10,
  "totalMobileStationConnections": 45,
  "totalReceivedBytes": 1048576,
  "totalSentBytes": 15728640,
  "totalReceivedMessages": 1000,
  "totalSentMessages": 15000,
  "connectionErrors": 0,
  "relayErrors": 0,
  "performance": {
    "threadPoolStatus": "ThreadPool[Active: 2, Pool: 5, Queue: 0, Completed: 1000]",
    "dataBufferStatus": "Buffer[Size: 50, Memory: 1.2MB, Age: 2min]",
    "memoryUsage": {
      "usedMemory": 134217728,
      "maxMemory": 1073741824,
      "usagePercent": 12.5
    }
  },
  "database": {
    "enabled": true,
    "connectionStatus": "CONNECTED"
  }
}
```

#### 1.3 系统性能监控
**GET** `/system/performance`

获取详细的系统性能指标。

**请求参数**：
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| days | Integer | 否 | 7 | 统计天数 |

**响应数据包含**：
- 连接性能指标
- 数据传输吞吐量
- 错误率分析
- 系统资源使用情况
- 数据库存储效率（如果启用）

### 2. 基站相关

#### 2.1 获取基站列表
**GET** `/base-stations`

获取所有基站的连接状态和统计信息。

**请求参数**：
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| days | Integer | 否 | 7 | 统计天数 |
| includeQuality | Boolean | 否 | true | 是否包含数据质量信息 |

**响应示例**：
```json
{
  "code": 200,
  "message": "基站信息获取成功",
  "data": [
    {
      "baseStationId": "BS_192.168.1.100_12345",
      "remoteAddress": "192.168.1.100",
      "connectTime": "2025-09-04T08:30:00",
      "lastActiveTime": "2025-09-04T09:59:55",
      "lastDataTime": "2025-09-04T09:59:55",
      "hourlyDataCount": 3600,
      "receivedBytes": 524288,
      "receivedMessages": 500,
      "status": "CONNECTED",
      "inactiveSeconds": 5,
      "rtcmMessageTypes": "RTCM3",
      "dataQuality": {
        "integrityPercent": 99.8,
        "avgDataSize": 1048.5,
        "updateFrequency": 1.0,
        "qualityRating": "EXCELLENT"
      }
    }
  ]
}
```

#### 2.2 获取指定基站详情
**GET** `/base-stations/{baseStationId}`

获取指定基站的详细信息。

**路径参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| baseStationId | String | 是 | 基站ID |

**请求参数**：
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| days | Integer | 否 | 7 | 统计天数 |

### 3. 移动站相关

#### 3.1 获取移动站列表
**GET** `/mobile-stations`

获取当前连接的移动站列表和状态。

**响应示例**：
```json
{
  "code": 200,
  "message": "移动站信息获取成功",
  "data": [
    {
      "mobileStationId": "MS_192.168.1.201_54321",
      "remoteAddress": "192.168.1.201",
      "connectTime": "2025-09-04T09:15:00",
      "lastActiveTime": "2025-09-04T09:59:50",
      "receivedBytes": 1048576,
      "receivedMessages": 1000,
      "status": "CONNECTED",
      "inactiveSeconds": 10
    }
  ]
}
```

### 4. 转发性能相关

#### 4.1 获取转发性能统计
**GET** `/relay/performance`

获取数据转发的性能指标、成功率、吞吐量等信息。

**请求参数**：
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| hours | Integer | 否 | 24 | 统计小时数 |
| includeDetails | Boolean | 否 | true | 是否包含详细统计 |

**响应数据结构**：
```json
{
  "timeRangeHours": 24,
  "startTime": "2025-09-03T10:00:00",
  "endTime": "2025-09-04T10:00:00",
  "totalRelayAttempts": 50000,
  "totalSuccess": 49950,
  "totalFailed": 50,
  "overallSuccessRate": 99.9,
  "totalSuccessBytes": 52428800,
  "activeBaseStations": 3,
  "activeMobileStations": 15,
  "avgSuccessDataSize": 1048.5,
  "efficiency": {
    "messagesPerSecond": 0.58,
    "bytesPerSecond": 608.0,
    "avgRelayLatency": 2.5,
    "systemLoadPercent": 15.2
  },
  "hourlyStats": [
    {
      "hourSlot": "2025-09-04 09:00:00",
      "totalRelays": 2100,
      "successRelays": 2098,
      "successBytes": 2199552,
      "successRate": 99.9
    }
  ],
  "baseStationStats": [
    {
      "baseStationId": "BS_192.168.1.100_12345",
      "relayCount": 20000,
      "successCount": 19980,
      "successBytes": 20971520,
      "avgDataSize": 1048.5,
      "lastRelayTime": "2025-09-04T09:59:55",
      "targetMobileStations": 15
    }
  ]
}
```

### 5. 数据库相关

#### 5.1 获取数据库状态
**GET** `/database/status`

获取数据库连接状态、存储统计等信息。

**请求参数**：
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| days | Integer | 否 | 7 | 统计天数 |

**响应示例**：
```json
{
  "code": 200,
  "message": "数据库状态获取成功",
  "data": {
    "enabled": true,
    "connectionStatus": "CONNECTED",
    "storageEfficiency": {
      "optimized_records": 168,
      "total_original_records": 604800,
      "storage_efficiency_percent": 99.97,
      "total_data_size_bytes": 629145600,
      "avg_data_per_hour": 3600,
      "active_base_stations": 3
    },
    "baseStationStatus": [
      {
        "base_station_id": "BS_192.168.1.100_12345",
        "remote_address": "192.168.1.100",
        "last_data_time": "2025-09-04T09:59:55",
        "hourly_data_count": 3600,
        "data_size": 3774873,
        "rtcm_message_types": "RTCM3",
        "inactive_seconds": 5
      }
    ],
    "timestamp": "2025-09-04T10:00:00"
  }
}
```

### 6. 兼容性接口

#### 6.1 获取原始统计数据（已弃用）
**GET** `/statistics`

返回原始的RelayStatistics对象，保持向后兼容。

⚠️ **注意**: 此接口已标记为弃用，建议使用 `/system/status` 替代。

#### 6.2 Ping接口
**GET** `/ping`

简单的服务可用性检查。

## 🔧 使用示例

### cURL示例

```bash
# 健康检查
curl -X GET "http://localhost:8899/api/v1/health"

# 获取系统状态
curl -X GET "http://localhost:8899/api/v1/system/status"

# 获取基站列表
curl -X GET "http://localhost:8899/api/v1/base-stations?days=7&includeQuality=true"

# 获取指定基站详情
curl -X GET "http://localhost:8899/api/v1/base-stations/BS_192.168.1.100_12345"

# 获取转发性能统计
curl -X GET "http://localhost:8899/api/v1/relay/performance?hours=24&includeDetails=true"

# 获取数据库状态
curl -X GET "http://localhost:8899/api/v1/database/status?days=7"
```

### JavaScript示例

```javascript
// 使用fetch API
async function getSystemStatus() {
  try {
    const response = await fetch('http://localhost:8899/api/v1/system/status');
    const result = await response.json();
    
    if (result.code === 200) {
      console.log('系统状态:', result.data);
    } else {
      console.error('获取失败:', result.message);
    }
  } catch (error) {
    console.error('请求错误:', error);
  }
}

// 获取基站列表
async function getBaseStations() {
  const response = await fetch('http://localhost:8899/api/v1/base-stations?days=7');
  const result = await response.json();
  return result.data;
}
```

### Python示例

```python
import requests
import json

# 基础配置
BASE_URL = "http://localhost:8899/api/v1"

def get_system_status():
    """获取系统状态"""
    response = requests.get(f"{BASE_URL}/system/status")
    result = response.json()
    
    if result['code'] == 200:
        return result['data']
    else:
        raise Exception(f"API错误: {result['message']}")

def get_base_stations(days=7, include_quality=True):
    """获取基站列表"""
    params = {
        'days': days,
        'includeQuality': include_quality
    }
    response = requests.get(f"{BASE_URL}/base-stations", params=params)
    return response.json()['data']

def get_relay_performance(hours=24):
    """获取转发性能统计"""
    params = {'hours': hours, 'includeDetails': True}
    response = requests.get(f"{BASE_URL}/relay/performance", params=params)
    return response.json()['data']

# 使用示例
if __name__ == "__main__":
    # 获取系统状态
    status = get_system_status()
    print(f"当前基站连接数: {status['currentBaseStationConnections']}")
    print(f"当前移动站连接数: {status['currentMobileStationConnections']}")
    
    # 获取基站列表
    base_stations = get_base_stations()
    for bs in base_stations:
        print(f"基站 {bs['baseStationId']}: {bs['status']}")
```

## 📊 数据模型

### SystemStatusDTO
系统状态数据传输对象，包含服务运行状态、连接统计、性能指标等。

### BaseStationDTO
基站信息数据传输对象，包含连接信息、数据统计、质量指标等。

### RelayPerformanceDTO
转发性能统计数据传输对象，包含成功率、吞吐量、效率指标等。

## ⚠️ 注意事项

1. **跨域支持**: API已配置CORS支持，允许跨域访问
2. **数据库依赖**: 部分接口需要数据库功能启用才能返回完整数据
3. **时间格式**: 所有时间字段均采用ISO 8601格式
4. **分页支持**: 当前版本暂不支持分页，未来版本会添加
5. **缓存策略**: 建议客户端实现适当的缓存策略，避免频繁请求

## 🔄 版本更新

- **v1.0.0**: 初始版本，提供基础监控和统计接口
- 向后兼容承诺：v1.x版本保持API兼容性
- 新功能将通过版本号递增发布

## 📞 技术支持

如有API使用问题或功能建议，请联系RTK团队或提交GitHub Issue。
