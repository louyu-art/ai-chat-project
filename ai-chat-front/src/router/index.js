import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import ChatView from '../views/ChatView.vue'
import AgentView from '../views/AgentView.vue'
import { getUser, isAgentRole } from '../api'

// 按角色返回首页：客服进工作台，普通用户进聊天页
const homeByRole = () => {
    const user = getUser()
    if (!user) return '/login'
    return isAgentRole(user.role) ? '/agent' : '/chat'
}

const routes = [
    { path: '/login', component: LoginView },
    { path: '/chat', component: ChatView, meta: { requireAuth: true, role: 'USER' } },
    { path: '/agent', component: AgentView, meta: { requireAuth: true, role: 'AGENT' } },
    { path: '/', redirect: () => homeByRole() }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// 全局守卫：未登录跳登录页，角色不匹配时回各自首页
router.beforeEach((to) => {
    const user = getUser()
    if (to.path === '/login') {
        // 已登录用户访问登录页直接回首页
        return user ? homeByRole() : true
    }
    if (to.meta.requireAuth && !user) {
        return '/login'
    }
    if (to.meta.role === 'AGENT' && !isAgentRole(user.role)) {
        return '/chat'
    }
    if (to.meta.role === 'USER' && isAgentRole(user.role)) {
        return '/agent'
    }
    return true
})

export default router
