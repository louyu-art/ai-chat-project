<template>
  <div class="login-container">
    <h2>AI智能客服 · 登录</h2>
    <input v-model="username" placeholder="用户名" />
    <input v-model="password" type="password" placeholder="密码" @keyup.enter="doLogin" />
    <button @click="doLogin" :disabled="loading">{{ loading ? '登录中...' : '登录' }}</button>
    <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { request, saveLogin, isAgentRole } from '../api'

const router = useRouter()
const username = ref('')
const password = ref('')
const errorMsg = ref('')
const loading = ref(false)

// 提交登录：后端用URLSearchParams传表单参数，成功后按角色跳转
const doLogin = async () => {
  if (!username.value || !password.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await request('/chat/login', {
      method: 'POST',
      body: new URLSearchParams({ username: username.value, password: password.value })
    })
    if (data.code !== 200) {
      errorMsg.value = data.msg || '登录失败'
      return
    }
    saveLogin(data.data)
    router.push(isAgentRole(data.data.role) ? '/agent' : '/chat')
  } catch (e) {
    errorMsg.value = '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  width: 320px;
  margin: 120px auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
h2 {
  text-align: center;
}
input {
  padding: 8px;
}
button {
  padding: 8px;
}
.error {
  color: #c00;
  font-size: 13px;
  text-align: center;
}
</style>
