<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'

const rows = ref([])
onMounted(async () => {
  rows.value = await api('/api/admin/roles')
})
</script>

<template>
  <div class="page">
    <h1>角色权限</h1>
    <p class="muted">一期只读矩阵。顾问仅本人，主管看本组织但不能邀请、改策略、导出或配置 Webhook，管理员管理组织成员。</p>
    <table>
      <thead>
        <tr><th>能力</th><th>顾问</th><th>主管</th><th>管理员</th></tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.capability">
          <td>{{ row.capability }}</td>
          <td>{{ row.advisor ? '✓' : '—' }}</td>
          <td>{{ row.supervisor ? '✓' : '—' }}</td>
          <td>{{ row.admin ? '✓' : '—' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
