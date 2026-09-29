<template>
  <div class="workbench">
    <div class="header">
      <h2>客服工作台</h2>
      <div>
        <span class="username">{{ user?.username }}</span>
        <button @click="doLogout">退出登录</button>
      </div>
    </div>
    <div class="main">
      <!-- 左侧：会话列表 -->
      <div class="session-list">
        <h3>待认领</h3>
        <div
          v-for="s in pendingList"
          :key="s.sessionId"
          class="session-item"
          :class="{ active: s.sessionId === currentSessionId }"
          @click="selectSession(s, 'pending')"
        >
          <div class="visitor">{{ s.visitorId }}</div>
          <div class="preview">{{ s.lastMessage || '（无消息）' }}</div>
        </div>
        <p v-if="pendingList.length === 0" class="empty">暂无待认领会话</p>

        <h3>我接待的</h3>
        <div
          v-for="s in mineList"
          :key="s.sessionId"
          class="session-item"
          :class="{ active: s.sessionId === currentSessionId }"
          @click="selectSession(s, 'mine')"
        >
          <div class="visitor">{{ s.visitorId }}</div>
          <div class="preview">{{ s.lastMessage || '（无消息）' }}</div>
        </div>
        <p v-if="mineList.length === 0" class="empty">暂无接待中会话</p>
      </div>

      <!-- 右侧：聊天窗口 -->
      <div class="chat-panel" v-if="currentSessionId">
        <div class="chat-header">
          <span>会话：{{ currentVisitorId }}</span>
          <div>
            <button v-if="isCurrentPending" @click="claimSession">认领</button>
            <button v-else @click="releaseSession">释放</button>
          </div>
        </div>
        <div class="msg-box" ref="msgBoxRef">
          <div v-for="item in msgList" :key="item.id" class="msg-item" :class="item.type">
            <span>{{ item.sender }}：{{ item.content }}</span>
          </div>
        </div>
        <div class="input-area">
          <input
            v-model="inputText"
            :disabled="isCurrentPending"
            @keyup.enter="sendMsg"
            :placeholder="isCurrentPending ? '认领后才能回复' : '输入回复...'"
          />
          <button @click="sendMsg" :disabled="isCurrentPending">发送</button>
        </div>
      </div>
      <div class="chat-panel placeholder" v-else>
        <p>请选择左侧会话查看聊天内容</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { request, getUser, getToken, clearLogin } from '../api'

const router = useRouter()
const user = getUser()

const pendingList = ref([])
const mineList = ref([])
const currentSessionId = ref('')
const currentVisitorId = ref('')
const currentFrom = ref('') // 'pending' 或 'mine'
const msgList = ref([])
const inputText = ref('')
const msgBoxRef = ref(null)
let ws = null
let msgId = 0
let pollTimer = null

// 当前选中的是否为待认领会话（未认领前禁止回复）
const isCurrentPending = computed(() => currentFrom.value === 'pending')

const senderView = (senderType) => {
  if (senderType === 'AGENT') return { sender: '我', type: 'agent' }
  if (senderType === 'VISITOR') return { sender: '访客', type: 'visitor' }
  if (senderType === 'AI') return { sender: 'AI客服', type: 'ai' }
  return { sender: '系统', type: 'system' }
}

// 加载会话列表（待认领 + 我接待的）
const loadSessions = async () => {
  try {
    const data = await request('/chat/agent/sessions')
    if (data.code === 200) {
      pendingList.value = data.data.pending
      mineList.value = data.data.mine
    }
  } catch (e) {
    // 401时request已处理跳转
  }
}

// 选中会话：加载历史消息并建立坐席ws连接
const selectSession = async (session, from) => {
  currentSessionId.value = session.sessionId
  currentVisitorId.value = session.visitorId
  currentFrom.value = from
  msgList.value = []
  await loadHistory()
  connectWs()
}

// 拉取该会话历史消息
const loadHistory = async () => {
  const data = await request(`/chat/messages?sessionId=${currentSessionId.value}`)
  if (data.code !== 200) return
  data.data.forEach((m) => {
    const view = senderView(m.senderType)
    addMsg(view.sender, m.content, view.type)
  })
  scrollBottom()
}

// 建立坐席WebSocket：接收访客实时消息和坐席消息回显
const connectWs = () => {
  if (ws) ws.close()
  ws = new WebSocket(
    `ws://127.0.0.1:8080/ws/chat?sessionId=${currentSessionId.value}&role=agent&token=${getToken()}`
  )
  ws.onmessage = (event) => {
    let msg
    try {
      msg = JSON.parse(event.data)
    } catch {
      return
    }
    // 列表变更广播：刷新会话列表
    if (msg.event) {
      loadSessions()
      return
    }
    // 聊天消息：访客实时消息或坐席消息回显
    const view = senderView(msg.senderType)
    addMsg(view.sender, msg.content, view.type)
    scrollBottom()
    // 访客新消息会改变列表预览，顺手刷新
    if (msg.senderType === 'VISITOR') {
      loadSessions()
    }
  }
  ws.onclose = () => {
    ws = null
  }
}

// 认领当前待认领会话
const claimSession = async () => {
  const data = await request(`/chat/agent/claim?sessionId=${currentSessionId.value}`, { method: 'POST' })
  if (data.code === 200) {
    currentFrom.value = 'mine'
    await loadSessions()
  } else {
    alert(data.msg)
    await loadSessions()
  }
}

// 释放当前会话回待认领池
const releaseSession = async () => {
  const data = await request(`/chat/agent/release?sessionId=${currentSessionId.value}`, { method: 'POST' })
  if (data.code === 200) {
    if (ws) ws.close()
    currentSessionId.value = ''
    currentVisitorId.value = ''
    currentFrom.value = ''
    msgList.value = []
    await loadSessions()
  } else {
    alert(data.msg)
  }
}

// 发送回复
const sendMsg = () => {
  if (!inputText.value || !ws) return
  // 消息显示依赖后端回显，避免与回显重复，本地不再追加
  ws.send(inputText.value)
  inputText.value = ''
}

// 退出登录
const doLogout = async () => {
  try {
    await request('/chat/logout', { method: 'POST' })
  } finally {
    if (ws) ws.close()
    clearLogin()
    router.push('/login')
  }
}

const addMsg = (sender, content, type) => {
  msgList.value.push({ id: msgId++, sender, content, type })
}

const scrollBottom = () => {
  nextTick(() => {
    if (msgBoxRef.value) {
      msgBoxRef.value.scrollTop = msgBoxRef.value.scrollHeight
    }
  })
}

onMounted(() => {
  loadSessions()
  // 轮询兜底，保证其他坐席操作后列表最终一致
  pollTimer = setInterval(loadSessions, 5000)
})

onUnmounted(() => {
  if (ws) ws.close()
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.workbench {
  width: 900px;
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
.main {
  display: flex;
  gap: 12px;
  height: 480px;
}
.session-list {
  width: 260px;
  border: 1px solid #ccc;
  padding: 8px;
  overflow-y: auto;
}
.session-list h3 {
  margin: 8px 0;
  font-size: 14px;
  color: #666;
}
.session-item {
  padding: 8px;
  border: 1px solid #eee;
  margin-bottom: 6px;
  cursor: pointer;
  border-radius: 4px;
}
.session-item:hover {
  background: #f5f5f5;
}
.session-item.active {
  border-color: #0066cc;
  background: #eef5ff;
}
.visitor {
  font-weight: bold;
  font-size: 13px;
}
.preview {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.empty {
  font-size: 12px;
  color: #999;
}
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #ccc;
}
.chat-panel.placeholder {
  align-items: center;
  justify-content: center;
  color: #999;
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px;
  border-bottom: 1px solid #eee;
}
.msg-box {
  flex: 1;
  padding: 10px;
  overflow-y: auto;
}
.msg-item {
  margin: 8px 0;
}
.visitor {
  color: #0066cc;
}
.agent {
  color: #1a7f37;
}
.ai {
  color: #222;
}
.system {
  color: #999;
  font-size: 12px;
}
.input-area {
  padding: 8px;
  border-top: 1px solid #eee;
}
input {
  width: 75%;
  padding: 6px;
}
button {
  margin-left: 6px;
  padding: 6px 12px;
}
</style>
