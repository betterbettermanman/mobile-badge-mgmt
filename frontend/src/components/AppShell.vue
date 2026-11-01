<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
const tabs = [
  { to: '/app/sessions', label: '会话', match: '/app/sessions' },
  { to: '/app/confirmed/profiles', label: '已确认', match: '/app/confirmed' },
  { to: '/app/devices', label: '设备', match: '/app/devices' },
  { to: '/app/me', label: '我的', match: '/app/me' }
]

function active(tab) {
  if (tab.match === '/app/sessions') {
    return route.path.startsWith('/app/sessions')
  }
  return route.path.startsWith(tab.match)
}
</script>

<template>
  <div class="app-frame">
    <div class="app-body">
      <router-view />
    </div>
    <nav class="tabbar">
      <router-link v-for="tab in tabs" :key="tab.to" :to="tab.to" :class="{ 'router-link-active': active(tab) }">
        {{ tab.label }}
      </router-link>
    </nav>
  </div>
</template>
