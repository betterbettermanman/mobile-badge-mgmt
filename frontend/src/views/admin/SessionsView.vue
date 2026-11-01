<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'
import DeviceSourceTag from '../../components/DeviceSourceTag.vue'
import { confirmLabel, labelOf, transcriptLabel } from '../../labels'

const items = ref([])
const error = ref('')
onMounted(async () => {
  try {
    const page = await api('/api/admin/sessions')
    items.value = page.items
  } catch (err) {
    error.value = err.message
  }
})
</script>

<template>
  <div class="page">
    <h1>组织会话</h1>
    <p class="muted">本组织会话，详情默认只读，不能代替顾问确认。</p>
    <p v-if="error" class="error-text">{{ error }}</p>
    <table>
      <thead>
        <tr><th>称呼</th><th>顾问</th><th>来源</th><th>转写</th><th>画像</th></tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td><router-link :to="`/admin/sessions/${item.id}`">{{ item.customerLabel || '未填写' }}</router-link></td>
          <td>{{ item.ownerName }}</td>
          <td><DeviceSourceTag :source="item.source" /></td>
          <td>{{ labelOf(transcriptLabel, item.transcriptStatus) }}</td>
          <td>{{ labelOf(confirmLabel, item.profileStatus) }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="items.length === 0" class="muted">尚无会话。</p>
  </div>
</template>
