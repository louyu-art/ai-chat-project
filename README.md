# AI 智能客服（本地运行版）

前后端分离的 AI 客服演示项目：

- `ai-chat-backend`：Spring Boot 3.2 + MyBatis-Plus + RabbitMQ + WebSocket，通过本机 Ollama（Qwen）生成回答
- `ai-chat-front`：Vue 3 + Vite

## 本地环境要求

- JDK 17+
- MySQL 8.x
- RabbitMQ 3.x（默认账号 `admin / admin123`）
- Node.js 18+
- Ollama，并拉取模型：`ollama pull qwen:1.8b`

## 后端启动

1. 初始化数据库（脚本会自建 `ai_chat_db` 库和表，并写入测试坐席账号 admin/123456）：

   ```bash
   mysql -uroot -p < ai-chat-backend/src/main/resources/sql/ai_chat_db.sql
   ```

2. 按本机实际情况修改 `ai-chat-backend/src/main/resources/application.yaml` 中的 MySQL、RabbitMQ 账号密码，以及 Ollama 地址/模型名。

3. 启动（Windows 用 `mvnw.cmd`，类 Linux 用 `./mvnw`）：

   ```bash
   cd ai-chat-backend
   mvnw.cmd spring-boot:run
   ```

启动成功后接口文档在 http://localhost:8080/doc.html（knife4j）。

## 前端启动

```bash
cd ai-chat-front
npm install
npm run dev
```

浏览器打开 http://localhost:5173 。

## 功能说明

- 打开页面自动创建会话并建立 WebSocket 连接（`/ws/chat?sessionId=xxx`）；
- AI 模式下，访客消息先入库，再投递到 RabbitMQ，由消费者调用本地 Ollama 生成回答，经 WebSocket 推送给前端（推送内容带 `senderType`，前端区分 AI/人工/系统消息）；
- 点击「转人工」后会话切换为人工模式（`ARTIFICIAL`），后续访客消息不再调用 AI；
- 坐席登录测试接口：`POST /chat/agentLogin`（username=admin，password=123456）。
