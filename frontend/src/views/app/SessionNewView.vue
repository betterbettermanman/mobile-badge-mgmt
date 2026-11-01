<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api'
import ConsentSheet from '../../components/ConsentSheet.vue'
import { consentLabel } from '../../labels'

const router = useRouter()
const customerLabel = ref('')
const source = ref('phone')
const error = ref('')
const loading = ref(false)
const sheet = ref(false)
const consent = ref({ status: 'missing', message: '未授权' })
const sessionId = ref(null)
const recording = ref(false)
const seconds = ref(0)
let timer = 0

const micHint = typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia
  ? '当前浏览器无法录音，请改用导入文件'
  : ''

function choose(next) {
  if (next === 'badge') return
  source.value = next
  if (next === 'file') router.push('/app/sessions/import')
}

async function ensureSession() {
  if (sessionId.value) return sessionId.value
  const created = await api('/api/sessions', {
    method: 'POST',
    body: { customerLabel: customerLabel.value, source: 'phone' }
  })
  sessionId.value = created.id
  return created.id
}

async function start() {
  if (source.value === 'badge') {
    error.value = '真工牌采集将在二期开放'
    return
  }
  error.value = ''
  loading.value = true
  try {
    consent.value = await api('/api/consents/status')
    if (consent.value.status !== 'valid') {
      sheet.value = true
      return
    }
    await beginRecording()
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function recordConsent() {
  loading.value = true
  try {
    const id = await ensureSession()
    consent.value = await api('/api/consents', {
      method: 'POST',
      body: { acknowledged: true, sessionId: id }
    })
    sheet.value = false
    await beginRecording()
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function beginRecording() {
  const id = await ensureSession()
  try {
    await api(`/api/sessions/${id}/recording/start`, { method: 'POST' })
    recording.value = true
    seconds.value = 0
    timer = window.setInterval(() => { seconds.value += 1 }, 1000)
  } catch (err) {
    if (['NO_CONSENT', 'CONSENT_EXPIRED', 'CONSENT_REVOKED'].includes(err.code)) {
      consent.value = { status: err.code === 'CONSENT_EXPIRED' ? 'expired' : err.code === 'CONSENT_REVOKED' ? 'revoked' : 'missing', message: err.message }
      sheet.value = true
      return
    }
    throw err
  }
}

async function stop() {
  window.clearInterval(timer)
  loading.value = true
  try {
    await api(`/api/sessions/${sessionId.value}/recording/stop`, { method: 'POST' })
    router.push(`/app/sessions/${sessionId.value}?tab=overview`)
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function cancel() {
  if (!window.confirm('放弃本次录音？')) return
  window.clearInterval(timer)
  await api(`/api/sessions/${sessionId.value}/recording/cancel`, { method: 'POST' })
  router.push('/app/sessions')
}

function clock(total) {
  const h = String(Math.floor(total / 3600)).padStart(2, '0')
  const m = String(Math.floor((total % 3600) / 60)).padStart(2, '0')
  const s = String(total % 60).padStart(2, '0')
  return `${h}:${m}:${s}`
}

onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <div class="page">
    <router-link to="/app/sessions">返回</router-link>
    <h1>新建会话</h1>
    <div v-if="!recording" class="stack">
      <label class="field">
        <span>客户称呼 / 备注</span>
        <input v-model="customerLabel" maxlength="80" placeholder="选填，建议填写" />
      </label>
      <div class="stack">
        <label class="choice" :class="{ on: source === 'phone' }">
          <span><input type="radio" value="phone" :checked="source === 'phone'" @change="choose('phone')" /> 手机录音</span>
          <span class="tag">手机</span>
        </label>
        <label class="choice" :class="{ on: source === 'file' }">
          <span><input type="radio" value="file" @change="choose('file')" /> 导入文件</span>
          <span class="tag">文件</span>
        </label>
        <label class="choice disabled" title="真工牌采集将在二期开放">
          <span><input type="radio" value="badge" disabled /> 工牌</span>
          <span class="tag off">二期</span>
        </label>
      </div>
      <p class="muted">真工牌采集将在二期开放，一期请使用手机录音或文件导入。</p>
      <p v-if="micHint" class="banner">{{ micHint }}</p>
      <p v-if="error" class="error-text">{{ error }}</p>
      <button class="btn primary" :disabled="loading || source === 'badge'" @click="start">
        {{ loading ? '处理中…' : '开始录音' }}
      </button>
    </div>
    <div v-else class="card stack" style="text-align:center">
      <div>录音中</div>
      <div class="timer">{{ clock(seconds) }}</div>
      <p class="muted">来源：手机 · 授权：{{ consentLabel[consent.status] || '已授权' }}</p>
      <p class="muted">一期录音字节为状态机联调，结束并上传后由 Mock ASR 生成转写。</p>
      <div class="row" style="justify-content:center">
        <button class="btn primary" :disabled="loading" @click="stop">结束并上传</button>
        <button class="btn ghost" @click="cancel">取消</button>
      </div>
      <p v-if="error" class="error-text">{{ error }}</p>
    </div>
    <ConsentSheet :open="sheet" :status="consent.status" :message="consent.message" @close="sheet = false" @submit="recordConsent" />
  </div>
</template>
