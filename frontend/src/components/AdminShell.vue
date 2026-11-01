<script setup>
import { useRoute } from 'vue-router'
import { authState } from '../auth'
import { roleLabel } from '../labels'

const route = useRoute()
function active(to) {
  if (to === '/admin') return route.path === '/admin'
  return route.path === to || route.path.startsWith(to + '/')
}

const groups = [
  {
    title: '组织',
    items: [
      { to: '/admin', label: '概览' },
      { to: '/admin/org', label: '组织与门店' },
      { to: '/admin/members', label: '成员与邀请' },
      { to: '/admin/roles', label: '角色权限' }
    ]
  },
  { title: '合规', items: [{ to: '/admin/consent-policy', label: '授权策略' }] },
  {
    title: '资产与业务',
    items: [
      { to: '/admin/devices', label: '设备' },
      { to: '/admin/sessions', label: '会话' }
    ]
  },
  {
    title: '集成',
    items: [
      { to: '/admin/export', label: '导出' },
      { to: '/admin/webhooks', label: 'Webhook' }
    ]
  }
]
</script>

<template>
  <div class="admin">
    <aside class="side">
      <strong>移动工牌管理</strong>
      <p class="muted" style="color:#b7d7cc">{{ authState.user?.name }} · {{ roleLabel[authState.user?.role] }}</p>
      <div v-for="group in groups" :key="group.title">
        <div class="group">{{ group.title }}</div>
        <router-link v-for="item in group.items" :key="item.to" :to="item.to" custom v-slot="{ href, navigate }">
          <a :href="href" :class="{ 'router-link-active': active(item.to) }" @click="navigate">{{ item.label }}</a>
        </router-link>
      </div>
      <router-link to="/app/sessions">进入顾问端</router-link>
    </aside>
    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>
