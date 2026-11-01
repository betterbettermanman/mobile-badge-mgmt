<script setup>
import { onMounted, ref, watch } from 'vue'
import { api } from '../../api'
import DeviceSourceTag from '../../components/DeviceSourceTag.vue'
import { captureLabel, confirmLabel, labelOf, transcriptLabel } from '../../labels'

const filters = [
  { key: 'all', label: '全部' },
  { key: 'recording', label: '录音中' },
  { key: 'processing', label: '转写中' },
  { key: 'failed', label: '失败' },
  { key: 'done', label: '已完成' }
]
const filter = ref('all')
const source = ref('')
const page = ref(0)
const data = ref({ items: [], total: 0, size: 20 })
const error = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  const params = new URLSearchParams({ page: String(page.value), size: '20' })
  if (source.value === 'phone' || source.value === 'file') params.set('source', source.value)
  if (filter.value === 'recording') params.set('captureStatus', 'recording')
  if (filter.value === 'processing') params.set('transcriptStatus', 'processing')
  if (filter.value === 'failed') params.set('transcriptStatus', 'failed')
  if (filter.value === 'done') params.set('transcriptStatus', 'succeeded')
  try {
    data.value = await api('/api/sessions?' + params.toString())
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

watch([filter, source], () => {
  page.value = 0
  load()
})
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="topbar">
      <h1>会话</h1>
      <div class="row">
        <router-link class="btn primary" to="/app/sessions/new">新建</router-link>
        <router-link class="btn ghost" to="/app/sessions/import">导入</router-link>
      </div>
    </div>
    <div class="chips">
      <button v-for="item in filters" :key="item.key" class="chip" :class="{ on: filter === item.key }" @click="filter = item.key">
        {{ item.label }}
      </button>
    </div>
    <label class="field">
      <span>采集来源</span>
      <select v-model="source">
        <option value="">手机 / 文件</option>
        <option value="phone">手机</option>
        <option value="file">文件</option>
        <option value="badge" disabled>工牌（二期，不可筛选提交）</option>
      </select>
    </label>
    <p v-if="loading" class="muted">加载中…</p>
    <p v-if="error" class="error-text">{{ error }}</p>
    <div v-if="!loading && data.items.length === 0" class="card">
      <p>尚无会话，点击下方新建</p>
      <router-link class="btn primary" to="/app/sessions/new">新建会话</router-link>
    </div>
    <div class="list">
      <router-link v-for="item in data.items" :key="item.id" class="item" :to="`/app/sessions/${item.id}`">
        <strong>{{ item.customerLabel || '未填写称呼' }}</strong>
        <div class="row" style="margin-top:8px">
          <DeviceSourceTag :source="item.source" />
          <span class="tag neutral">{{ labelOf(captureLabel, item.captureStatus) }}</span>
          <span v-if="item.transcriptStatus" class="tag" :class="item.transcriptStatus === 'failed' ? 'bad' : ''">
            {{ labelOf(transcriptLabel, item.transcriptStatus) }}
          </span>
          <span v-if="item.profileStatus" class="tag" :class="item.profileStatus === 'draft' ? 'warn' : ''">
            画像{{ labelOf(confirmLabel, item.profileStatus) }}
          </span>
        </div>
      </router-link>
    </div>
    <div class="row between" v-if="data.total > data.size">
      <button class="btn ghost" :disabled="page === 0" @click="page--; load()">上一页</button>
      <span class="muted">{{ page + 1 }}</span>
      <button class="btn ghost" :disabled="(page + 1) * data.size >= data.total" @click="page++; load()">下一页</button>
    </div>
  </div>
</template>
