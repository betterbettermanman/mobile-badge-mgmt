<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'
import { isAdmin } from '../../auth'

const policy = ref(null)
const days = ref(30)
const error = ref('')
const message = ref('')
const canEdit = isAdmin()

onMounted(async () => {
  policy.value = await api('/api/admin/consent-policy')
  days.value = policy.value.validityDays
})

async function save() {
  error.value = ''
  try {
    policy.value = await api('/api/admin/consent-policy', { method: 'PUT', body: { validityDays: Number(days.value) } })
    message.value = '已保存。策略仍是无授权禁止开录，将影响全体顾问开录。'
  } catch (err) {
    error.value = err.message
  }
}
</script>

<template>
  <div class="page">
    <h1>授权策略</h1>
    <p v-if="!canEdit" class="banner">主管只能查看策略，不能修改。</p>
    <div class="card stack" v-if="policy">
      <label class="choice on">
        <span><input type="radio" checked disabled /> 无授权禁止开录（默认且锁定）</span>
      </label>
      <label class="field">
        <span>授权有效期（天）</span>
        <input v-model="days" type="number" min="1" max="365" :disabled="!canEdit" />
      </label>
      <label class="check">
        <input type="checkbox" disabled />
        <span>每次会话重新确认（一期不启用）</span>
      </label>
      <p class="muted">「仅提醒不强制」不在一期开放。</p>
      <p v-if="message" class="banner ok">{{ message }}</p>
      <p v-if="error" class="error-text">{{ error }}</p>
      <button v-if="canEdit" class="btn primary" @click="save">保存</button>
    </div>
  </div>
</template>
