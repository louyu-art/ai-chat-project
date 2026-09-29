# AI 智能客服后端服务（ai-chat-backend）

基于 Spring Boot 3.2 + MyBatis-Plus + RabbitMQ + WebSocket 的智能客服后端，
通过本机 Ollama 调用 Qwen 模型生成 AI 回答，并支持访客与人工坐席的双向实时通信。

## 技术栈

- Java 17
- Spring Boot 3.2.0
- Spring Web（REST 接口）
- Spring WebSocket（实时双向通信）
- Spring AMQP / RabbitMQ（AI 任务异步解耦）
- MyBatis-Plus 3.5.5（ORM，Spring Boot 3 专用 starter）
- MySQL 8.x（持久化）
- Knife4j 4.3.0（OpenAPI3 接口文档，访问 `/doc.html`）
- Lombok（实体类模板代码简化）
- Ollama 本地推理（HTTP 调用 `qwen:1.8b`）

## 目录结构

```
ai-chat-backend
├── pom.xml                                  # Maven 依赖与构建配置
├── mvnw / mvnw.cmd                          # Maven Wrapper（Windows 用 mvnw.cmd）
└── src/main
    ├── java/com/aichat
    │   ├── AiChatApplication.java            # 启动类，扫描 mapper 包
    │   ├── config
    │   │   ├── RabbitConfig.java            # MQ 队列/交换机/绑定/JSON 转换器
    │   │   ├── WebSocketConfig.java         # 注册 /ws/chat 端点
    │   │   ├── WebMvcConfig.java            # 注册鉴权拦截器
    │   │   └── CorsConfig.java             # 跨域放行前端 5173 端口
    │   ├── common
    │   │   ├── AuthInterceptor.java         # 鉴权拦截器，校验 token 与客服角色
    │   │   ├── GlobalExceptionHandler.java # 全局异常处理
    │   │   └── Result.java                  # 统一响应封装 {code,msg,data}
    │   ├── controller
    │   │   └── ChatController.java          # 登录/会话/转人工/坐席管理接口
    │   ├── handler
    │   │   └── ChatWebSocketHandler.java    # WebSocket 连接与消息分发
    │   ├── service
    │   │   ├── AuthService.java             # 登录态维护（内存 token，演示用）
    │   │   ├── LlmService.java             # RabbitMQ 投递 + Ollama 同步调用
    │   │   └── WebSocketService.java       # 在线会话管理、消息入库与推送
    │   ├── listener
    │   │   └── LlmTaskListener.java         # 消费 MQ 异步调用 LLM 并回推
    │   ├── dto
    │   │   └── LlmTaskDTO.java              # MQ 任务载荷
    │   ├── entity
    │   │   ├── SysUser.java                 # 坐席/用户
    │   │   ├── ChatSession.java             # 会话
    │   │   ├── ChatMessage.java             # 消息
    │   │   └── LlmCallLog.java              # LLM 调用日志
    │   └── mapper
    │       ├── SysUserMapper.java
    │       ├── ChatSessionMapper.java
    │       ├── ChatMessageMapper.java
    │       └── LlmCallLogMapper.java
    └── resources
        ├── application.yaml                 # 应用配置
        └── sql/ai_chat_db.sql               # 建库建表与测试数据脚本
```

## 环境要求

- JDK 17 或以上
- Maven 3.6+（或直接使用项目自带 `mvnw.cmd`，无需本机安装 Maven）
- MySQL 8.x
- RabbitMQ 3.x（默认账号 `guest / guest`）
- 本机已安装 Ollama，并拉取模型：

```bash
ollama pull qwen:1.8b
```

## 快速开始

### 1. 初始化数据库

执行 SQL 脚本，会自动创建 `ai_chat_db` 库与 4 张表，并写入 3 个测试账号：

```bash
mysql -uroot -p < src/main/resources/sql/ai_chat_db.sql
```

测试账号（密码均为 `123456`）：

| 用户名  | 角色   | 用途           |
|---------|--------|----------------|
| admin   | admin  | 管理员（兼具客服权限）|
| agent1  | AGENT  | 人工坐席       |
| user1   | USER   | 普通访客       |

### 2. 修改配置

按本机环境修改 `src/main/resources/application.yaml`：

- `spring.datasource`：MySQL 地址、账号、密码
- `spring.rabbitmq`：RabbitMQ 地址与账号（默认 `guest/guest`）
- `llm.ollama`：Ollama 服务地址与模型名

### 3. 启动服务

Windows：

```bash
mvnw.cmd spring-boot:run
```

Linux/macOS：

```bash
./mvnw spring-boot:run
```

启动成功后：

- 服务端口：`http://localhost:8080`
- 接口文档：`http://localhost:8080/doc.html`（Knife4j）

## 配置说明（application.yaml）

```yaml
server:
  port: 8080                               # 服务端口

spring:
  datasource:                              # MySQL 数据源
    url: jdbc:mysql://localhost:3306/ai_chat_db?...
    username: root
    password: "123456"
  rabbitmq:                                # RabbitMQ 连接
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest

mybatis-plus:                              # ORM 配置
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.aichat.entity
  configuration:
    map-underscore-to-camel-case: true     # 数据库下划线自动转驼峰

llm:                                       # 本地 Ollama
  ollama:
    url: http://localhost:11434/api/chat
    model-name: qwen:1.8b
```

## REST 接口一览

统一前缀 `/chat`，除 `/chat/login` 外均需携带请求头 `Authorization: Bearer {token}`。
统一响应结构：`{ code, msg, data }`，`code=200` 为成功。

| 方法 | 路径                       | 鉴权          | 说明                                   |
|------|----------------------------|---------------|----------------------------------------|
| POST | `/chat/login`              | 无            | 登录，返回 token 与用户信息             |
| POST | `/chat/logout`             | 登录          | 退出登录，销毁 token                   |
| GET  | `/chat/createSession`      | 登录          | 创建访客会话，返回 sessionId           |
| GET  | `/chat/messages?sessionId=`| 登录          | 拉取会话历史消息                       |
| POST | `/chat/transferAgent?sessionId=` | 登录   | 访客转人工，会话状态切为 ARTIFICIAL    |
| GET  | `/chat/agent/sessions`     | 客服角色      | 获取待认领 + 我接待的会话列表          |
| POST | `/chat/agent/claim?sessionId=`   | 客服角色 | 认领会话（数据库原子更新防抢单）       |
| POST | `/chat/agent/release?sessionId=` | 客服角色 | 释放会话回待认领池                     |

## WebSocket 协议

端点：`ws://localhost:8080/ws/chat`

连接参数（Query String）：

| 参数       | 必填 | 说明                                  |
|------------|------|---------------------------------------|
| sessionId  | 是   | 会话 ID                               |
| role       | 否   | `agent` 表示坐席连接，缺省视为访客    |
| token      | 坐席必填 | 登录 token，坐席连接需校验 AGENT/ADMIN 角色 |

下行消息为 JSON：

```json
// 聊天消息
{ "senderType": "AI|AGENT|VISITOR", "content": "消息内容", "sessionId": "..." }

// 列表事件广播（仅坐席）
{ "event": "TRANSFER|CLAIM|RELEASE", "sessionId": "..." }
```

## 业务流程

1. **会话建立**：访客登录后调用 `/chat/createSession` 拿到 sessionId，前端连接 WebSocket。
2. **AI 接待**：访客消息经 WebSocket 进入 `WebSocketService.handleVisitorMsg`，先入库，
   再根据会话状态：`AI` 模式投递到 RabbitMQ，由 `LlmTaskListener` 异步调用 Ollama，
   生成回答经 WebSocket 推送给访客；`ARTIFICIAL` 模式直接推送给在线坐席。
3. **转人工**：访客调用 `/chat/transferAgent`，会话状态切为 `ARTIFICIAL`，
   并向所有在线坐席广播 `TRANSFER` 事件，坐席工作台刷新列表。
4. **认领/释放**：坐席通过 `/chat/agent/claim` 与 `/chat/agent/release` 操作会话归属，
   采用 `WHERE agent_id IS NULL` 数据库级原子更新，避免多坐席抢单冲突。

## 关键设计说明

- **鉴权**：当前 `AuthService` 使用内存 `ConcurrentHashMap` 保存 token（重启失效），
  代码注释明确标注「正式项目换 JWT」，便于后续替换。
- **消息可靠入库**：访客/坐席消息一律先写 `chat_message` 表再推送 WebSocket，
  保证访客断线期间的 AI/人工回复不丢失，可通过 `/chat/messages` 拉取历史。
- **AI 异步解耦**：AI 调用通过 RabbitMQ 投递任务，避免阻塞 WebSocket 线程，
  队列名 `llm_task_queue`、交换机 `llm_task_exchange`、routingKey `llm.task`。
- **会话状态机**：`AI`（AI 接待）→ `ARTIFICIAL`（人工接待），状态字段集中维护在 `chat_session.session_status`。

## 数据表结构

| 表名           | 说明                       | 关键字段                                       |
|----------------|----------------------------|------------------------------------------------|
| sys_user       | 坐席/访客账号              | username, password, role(agent/admin/USER)    |
| chat_session   | 会话                       | session_id, visitor_id, agent_id, session_status|
| chat_message   | 消息                       | session_id, sender_type(VISITOR/AI/AGENT), content |
| llm_call_log   | LLM 调用日志               | session_id, prompt, token_count, call_status   |

## 备注

- 项目为本地演示版，未使用 Redis、JWT、分布式锁等正式生产组件，
  升级路径清晰：token 换 JWT、会话在线表换 Redis、抢单可加 Redis 分布式锁。
- RabbitMQ 账号 `guest/guest` 仅用于本机演示，部署到服务器请改用业务账号。
