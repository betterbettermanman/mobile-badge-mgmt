<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { homePath, login } from '../auth'

const route = useRoute()
const router = useRouter()
const account = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  if (!account.value.trim()) {
    error.value = '请输入账号'
    return
  }
  if (!password.value) {
    error.value = '请输入密码'
    return
  }
  loading.value = true
  try {
    const user = await login(account.value.trim(), password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    if (redirect.startsWith('/app') || (redirect.startsWith('/admin') && user.role !== 'ADVISOR')) {
      router.push(redirect)
    } else {
      router.push(homePath(user))
    }
  } catch (err) {
    error.value = err.status === 401 ? '账号或密码不正确' : (err.message || '网络异常，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="login-card stack" @submit.prevent="submit">
      <div class="brand-mark">牌</div>
      <div>
        <h1>移动工牌管理</h1>
        <p class="sub">手机号或工号登录。忘记密码请联系管理员。</p>
      </div>
      <label class="field">
        <span>手机号 / 工号</span>
        <input v-model="account" autocomplete="username" />
      </label>
      <label class="field">
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" />
      </label>
      <p v-if="error" class="error-text">{{ error }}</p>
      <button class="btn primary block" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
    </form>
  </div>
</template>
