<template>
  <div class="container">
    <div class="header">
      <h2>AI智能客服</h2>
      <div>
        <span class="username">{{ user?.username }}</span>
        <button @click="doLogout">退出登录</button>
      </div>
    </div>
    <div class="msg-box" ref="msgBoxRef">
      <div v-for="item in msgList" :key="item.id" class="msg-item" :class="item.type">
        <span>{{ item.sender }}：{{ item.content }}</span>
      </div>
    </div>
    <div class="input-area">
      <input v-model="inputText" @keyup.enter="sendMsg" placeholder="输入消息..." />
      <button @click="sendMsg">发送</button>
      <button @click="transferAgent">转人工</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { request, getUser, getToken, clearLogin } from '../api'

const router = useRouter()
const user = getUser()

const msgBoxRef = ref(null)
const inputText = ref('')
const msgList = ref([])
let ws = null
let sessionId = ref('')
let msgId = 0

// senderType映射显示名和样式，历史消息与实时消息共用
const senderView = (senderType) => {
  if (senderType === 'AGENT') return { sender: '人工坐席', type: 'agent' }
  if (senderType === 'AI') return { sender: 'AI客服', type: 'ai' }
  if (senderType === 'VISITOR') return { sender: '我', type: 'visitor' }
  return { sender: '系统', type: 'system' }
}

// 初始化：优先复用localStorage中的会话（刷新不掉线），否则创建新会话
const initSession = async () => {
  const cached = localStorage.getItem('sessionId')
  if (cached) {
    sessionId.value = cached
    await loadHistory()
    initWebSocket()
    return
  }
  const data = await request('/chat/createSession')
  if (data.code !== 200) {
    addMsg('系统', data.msg || '创建会话失败', 'system')
    return
  }
  sessionId.value = data.data
  localStorage.setItem('sessionId', data.data)
  initWebSocket()
}

// 拉取历史消息（刷新页面后恢复聊天记录）
const loadHistory = async () => {
  const data = await request(`/chat/messages?sessionId=${sessionId.value}`)
  if (data.code !== 200) return
  data.data.forEach((m) => {
    const view = senderView(m.senderType)
    addMsg(view.sender, m.content, view.type)
  })
  scrollBottom()
}

// 初始化WebSocket
const initWebSocket = () => {
  ws = new WebSocket(`ws://127.0.0.1:8080/ws/chat?sessionId=${sessionId.value}&token=${getToken()}`)

  ws.onopen = () => {
    addMsg('系统', '连接成功，可以开始咨询', 'system')
  }

  ws.onmessage = (event) => {
    // 后端推送 {"senderType":"AI/AGENT/...","content":"..."}
    let msg
    try {
      msg = JSON.parse(event.data)
    } catch {
      msg = { senderType: 'AI', content: event.data }
    }
    const view = senderView(msg.senderType)
    addMsg(view.sender, msg.content, view.type)
    scrollBottom()
  }

  ws.onclose = () => {
    addMsg('系统', '连接断开', 'system')
  }
}

// 发送消息
const sendMsg = () => {
  if (!inputText.value || !ws) return
  addMsg('我', inputText.value, 'visitor')
  ws.send(inputText.value)
  inputText.value = ''
  scrollBottom()
}

// 转人工
const transferAgent = async () => {
  const data = await request(`/chat/transferAgent?sessionId=${sessionId.value}`, { method: 'POST' })
  addMsg('系统', data.code === 200 ? '已提交转人工申请' : data.msg, 'system')
}

// 退出登录：通知后端销毁token并清空本地登录态
const doLogout = async () => {
  try {
    await request('/chat/logout', { method: 'POST' })
  } finally {
    if (ws) ws.close()
    clearLogin()
    router.push('/login')
  }
}

// 添加消息到列表
const addMsg = (sender, content, type) => {
  msgList.value.push({
    id: msgId++,
    sender,
    content,
    type
  })
}

// 滚动到底部
const scrollBottom = () => {
  setTimeout(() => {
    msgBoxRef.value.scrollTop = msgBoxRef.value.scrollHeight
  }, 50)
}

onMounted(() => {
  initSession()
})

onUnmounted(() => {
  if (ws) ws.close()
})
</script>

<style scoped>
.container {
  width: 600px;
  margin: 20px auto;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.username {
  margin-right: 8px;
  color: #666;
}
.msg-box {
  height: 420px;
  border: 1px solid #ccc;
  padding: 10px;
  overflow-y: auto;
}
.msg-item {
  margin: 8px 0;
}
.visitor {
  color: #0066cc;
}
.ai {
  color: #222;
}
.agent {
  color: #1a7f37;
}
.system {
  color: #999;
  font-size: 12px;
}
.input-area {
  margin-top: 10px;
}
input {
  width: 450px;
  padding: 6px;
}
button {
  margin-left: 6px;
  padding: 6px 12px;
}
</style>
