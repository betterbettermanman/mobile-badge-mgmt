import { reactive } from 'vue'
import { api } from './api'

export const authState = reactive({
  user: null,
  ready: false
})

export function homePath(user = authState.user) {
  if (!user) return '/login'
  return user.role === 'ADMIN' ? '/admin' : '/app/sessions'
}

export async function refreshMe() {
  try {
    authState.user = await api('/api/me')
  } catch (error) {
    if (error.status === 401) authState.user = null
    else throw error
  } finally {
    authState.ready = true
  }
}

export async function login(account, password) {
  authState.user = await api('/api/auth/login', {
    method: 'POST',
    body: { account, password },
    allow401: true
  })
  return authState.user
}

export async function logout() {
  try {
    await api('/api/auth/logout', { method: 'POST' })
  } finally {
    authState.user = null
  }
}

export function isAdmin() {
  return authState.user?.role === 'ADMIN'
}
