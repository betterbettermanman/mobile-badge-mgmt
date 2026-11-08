<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api'
import DeviceSourceTag from '../../components/DeviceSourceTag.vue'
import { VENDOR_SENTENCE } from '../../labels'

const router = useRouter()
const customerLabel = ref('')
const file = ref(null)
const acknowledged = ref(false)
const consentValid = ref(false)
const error = ref('')
const loading = ref(false)
const allowed = ['wav', 'mp3', 'm4a', 'aac']

onMounted(async () => {
  try {
    const status = await api('/api/consents/status')
    consentValid.value = status.status === 'valid'
  } catch {
    consentValid.value = false
  }
})

function onFile(event) {
  file.value = event.target.files?.[0] || null
  error.value = ''
}

async function submit() {
  error.value = ''
  if (!file.value) {
    error.value = '请选择音频文件'
    return
  }
  const ext = file.value.name.split('.').pop()?.toLowerCase()
  if (!allowed.includes(ext)) {
    error.value = '仅支持 wav / mp3 / m4a / aac'
    return
  }
  if (file.value.size > 200 * 1024 * 1024) {
    error.value = '文件超过大小限制（默认 200MB）'
    return
  }
  if (!consentValid.value && !acknowledged.value) {
    error.value = '请先确认客户已同意录音'
    return
  }
  const body = new FormData()
  body.append('file', file.value)
  body.append('customerLabel', customerLabel.value)
  body.append('consentAcknowledged', consentValid.value ? 'false' : 'true')
  loading.value = true
  try {
    const created = await api('/api/sessions/import', { method: 'POST', body })
    router.push(`/app/sessions/${created.id}`)
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <router-link to="/app/sessions">返回</router-link>
    <div class="row between">
      <h1>导入文件</h1>
      <DeviceSourceTag source="file" />
    </div>
    <p class="muted">来源固定为文件。工牌不能从这里提交。</p>
    <label class="field">
      <span>客户称呼 / 备注</span>
      <input v-model="customerLabel" maxlength="80" />
    </label>
    <label class="field">
      <span>音频文件</span>
      <input type="file" accept=".wav,.mp3,.m4a,.aac,audio/*" @change="onFile" />
    </label>
    <p v-if="consentValid" class="banner ok">已授权。有效期内无需重新勾选。{{ VENDOR_SENTENCE }}。</p>
    <template v-else>
      <div class="vendor-box">
        <p>{{ VENDOR_SENTENCE }}。</p>
        <p>草案须人工确认后才进入正式库。</p>
      </div>
      <label class="check">
        <input v-model="acknowledged" type="checkbox" />
        <span>我已向客户说明，并取得录音同意</span>
      </label>
    </template>
    <p v-if="error" class="error-text">{{ error }}</p>
    <button class="btn primary" :disabled="loading" @click="submit">{{ loading ? '上传中…' : '上传' }}</button>
  </div>
</template>
