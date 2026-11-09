<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'
import { isAdmin } from '../../auth'

const canEdit = isAdmin()
const hooks = ref([])
const deliveries = ref([])
const policy = ref({ allowlist: '', outboundEnabled: false, hint: '' })
const error = ref('')
const message = ref('')
const form = ref({
  url: '',
  secret: '',
  profile: true,
  need: true,
  enabled: true
})

function events() {
  const list = []
  if (form.value.profile) list.push('profile.confirmed')
  if (form.value.need) list.push('need.confirmed')
  return list
}

async function load() {
  if (!canEdit) return
  policy.value = await api('/api/admin/webhooks/policy')
  hooks.value = await api('/api/admin/webhooks')
  deliveries.value = await api('/api/admin/webhooks/deliveries')
}

async function savePolicy() {
  error.value = ''
  try {
    policy.value = await api('/api/admin/webhooks/policy', {
      method: 'PUT',
      body: { allowlist: policy.value.allowlist, outboundEnabled: policy.value.outboundEnabled }
    })
    message.value = policy.value.allowlist?.trim()
      ? '出站策略已保存。'
      : '已保存配置。注意：白名单为空，运行时仍不会出站。'
  } catch (err) {
    error.value = err.message
  }
}

async function create() {
  error.value = ''
  message.value = ''
  const url = form.value.url.trim()
  if (url.startsWith('http://')) {
    error.value = '禁止使用 http://，请改用 HTTPS。'
    return
  }
  if (!url.startsWith('https://')) {
    error.value = '请输入合法的 HTTPS URL'
    return
  }
  if (!form.value.secret || form.value.secret.trim().length < 8) {
    error.value = '密钥至少 8 位'
    return
  }
  if (events().length === 0) {
    error.value = '请选择订阅事件'
    return
  }
  try {
    const saved = await api('/api/admin/webhooks', {
      method: 'POST',
      body: { url, secret: form.value.secret, events: events(), enabled: form.value.enabled }
    })
    form.value.secret = ''
    message.value = saved.notice || '已保存。密钥已脱敏回显，明文不会再次展示。'
    await load()
  } catch (err) {
    error.value = err.message
  }
}

function stateLabel(item) {
  if (item.state === 'succeeded') return `成功 ${item.statusCode}`
  if (item.state === 'retrying') return `重试中（第 ${item.attempt} 次${item.nextRetryAt ? '，下次 ' + item.nextRetryAt : ''}）`
  if (item.state === 'failed') return '失败'
  if (item.state === 'skipped') return '未出站'
  return item.state || String(item.statusCode)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>Webhook</h1>
    <div v-if="!canEdit" class="card">
      <h2>无权访问该页面</h2>
      <p>Webhook 仅管理员可配置。</p>
    </div>
    <template v-else>
      <p class="banner">二期受控出站：仅 HTTPS · 域名白名单 · HMAC · 默认仅确认类事件。白名单为空 = 不出站。</p>
      <div class="banner danger">
        火山引擎 ASR、阿里云百炼 LLM 的 API Key 禁止在本页配置，由运维注入环境变量 / 容器 secret。本页仅可粘贴 Webhook HMAC Secret（保存后脱敏回显）。
      </div>
      <form class="card stack" @submit.prevent="savePolicy">
        <h2>出站策略</h2>
        <label class="field">
          <span>域名白名单（逗号分隔）</span>
          <input v-model="policy.allowlist" placeholder="hooks.example.com" />
        </label>
        <p class="muted">{{ policy.hint || '当前白名单为空：即使启用也不会向外部投递。' }}</p>
        <label class="check">
          <input v-model="policy.outboundEnabled" type="checkbox" />
          <span>启用出站（仍受白名单约束）</span>
        </label>
        <button class="btn" type="submit">保存出站策略</button>
      </form>
      <form class="card stack" @submit.prevent="create">
        <h2>端点</h2>
        <label class="field"><span>HTTPS URL</span><input v-model="form.url" placeholder="https://example.com/hooks" /></label>
        <label class="field"><span>HMAC Secret</span><input v-model="form.secret" type="password" autocomplete="new-password" placeholder="至少 8 位，保存后脱敏" /></label>
        <label class="check"><input v-model="form.profile" type="checkbox" /> profile.confirmed</label>
        <label class="check"><input v-model="form.need" type="checkbox" /> need.confirmed</label>
        <label class="check"><input type="checkbox" disabled /> transcript.succeeded（默认关闭，防止转写明文外泄）</label>
        <label class="check"><input v-model="form.enabled" type="checkbox" /> 启用此端点</label>
        <p v-if="error" class="error-text">{{ error }}</p>
        <p v-if="message" class="banner ok">{{ message }}</p>
        <button class="btn primary" type="submit">保存</button>
      </form>
      <article v-for="hook in hooks" :key="hook.id" class="card stack">
        <strong>{{ hook.url }}</strong>
        <span class="muted">密钥 {{ hook.secretMasked }} · {{ hook.events.join('、') }} · {{ hook.enabled ? '启用' : '停用' }}</span>
      </article>
      <h2>投递日志（只读 · 自动重试，无手动重投）</h2>
      <table v-if="deliveries.length">
        <thead>
          <tr><th>时间</th><th>事件</th><th>状态</th><th>详情</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in deliveries" :key="item.id">
            <td>{{ item.createdAt }}</td>
            <td>{{ item.eventName }}</td>
            <td>{{ stateLabel(item) }}</td>
            <td>{{ item.detail }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">尚无投递记录。</p>
    </template>
  </div>
</template>
