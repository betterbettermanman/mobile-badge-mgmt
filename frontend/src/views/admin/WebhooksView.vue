<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'
import { isAdmin } from '../../auth'

const canEdit = isAdmin()
const hooks = ref([])
const deliveries = ref([])
const error = ref('')
const message = ref('')
const form = ref({
  url: '',
  secret: '',
  profile: true,
  need: true,
  transcript: false
})

function events() {
  const list = []
  if (form.value.profile) list.push('profile.confirmed')
  if (form.value.need) list.push('need.confirmed')
  if (form.value.transcript) list.push('transcript.succeeded')
  return list
}

async function load() {
  if (!canEdit) return
  hooks.value = await api('/api/admin/webhooks')
  deliveries.value = await api('/api/admin/webhooks/deliveries')
}

async function create() {
  error.value = ''
  if (!form.value.url.startsWith('https://')) {
    error.value = '请输入合法的 HTTPS URL'
    return
  }
  try {
    await api('/api/admin/webhooks', {
      method: 'POST',
      body: { url: form.value.url, secret: form.value.secret, events: events(), enabled: true }
    })
    message.value = '已保存。一期不会向该地址发起真实请求。'
    await load()
  } catch (err) {
    error.value = err.message
  }
}

async function test(id) {
  const result = await api(`/api/admin/webhooks/${id}/test`, { method: 'POST' })
  message.value = result.detail
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>Webhook</h1>
    <div v-if="!canEdit" class="card">
      <h2>无权访问该页面</h2>
      <p>Webhook 仅管理员可配置。这里没有 CRM 字段映射。</p>
    </div>
    <template v-else>
      <p class="banner">一期只保存配置并记录测试事件，不向外部 URL 投递，也不做 CRM 双向同步。</p>
      <form class="card stack" @submit.prevent="create">
        <label class="field"><span>HTTPS URL</span><input v-model="form.url" placeholder="https://example.com/hooks" /></label>
        <label class="field"><span>密钥</span><input v-model="form.secret" placeholder="至少 8 位，保存后掩码显示" /></label>
        <label class="check"><input v-model="form.profile" type="checkbox" /> profile.confirmed</label>
        <label class="check"><input v-model="form.need" type="checkbox" /> need.confirmed</label>
        <label class="check"><input v-model="form.transcript" type="checkbox" /> transcript.succeeded</label>
        <p v-if="error" class="error-text">{{ error }}</p>
        <p v-if="message" class="banner ok">{{ message }}</p>
        <button class="btn primary">保存</button>
      </form>
      <article v-for="hook in hooks" :key="hook.id" class="card stack">
        <strong>{{ hook.url }}</strong>
        <span class="muted">密钥 {{ hook.secretMasked }} · {{ hook.events.join('、') }} · {{ hook.enabled ? '启用' : '停用' }}</span>
        <button class="btn ghost" @click="test(hook.id)">测试发送</button>
      </article>
      <h2>最近记录</h2>
      <article v-for="item in deliveries" :key="item.id" class="item">
        {{ item.eventName }} · {{ item.statusCode }} · {{ item.detail }}
      </article>
    </template>
  </div>
</template>
