<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'
import { isAdmin } from '../../auth'

const org = ref(null)
const stores = ref([])
const name = ref('')
const storeName = ref('')
const error = ref('')
const message = ref('')
const canEdit = isAdmin()

async function load() {
  org.value = await api('/api/org')
  name.value = org.value.name
  stores.value = await api('/api/stores')
}

async function saveOrg() {
  try {
    org.value = await api('/api/org', { method: 'PUT', body: { name: name.value } })
    message.value = '组织名称已更新'
  } catch (err) {
    error.value = err.message
  }
}

async function addStore() {
  if (!storeName.value.trim()) {
    error.value = '请输入门店名称'
    return
  }
  await api('/api/stores', { method: 'POST', body: { name: storeName.value.trim() } })
  storeName.value = ''
  await load()
}

async function toggle(store) {
  await api(`/api/stores/${store.id}`, { method: 'PATCH', body: { name: store.name, enabled: !store.enabled } })
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>组织与门店</h1>
    <p v-if="!canEdit" class="banner">主管只能查看组织和门店。</p>
    <p v-if="error" class="error-text">{{ error }}</p>
    <p v-if="message" class="banner ok">{{ message }}</p>
    <div class="card stack" v-if="org">
      <label class="field">
        <span>组织名称</span>
        <input v-model="name" :disabled="!canEdit" />
      </label>
      <button v-if="canEdit" class="btn primary" @click="saveOrg">保存</button>
    </div>
    <article v-for="store in stores" :key="store.id" class="item row between">
      <span>{{ store.name }} · {{ store.enabled ? '启用' : '停用' }}</span>
      <button v-if="canEdit" class="btn ghost" @click="toggle(store)">{{ store.enabled ? '停用' : '启用' }}</button>
    </article>
    <div v-if="canEdit" class="row">
      <input v-model="storeName" maxlength="50" placeholder="新门店名称" />
      <button class="btn" @click="addStore">新增门店</button>
    </div>
  </div>
</template>
