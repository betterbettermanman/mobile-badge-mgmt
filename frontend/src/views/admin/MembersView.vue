<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'
import { isAdmin } from '../../auth'
import { roleLabel } from '../../labels'

const members = ref([])
const stores = ref([])
const error = ref('')
const form = ref({ account: '', name: '', password: '', role: 'ADVISOR', storeId: '' })
const canInvite = isAdmin()

async function load() {
  members.value = await api('/api/members')
  stores.value = await api('/api/stores')
}

async function create() {
  error.value = ''
  try {
    await api('/api/members', {
      method: 'POST',
      body: {
        account: form.value.account,
        name: form.value.name,
        password: form.value.password,
        role: form.value.role,
        storeId: form.value.storeId ? Number(form.value.storeId) : null
      }
    })
    form.value = { account: '', name: '', password: '', role: 'ADVISOR', storeId: '' }
    await load()
  } catch (err) {
    error.value = err.message
  }
}

async function disable(member) {
  if (!window.confirm('停用后该账号将无法登录。是否继续？')) return
  await api(`/api/members/${member.id}`, { method: 'PATCH', body: { enabled: false } })
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>成员与邀请</h1>
    <p v-if="!canInvite" class="banner">主管不能邀请成员或修改角色。</p>
    <p v-if="error" class="error-text">{{ error }}</p>
    <form v-if="canInvite" class="card stack" @submit.prevent="create">
      <p class="muted">一期由管理员直接创建账号并设置初始密码，不发送邮件。</p>
      <div class="row">
        <input v-model="form.account" placeholder="手机号 / 工号" />
        <input v-model="form.name" placeholder="姓名" />
        <input v-model="form.password" type="password" placeholder="初始密码，至少 6 位" />
      </div>
      <div class="row">
        <select v-model="form.role">
          <option value="ADVISOR">顾问</option>
          <option value="SUPERVISOR">主管</option>
          <option value="ADMIN">管理员</option>
        </select>
        <select v-model="form.storeId">
          <option value="">不指定门店</option>
          <option v-for="store in stores" :key="store.id" :value="store.id">{{ store.name }}</option>
        </select>
        <button class="btn primary">创建账号</button>
      </div>
    </form>
    <table>
      <thead>
        <tr><th>姓名</th><th>账号</th><th>角色</th><th>门店</th><th>状态</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="member in members" :key="member.id">
          <td>{{ member.name }}</td>
          <td>{{ member.account }}</td>
          <td>{{ roleLabel[member.role] }}</td>
          <td>{{ member.storeName || '—' }}</td>
          <td>{{ member.enabled ? '已启用' : '已停用' }}</td>
          <td>
            <button v-if="canInvite && member.enabled" class="btn ghost" @click="disable(member)">停用</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
