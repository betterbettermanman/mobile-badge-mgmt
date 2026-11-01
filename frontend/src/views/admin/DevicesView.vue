<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'
import { isAdmin } from '../../auth'
import DeviceSourceTag from '../../components/DeviceSourceTag.vue'
import { deviceStatusLabel, labelOf } from '../../labels'

const source = ref('')
const devices = ref([])
const error = ref('')
const canDisable = isAdmin()

async function load() {
  const query = source.value && source.value !== 'badge' ? `?source=${source.value}` : ''
  devices.value = source.value === 'badge' ? [] : await api('/api/admin/devices' + query)
}

async function disable(item) {
  await api(`/api/admin/devices/${item.id}/disable`, { method: 'POST' })
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>组织设备</h1>
    <p class="muted">没有固件、OTA 或工牌配对。工牌类型一期通常为空。</p>
    <select v-model="source" @change="load">
      <option value="">全部可用类型</option>
      <option value="phone">手机</option>
      <option value="file">文件</option>
      <option value="badge">工牌（二期）</option>
    </select>
    <p v-if="source === 'badge'" class="banner">真工牌采集将在二期开放，当前没有可绑定的工牌。</p>
    <p v-if="error" class="error-text">{{ error }}</p>
    <table>
      <thead>
        <tr><th>名称</th><th>类型</th><th>顾问</th><th>状态</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="item in devices" :key="item.id">
          <td>{{ item.displayName }}</td>
          <td><DeviceSourceTag :source="item.source" /></td>
          <td>{{ item.userName }}</td>
          <td>{{ labelOf(deviceStatusLabel, item.status) }}</td>
          <td><button v-if="canDisable && item.status === 'bound'" class="btn ghost" @click="disable(item)">停用</button></td>
        </tr>
      </tbody>
    </table>
    <p v-if="devices.length === 0" class="muted">暂无设备。</p>
  </div>
</template>
