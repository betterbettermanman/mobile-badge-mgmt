<script setup>
import { useRouter } from 'vue-router'
import { authState, logout } from '../../auth'
import { consentLabel, labelOf, roleLabel } from '../../labels'

const router = useRouter()
async function exit() {
  await logout()
  router.push('/login')
}
</script>

<template>
  <div class="page">
    <h1>我的</h1>
    <div class="card stack">
      <div>{{ authState.user?.name }}</div>
      <div class="muted">{{ roleLabel[authState.user?.role] }} · {{ authState.user?.organizationName }}</div>
      <div class="muted">门店：{{ authState.user?.storeName || '未分配' }}</div>
      <div>录音授权：{{ labelOf(consentLabel, authState.user?.consentStatus) }}</div>
      <router-link to="/app/consent">查看授权记录</router-link>
    </div>
    <router-link v-if="authState.user?.role !== 'ADVISOR'" class="btn primary" to="/admin" style="text-align:center;text-decoration:none">进入管理端</router-link>
    <button class="btn ghost" @click="exit">退出登录</button>
  </div>
</template>
