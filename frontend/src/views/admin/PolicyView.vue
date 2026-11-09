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
    message.value = '模板已同步点名：火山引擎（ASR）· 阿里云百炼（LLM）。'
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
        <span>每次会话重新确认（不启用，有效期内免重复勾选）</span>
      </label>
      <label class="field">
        <span>知情同意模板</span>
        <textarea :value="policy.statementTemplate" readonly />
      </label>
      <p class="muted">模板必须点名火山引擎（ASR）与阿里云百炼（LLM）。顾问端授权半屏引用这段说明。</p>
      <p v-if="message" class="banner ok">{{ message }}</p>
      <p v-if="error" class="error-text">{{ error }}</p>
      <button v-if="canEdit" class="btn primary" @click="save">保存</button>
    </div>
  </div>
</template>
