# 舆情情感分析平台

基于 Spring Boot + Vue 3 的中文社交媒体舆情情感分析平台，支持微博、小红书等平台的数据采集、TextCNN 深度学习情感分析、DBSCAN 话题聚类、实时预警推送。

## 功能特性

- **多平台数据采集** — 通过 Playwright 爬取微博、小红书帖子数据
- **情感分析** — 基于 TextCNN 深度学习模型（DL4J），自动判断帖子正面/负面情感
- **话题聚类** — TF-IDF + DBSCAN 算法自动发现热点话题
- **趋势分析** — 按时间/地域维度展示情感走势与分布
- **实时推送** — WebSocket (STOMP) 实时推送新帖与告警
- **智能预警** — 可配置告警规则，支持企业微信/钉钉 Webhook 与邮件通知
- **数据总览** — ECharts 可视化仪表盘

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3、Element Plus、ECharts、Vue Router、Axios、SockJS + STOMP |
| 后端 | Spring Boot 2.7、MyBatis-Plus 3.5.5、WebSocket |
| 数据库 | MySQL 8.0、Redis |
| 深度学习 | DeepLearning4J 1.0.0-M2.1、ND4J (CUDA 11.6) |
| NLP | HanLP (中文分词) |
| 爬虫 | Playwright 1.44.0 |
| 通知 | OkHttp (Webhook)、Spring Mail |

## 项目结构

```
sentiment-platform/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/sentiment/
│   │   ├── collector/                # 数据采集（微博、小红书爬虫）
│   │   ├── analyzer/                 # 情感分析、关键词提取、话题聚类
│   │   ├── alert/                    # 告警引擎与通知服务
│   │   ├── config/                   # 配置类（Redis、MyBatis-Plus、WebSocket）
│   │   ├── controller/               # REST API 控制器
│   │   ├── entity/                   # 数据库实体类
│   │   ├── health/                   # 健康检查服务
│   │   ├── mapper/                   # MyBatis-Plus Mapper 接口
│   │   ├── service/                  # 业务逻辑层（目前暂空，控制器直接调用 Mapper）
│   │   └── util/                     # 工具类（数据生成器、模型信息查看工具等）
│   ├── src/main/resources/
│   │   └── application.yml           # 应用配置
│   └── pom.xml
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── views/                    # 页面组件
│   │   ├── router/                   # 路由配置
│   │   ├── App.vue
│   │   └── main.js
│   └── package.json
├── model/                            # 预训练情感模型 (sentiment_model.zip)
└── sql/
    └── init.sql                      # 数据库初始化脚本

文本信息情感分析/                        # TextCNN 模型训练模块（独立项目）
└── src/main/java/com/sentiment/
    ├── SentimentModel.java           # TextCNN 训练与评估
    └── SmokeTest.java                # 冒烟测试类
```


## 数据库设计

### MySQL 表结构

| 表名 | 说明 |
|------|------|
| `posts` | 帖子数据：平台来源、内容、互动数据、情感分析结果、地域信息 |
| `hot_topics` | 热点话题：聚类名称、关键词、热度分数、情感趋势 (JSON) |
| `alerts` | 告警记录：类型、严重程度、触发数据 (JSON)、已读/通知状态 |
| `health_check` | 系统模块健康状态 |
| `monitor_keywords` | 监控关键词配置 |
| `alert_rules` | 告警规则配置 (JSON) |

### Redis 用途

| Key | 用途 |
|-----|------|
| `weibo:cookies` | 微博登录 Cookie 缓存 |
| `xhs:cookies` | 小红书登录 Cookie 缓存 |
| `sentiment:collector:enabled` | 采集器开关 |
| `sentiment:analyzer:enabled` | 分析器开关 |

## 核心算法

### TextCNN 情感分类模型

```
Input [batch, 1500] (字符哈希索引序列)
  → Embedding (vocab=1500, dim=256)
  → Conv1D (kernel=3, 256→128) + ReLU
  → Conv1D (kernel=5, 128→128) + ReLU
  → GlobalMaxPooling
  → Dense (128→128) + ReLU + Dropout(0.5)
  → Output (128→2) + Softmax
```

- 训练数据集：ChnSentiCorp 中文情感语料
- 优化器：Adam (lr=0.001)，支持早停与学习率衰减

### DBSCAN 话题聚类

- 文本表示：TF-IDF 稀疏向量
- 距离度量：余弦距离
- 参数：eps=0.3, min_samples=5
- 话题合并：Jaccard 相似度 > 0.85

## 快速开始

### 环境要求

- JDK 11+
- Node.js 16+
- MySQL 8.0+
- Redis 6+
- CUDA 11.6（GPU 加速推理，可选）

### 1. 初始化数据库

```bash
mysql -u root -p < sentiment-platform/sql/init.sql
```

### 2. 配置环境变量（可选）

```bash
export MYSQL_PASSWORD=your_password
export REDIS_PASSWORD=your_redis_password
export MODEL_PATH=/path/to/sentiment_model.zip
export NOTIFICATION_WEBHOOK=https://your-webhook-url
```

### 3. 启动后端

```bash
cd sentiment-platform/backend
mvn spring-boot:run
```

后端服务默认运行在 `http://localhost:9090`

### 4. 启动前端

```bash
cd sentiment-platform/frontend
npm install
npm run dev
```

前端开发服务器默认运行在 `http://localhost:5173`

### 5. 训练情感模型（可选）

```bash
cd 文本信息情感分析
mvn compile exec:java -Dexec.mainClass="com.sentiment.SentimentModel"
```

## 前端页面

| 路由 | 页面 | 功能 |
|------|------|------|
| `/live` | 实时舆情流 | 实时展示新采集的帖子，WebSocket 推送 |
| `/dashboard` | 数据总览 | 关键指标卡片、情感分布图、平台统计 |
| `/trend` | 趋势分析 | 情感趋势折线图、地域分布热力图 |
| `/topics` | 话题追踪 | 热点话题列表、话题详情与情感走势 |
| `/alerts` | 预警中心 | 告警列表、告警详情、规则管理 |
| `/settings` | 系统设置 | 采集/分析开关、监控关键词、通知配置 |

## API 接口

后端提供 RESTful API，主要端点：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/posts` | 分页查询帖子 |
| GET | `/api/dashboard/stats` | 仪表盘统计数据 |
| GET | `/api/trend/sentiment` | 情感趋势数据 |
| GET | `/api/trend/region` | 地域分布数据 |
| GET | `/api/topics` | 热点话题列表 |
| GET | `/api/alerts` | 告警列表 |
| GET | `/api/alerts/rules` | 告警规则列表 |
| POST | `/api/alerts/rules` | 创建告警规则 |
| GET | `/api/settings/keywords` | 监控关键词列表 |
| POST | `/api/settings/keywords` | 添加监控关键词 |

## 许可证

MIT
