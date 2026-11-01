<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'
import { isAdmin } from '../../auth'
import { jobLabel, labelOf } from '../../labels'

const canExport = isAdmin()
const jobs = ref([])
const error = ref('')
const form = ref({
  from: '',
  to: '',
  includeConfirmedProfiles: true,
  includeConfirmedNeeds: true,
  includeSessionMeta: false,
  includeTranscript: false,
  format: 'JSON'
})

async function load() {
  if (!canExport) return
  jobs.value = await api('/api/admin/exports')
}

async function create() {
  error.value = ''
  if (form.value.from && form.value.to && form.value.to < form.value.from) {
    error.value = '结束时间需不早于开始时间'
    return
  }
  try {
    await api('/api/admin/exports', {
      method: 'POST',
      body: {
        from: form.value.from || null,
        to: form.value.to || null,
        includeConfirmedProfiles: form.value.includeConfirmedProfiles,
        includeConfirmedNeeds: form.value.includeConfirmedNeeds,
        includeSessionMeta: form.value.includeSessionMeta,
        includeTranscript: form.value.includeTranscript,
        format: form.value.format
      }
    })
    await load()
  } catch (err) {
    error.value = err.message
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>导出</h1>
    <div v-if="!canExport" class="card">
      <h2>无权访问该页面</h2>
      <p>导出仅管理员可操作。主管不能触发或下载导出。</p>
    </div>
    <template v-else>
      <div class="card stack">
        <p class="banner">默认仅导出已确认数据，不包含草案。</p>
        <div class="row">
          <label class="field"><span>从</span><input v-model="form.from" type="date" /></label>
          <label class="field"><span>到</span><input v-model="form.to" type="date" /></label>
        </div>
        <label class="check"><input v-model="form.includeConfirmedProfiles" type="checkbox" /> 已确认画像</label>
        <label class="check"><input v-model="form.includeConfirmedNeeds" type="checkbox" /> 已确认需求</label>
        <label class="check"><input v-model="form.includeSessionMeta" type="checkbox" /> 会话元数据</label>
        <label class="check"><input v-model="form.includeTranscript" type="checkbox" /> 转写全文（含敏感内容）</label>
        <div class="row">
          <label class="check"><input v-model="form.format" type="radio" value="CSV" /> CSV</label>
          <label class="check"><input v-model="form.format" type="radio" value="JSON" /> JSON</label>
        </div>
        <p v-if="error" class="error-text">{{ error }}</p>
        <button class="btn primary" @click="create">生成导出</button>
      </div>
      <table>
        <thead><tr><th>状态</th><th>格式</th><th>说明</th><th></th></tr></thead>
        <tbody>
          <tr v-for="job in jobs" :key="job.id">
            <td>{{ labelOf(jobLabel, job.status) }}</td>
            <td>{{ job.format }}</td>
            <td>{{ job.message }}</td>
            <td><a v-if="job.downloadPath" :href="job.downloadPath">下载</a></td>
          </tr>
        </tbody>
      </table>
    </template>
  </div>
</template>
