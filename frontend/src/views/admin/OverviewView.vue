<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'
import { isAdmin } from '../../auth'

const stats = ref(null)
const error = ref('')
onMounted(async () => {
  try {
    stats.value = await api('/api/admin/overview')
  } catch (err) {
    error.value = err.message
  }
})
</script>

<template>
  <div class="page">
    <h1>组织概览</h1>
    <p class="muted">轻量计数，不是经营看板。</p>
    <p v-if="error" class="error-text">{{ error }}</p>
    <div v-if="stats" class="stats">
      <div class="card stat"><span class="muted">成员数</span><strong>{{ stats.memberCount }}</strong></div>
      <div class="card stat"><span class="muted">今日会话</span><strong>{{ stats.todaySessionCount }}</strong></div>
      <div class="card stat"><span class="muted">转写失败</span><strong>{{ stats.transcriptFailedCount }}</strong></div>
      <div class="card stat"><span class="muted">待确认草案</span><strong>{{ stats.pendingDraftCount }}</strong></div>
      <div class="card stat"><span class="muted">已确认画像</span><strong>{{ stats.confirmedProfileCount }}</strong></div>
      <div class="card stat"><span class="muted">已绑定设备</span><strong>{{ stats.boundDeviceCount }}</strong></div>
    </div>
    <div class="row" v-if="isAdmin()">
      <router-link class="btn primary" to="/admin/members">邀请成员</router-link>
      <router-link class="btn" to="/admin/consent-policy">授权策略</router-link>
      <router-link class="btn" to="/admin/export">导出</router-link>
    </div>
  </div>
</template>
