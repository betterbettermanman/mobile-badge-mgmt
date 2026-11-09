<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'
import { consentLabel, labelOf, VENDOR_SENTENCE } from '../../labels'

const status = ref(null)
const records = ref([])
const error = ref('')

onMounted(async () => {
  try {
    status.value = await api('/api/consents/status')
    records.value = await api('/api/consents')
  } catch (err) {
    error.value = err.message
  }
})
</script>

<template>
  <div class="page">
    <h1>录音授权</h1>
    <p v-if="error" class="error-text">{{ error }}</p>
    <div v-if="status" class="card">
      <div>当前状态：{{ labelOf(consentLabel, status.status) }}</div>
      <p class="muted">{{ status.message }}。无有效授权时不能开始录音。</p>
      <p class="muted">{{ VENDOR_SENTENCE }}。有效期内无需每次开录重新勾选。</p>
    </div>
    <article v-for="item in records" :key="item.id" class="item">
      <div>{{ labelOf(consentLabel, item.status) }} · {{ item.statement }}</div>
      <p class="muted">{{ item.grantedAt }}</p>
    </article>
    <p v-if="records.length === 0" class="muted">尚无授权记录。</p>
  </div>
</template>
