// 统一请求封装：自动携带token，401时清登录态并跳登录页
const BASE_URL = 'http://localhost:8080'

export const getToken = () => localStorage.getItem('token')

export const getUser = () => {
    const raw = localStorage.getItem('user')
    return raw ? JSON.parse(raw) : null
}

export const saveLogin = (data) => {
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify({ id: data.id, username: data.username, role: data.role }))
}

export const clearLogin = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('sessionId')
}

// 判断是否为客服角色（AGENT/ADMIN，忽略大小写）
export const isAgentRole = (role) => {
    if (!role) return false
    const r = role.toUpperCase()
    return r === 'AGENT' || r === 'ADMIN'
}

export const request = async (url, options = {}) => {
    const headers = { ...(options.headers || {}) }
    const token = getToken()
    if (token) {
        headers['Authorization'] = `Bearer ${token}`
    }
    const res = await fetch(BASE_URL + url, { ...options, headers })
    if (res.status === 401) {
        clearLogin()
        window.location.href = '/login'
        throw new Error('未登录或登录已过期')
    }
    return res.json()
}
