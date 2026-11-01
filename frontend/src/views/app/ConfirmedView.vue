<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api'
import { confirmLabel, followLabel, labelOf } from '../../labels'

const props = defineProps({
  kind: { type: String, default: 'profiles' }
})
const router = useRouter()
const q = ref('')
const items = ref([])
const error = ref('')

async function load() {
  error.value = ''
  const path = props.kind === 'needs' ? '/api/confirmed/needs' : '/api/confirmed/profiles'
  const query = q.value.trim() ? `?q=${encodeURIComponent(q.value.trim())}` : ''
  try {
    const rows = await api(path + query)
    items.value = rows.filter((item) => item.confirmationStatus === 'confirmed')
  } catch (err) {
    error.value = err.message
  }
}

watch(() => props.kind, load)
onMounted(load)
</script>

<template>
  <div class="page">
    <h1>已确认</h1>
    <div class="tabs">
      <button class="chip" :class="{ on: kind === 'profiles' }" @click="router.push('/app/confirmed/profiles')">画像</button>
      <button class="chip" :class="{ on: kind === 'needs' }" @click="router.push('/app/confirmed/needs')">需求</button>
    </div>
    <p class="muted">只显示已确认数据。草案留在会话里，不会出现在这里。</p>
    <div class="row">
      <input v-model="q" placeholder="搜索已确认内容" @keyup.enter="load" />
      <button class="btn" @click="load">搜索</button>
    </div>
    <p v-if="error" class="error-text">{{ error }}</p>
    <div v-if="items.length === 0" class="card">暂无已确认数据，请在会话中确认草案</div>
    <router-link v-for="item in items" :key="item.id" class="item" :to="`/app/sessions/${item.sessionId}?tab=${kind === 'needs' ? 'needs' : 'profile'}`">
      <strong>{{ kind === 'needs' ? item.title : item.appellation }}</strong>
      <p class="muted">
        {{ kind === 'needs' ? item.detail : item.intent }}
        · {{ labelOf(confirmLabel, item.confirmationStatus) }}
        <template v-if="kind === 'needs'"> · {{ labelOf(followLabel, item.followStatus) }}</template>
      </p>
    </router-link>
  </div>
</template>
