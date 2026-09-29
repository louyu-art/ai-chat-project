# AI 智能客服前端（ai-chat-front）

基于 Vue 3 + Vite 的智能客服前端，面向访客与人工坐席两端，
通过 WebSocket 与后端实时通信，支持 AI 客服自动回复与人工坐席接管。

## 技术栈

- Vue 3.5（`<script setup>` 组合式 API）
- Vue Router 4（前端路由与角色守卫）
- Vite 8（开发与构建工具）
- 原生 fetch（统一封装于 `src/api/index.js`）
- 原生 WebSocket（与后端 `/ws/chat` 端点通信）
- 原生 CSS（无 UI 库、无 CSS 预处理器，组件内 scoped 样式）

> 项目使用纯 JavaScript，未引入 TypeScript。

## 目录结构

```
ai-chat-front
├── index.html                          # HTML 入口，标题「AI智能客服」
├── package.json
├── vite.config.js                      # Vite 配置（仅启用 vue 插件）
└── src
    ├── main.js                          # 入口：createApp + 挂载 router
    ├── App.vue                          # 根组件，仅一个 <router-view />
    ├── style.css                        # 全局样式
    ├── api
    │   └── index.js                     # 统一请求封装与登录态管理
    ├── router
    │   └── index.js                     # 路由配置与全局守卫
    └── views
        ├── LoginView.vue               # 登录页
        ├── ChatView.vue                # 访客聊天页（AI 接待 + 转人工）
        └── AgentView.vue              # 客服工作台（认领/接待/释放会话）
```

## 环境要求

- Node.js 18+
- npm（或 pnpm / yarn，下文以 npm 为例）

后端依赖：需先启动 `ai-chat-backend`（默认 `http://localhost:8080`）。

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

浏览器打开 `http://localhost:5173`。

构建生产包：

```bash
npm run build      # 产物输出到 dist/
npm run preview    # 本地预览构建产物
```

## 路由说明

| 路径     | 视图          | 鉴权要求              | 说明                       |
|----------|---------------|-----------------------|----------------------------|
| `/login` | LoginView     | 无                    | 登录入口                   |
| `/chat`  | ChatView      | 登录，且角色为 USER   | 访客聊天页                 |
| `/agent` | AgentView     | 登录，且角色为 AGENT/ADMIN | 客服工作台            |
| `/`      | -             | 按角色重定向          | 已登录按角色跳转 `/agent` 或 `/chat`，未登录跳 `/login` |

全局守卫（`src/router/index.js`）：

- 未登录访问受保护页面 → 跳 `/login`
- 已登录访问 `/login` → 按角色回首页
- 角色与目标页面不匹配 → 回各自首页（坐席进 `/agent`，访客进 `/chat`）

## 登录态与请求封装

`src/api/index.js` 统一管理：

- `localStorage` 存储 `token`、`user`（id/username/role）、`sessionId`
- `request(url, options)` 自动附带 `Authorization: Bearer {token}` 请求头
- 401 响应自动清空登录态并跳转 `/login`
- `isAgentRole(role)`：判断是否为 `AGENT` 或 `ADMIN`（忽略大小写）

> 后端默认地址写死为 `http://localhost:8080`（`BASE_URL` 常量），部署到服务器需修改此处。

## WebSocket 通信

连接地址：

```
ws://127.0.0.1:8080/ws/chat?sessionId={sessionId}&token={token}              # 访客
ws://127.0.0.1:8080/ws/chat?sessionId={sessionId}&role=agent&token={token}    # 坐席
```

下行消息（JSON）：

```json
{ "senderType": "AI|AGENT|VISITOR", "content": "消息内容" }
{ "event": "TRANSFER|CLAIM|RELEASE", "sessionId": "..." }
```

前端处理：

- 聊天消息按 `senderType` 渲染（AI / 人工坐席 / 访客 / 系统 四种样式）
- 列表事件（`event` 字段）触发会话列表刷新

## 功能说明

### 访客端（ChatView）

- 进入页面自动复用 `localStorage.sessionId` 或调用 `/chat/createSession` 创建会话，并建立 WebSocket
- 拉取历史消息（`/chat/messages`），刷新页面不丢上下文
- 发送消息经 WebSocket 至后端，AI 模式下由后端调用 Ollama 异步回推
- 一键「转人工」，调用 `/chat/transferAgent`，会话切为人工模式后消息直送坐席
- 退出登录调用 `/chat/logout` 并清空本地登录态

### 客服工作台（AgentView）

- 左侧会话列表分两组：「待认领」「我接待的」（`/chat/agent/sessions`）
- 选中会话加载历史消息并建立坐席 WebSocket 连接
- 待认领会话需先「认领」（`/chat/agent/claim`）才能回复，避免误抢
- 已认领会话支持「释放」（`/chat/agent/release`）回待认领池
- 实时接收访客消息，并通过后端回显同步显示坐席发送的消息
- 列表事件触发自动刷新，另设 5 秒轮询兜底保证多坐席操作最终一致

## 测试账号

后端 SQL 初始化的测试账号（密码均为 `123456`）：

| 用户名 | 角色   | 入口     |
|--------|--------|----------|
| admin  | admin  | 工作台   |
| agent1 | AGENT  | 工作台   |
| user1  | USER   | 访客聊天 |

## 配置修改指引

如需指向不同后端地址，修改 `src/api/index.js` 顶部 `BASE_URL` 常量；
WebSocket 地址硬编码在 `ChatView.vue` 与 `AgentView.vue` 中，按需替换 `127.0.0.1:8080`。
