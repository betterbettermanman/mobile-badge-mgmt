<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'
import DeviceSourceTag from '../../components/DeviceSourceTag.vue'
import { deviceStatusLabel, labelOf } from '../../labels'

const devices = ref([])
const error = ref('')
const message = ref('')

async function load() {
  devices.value = await api('/api/devices')
}

async function bind() {
  error.value = ''
  try {
    await api('/api/devices/bind', { method: 'POST', body: { source: 'phone', displayName: '本机' } })
    message.value = '已绑定本机为手机录音源'
    await load()
  } catch (err) {
    error.value = err.message
  }
}

async function unbind(id) {
  if (!window.confirm('解绑后需重新绑定才能使用手机录音。是否解绑？')) return
  await api(`/api/devices/${id}/unbind`, { method: 'POST' })
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>采集来源</h1>
    <div class="row">
      <DeviceSourceTag source="phone" />
      <span class="muted">可用</span>
      <DeviceSourceTag source="file" />
      <span class="muted">导入即用</span>
      <DeviceSourceTag source="badge" />
    </div>
    <p class="muted">真工牌采集将在二期开放，一期请使用手机录音或文件导入。这里不提供工牌绑定。</p>
    <p v-if="message" class="banner ok">{{ message }}</p>
    <p v-if="error" class="error-text">{{ error }}</p>
    <article v-for="item in devices" :key="item.id" class="card stack">
      <div class="row between">
        <strong>{{ item.displayName }}</strong>
        <DeviceSourceTag :source="item.source" />
      </div>
      <span class="muted">{{ labelOf(deviceStatusLabel, item.status) }}</span>
      <button v-if="item.status === 'bound'" class="btn ghost" @click="unbind(item.id)">解绑</button>
    </article>
    <button class="btn primary" @click="bind">绑定本机为手机录音源</button>
  </div>
</template>
