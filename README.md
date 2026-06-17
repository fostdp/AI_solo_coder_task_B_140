# 古代沙船（方艄）稳性仿真与装载优化系统

Ancient Sand Ship (Fang Shao) Stability Simulation & Loading Optimization System

## 架构图

```
                          ┌──────────────────────────────────────────┐
                          │           Browser / Mobile               │
                          │   Vue3 + Three.js + Chart.js             │
                          │   junk_ship_3d.js │ stability_panel.js   │
                          └──────┬──────────────────┬────────────────┘
                                 │ HTTP/WS           │
                          ┌──────▼──────────────────▼────────────────┐
                          │         Nginx (Gzip + Reverse Proxy)     │
                          │   /           → frontend static files     │
                          │   /api/*      → backend:8080              │
                          │   /ws/*       → backend WebSocket         │
                          └──────┬──────────────────────────────────┘
                                 │
    ┌────────────────────────────▼─────────────────────────────────────────┐
    │                    SpringBoot Backend (:8080)                         │
    │                                                                      │
    │  ┌─────────────┐  Event  ┌──────────────────┐  Event  ┌───────────┐ │
    │  │mqtt_receiver│────────►│stability_simulator│────────►│ alarm_ws  │ │
    │  │ ·MQTT Inbound│        │ ·KG/KM/GM/GZ/MR  │        │ ·Evaluate │ │
    │  │ ·Validation  │        │ ·FSC correction   │        │ ·WebSocket│ │
    │  │ ·Parse+Save  │        │ ·GZ curve         │        │ ·Push     │ │
    │  └──────┬───────┘        └──────────────────┘        └─────┬─────┘ │
    │         │                      ▲                           │        │
    │         │                      │ Event                     │        │
    │  ┌──────▼──────────────────────┴───────────────────────────┘        │
    │  │  loading_optimizer                                                │
    │  │  ·OR-Tools MIP  ·Greedy Heuristic  ·Local Search                 │
    │  └──────────────────────────────────────────────────────────────────┘
    │         │                                                             │
    │  ┌──────▼──────┐  ┌──────────────┐  ┌─────────────────────┐         │
    │  │ Actuator    │  │ Prometheus   │  │ Ship/Cargo YAML     │         │
    │  │ /health     │  │ /prometheus  │  │ ship-params.yml     │         │
    │  │ /metrics    │  │ Registry     │  │ cargo-params.yml    │         │
    │  └─────────────┘  └──────┬───────┘  └─────────────────────┘         │
    └──────────────────────────┼──────────────────────────────────────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
    ┌────▼─────┐        ┌─────▼──────┐        ┌─────▼──────┐
    │PostgreSQL│        │Mosquitto   │        │ Simulator  │
    │  :5432   │        │  MQTT      │        │  Python    │
    │ +Indexes │        │  :1883     │        │  CLI       │
    └──────────┘        └────────────┘        └────────────┘
                                                      │
                                         ┌────────────▼───────────┐
                                         │ 5 Cargo Presets        │
                                         │ 5 Sea Condition Levels │
                                         └────────────────────────┘
```

## 模块说明

| 模块 | 包路径 | 职责 |
|------|--------|------|
| mqtt_receiver | `com.sandship.stability.mqtt_receiver` | MQTT数据接收、物理范围校验、嵌套JSON兼容解析 |
| stability_simulator | `com.sandship.stability.stability_simulator` | 静力学计算：KG/KM/GM/GZ/MR/T，自由液面修正，GZ曲线 |
| loading_optimizer | `com.sandship.stability.loading_optimizer` | OR-Tools整数规划 + 贪心启发式 + 局部搜索两阶段策略 |
| alarm_ws | `com.sandship.stability.alarm_ws` | GM/横摇/舱底水告警评估，WebSocket按船推送 |

**事件解耦**：

```
SensorDataReceivedEvent → StabilitySimulator → StabilityCalculatedEvent
                                                       ↓
                                                 AlarmEvaluator
                                                 AlarmWebSocket → Browser
```

## 快速部署

### 前提

- Docker 20.10+
- Docker Compose v2+

### 一键启动

```bash
git clone <repo-url>
cd AI_solo_coder_task_A_140

# 启动全部服务
docker compose up -d --build

# 查看状态
docker compose ps

# 查看日志
docker compose logs -f backend
```

服务启动顺序：PostgreSQL → Mosquitto → Backend → Frontend → Simulator → Prometheus

### 访问地址

| 服务 | 地址 |
|------|------|
| 前端界面 | http://localhost |
| 后端API | http://localhost:8080/api |
| Swagger文档 | http://localhost:8080/api/swagger-ui.html |
| Actuator健康 | http://localhost:8080/api/actuator/health |
| Prometheus指标 | http://localhost:8080/api/actuator/prometheus |
| Prometheus面板 | http://localhost:9090 |
| MQTT Broker | localhost:1883 |

### 环境变量

```bash
# .env 文件（可选）
DB_PASSWORD=your_secure_password
DB_PORT=5432
MQTT_PORT=1883
FRONTEND_PORT=80
PROMETHEUS_PORT=9090
SIM_INTERVAL=60
CARGO_PRESET=grain_salt
SEA_CONDITION=moderate
LOG_LEVEL=INFO
```

### 停止服务

```bash
docker compose down
# 保留数据卷
docker compose down -v  # 删除数据卷
```

## 模拟器用法

模拟器支持5种货物预设和5种海况等级，可通过命令行参数或环境变量配置。

### 货物预设

| 预设名 | 说明 | 货物组合 |
|--------|------|----------|
| `grain_salt` | 粮盐混装（默认） | 粮/盐/粮/盐 |
| `tea_porcelain` | 茶瓷贸易 | 茶/瓷/茶/瓷 |
| `silk_salt` | 丝盐贸易 | 丝/盐/丝/盐 |
| `full_grain` | 满载粮食 | 粮/粮/粮/粮 |
| `mixed` | 五货混装 | 粮/盐/茶/瓷/丝 |

### 海况等级

| 等级 | 说明 | 风速范围 | 浪高范围 | 极端横摇概率 |
|------|------|----------|----------|-------------|
| `calm` | 平静海面 | 1-5 m/s | 0.1-0.5 m | 0.1% |
| `moderate` | 中等海况（默认） | 5-15 m/s | 0.5-2.0 m | 0.5% |
| `rough` | 恶劣海况 | 15-30 m/s | 2.0-5.0 m | 2% |
| `storm` | 暴风骤雨 | 30-50 m/s | 5.0-10.0 m | 5% |
| `typhoon` | 台风过境 | 50-80 m/s | 8.0-15.0 m | 10% |

### 命令行用法

```bash
# Docker内使用（通过docker-compose）
CARGO_PRESET=tea_porcelain SEA_CONDITION=rough docker compose up -d simulator

# 本地Python直接运行
pip install paho-mqtt
python simulator/sand_ship_simulator.py --broker localhost --interval 30

# 茶瓷贸易 + 暴风海况
python simulator/sand_ship_simulator.py --cargo-preset tea_porcelain --sea-condition storm

# 5艘船 + 台风海况
python simulator/sand_ship_simulator.py --ships 5 --sea-condition typhoon

# 查看帮助
python simulator/sand_ship_simulator.py --help
```

### 环境变量配置（Docker）

```bash
MQTT_BROKER=mqtt          # MQTT地址
MQTT_PORT=1883            # MQTT端口
REPORT_INTERVAL=60        # 上报间隔(秒)
CARGO_PRESET=grain_salt   # 货物预设
SEA_CONDITION=moderate    # 海况等级
```

## 监控

### Actuator端点

| 端点 | 说明 |
|------|------|
| `GET /api/actuator/health` | 健康检查（DB + 磁盘） |
| `GET /api/actuator/prometheus` | Prometheus格式指标 |
| `GET /api/actuator/metrics` | Micrometer指标列表 |
| `GET /api/actuator/env` | 环境变量（脱敏） |
| `GET /api/actuator/loggers` | 日志级别管理 |

### Prometheus

访问 `http://localhost:9090`，可查询：

- `http_server_requests_seconds_bucket` — API延迟分布
- `jvm_memory_used_bytes` — JVM内存使用
- `hikaricp_connections_active` — 数据库连接池
- `system_cpu_usage` — CPU使用率

### Docker健康检查

所有服务均配置了`healthcheck`：

```bash
docker compose ps  # 查看healthy状态
```

## PostgreSQL索引

系统在`init.sql`中预建了以下性能索引：

| 索引 | 表 | 类型 | 用途 |
|------|-----|------|------|
| `idx_sensor_data_time_range` | sensor_data | B-Tree(ship_id, timestamp DESC) | 时间范围查询 |
| `idx_sensor_data_roll` | sensor_data | Partial(roll_angle) | 大横摇角过滤 |
| `idx_stability_status` | stability_results | Partial(status) | 异常状态过滤 |
| `idx_stability_gm` | stability_results | Partial(gm<0.3) | 低GM告警查询 |
| `idx_alarms_severity_ack` | alarms | Partial(ack=false) | 未确认告警 |
| `idx_alarms_triggered` | alarms | B-Tree(triggered_at DESC) | 告警时间线 |
| `idx_cargo_loadings_ship_opt` | cargo_loadings | B-Tree(ship_id, optimized) | 优化装载查询 |
| `idx_cargo_holds_ship_tank` | cargo_holds | Partial(is_tank=true) | 液舱FSC计算 |

## 前端Gzip

Nginx配置了对以下类型的Gzip压缩：

- `text/*`（html/css/xml/js）
- `application/json` / `application/javascript`
- `application/xml`
- `image/svg+xml`
- `font/*`

压缩等级6，最小压缩阈值1KB，静态资源缓存30天。

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + Vite + Three.js + Chart.js + Element Plus |
| 后端 | Spring Boot 3.2 + JPA + Spring Integration MQTT + WebSocket |
| 优化 | Google OR-Tools CBC + 贪心启发式 |
| 数据库 | PostgreSQL 16 |
| 消息 | Eclipse Mosquitto MQTT 5.0 |
| 监控 | Spring Actuator + Micrometer + Prometheus |
| 容器 | Docker多阶段构建 + Docker Compose |
| 代理 | Nginx (Gzip + 反向代理 + WebSocket升级) |
