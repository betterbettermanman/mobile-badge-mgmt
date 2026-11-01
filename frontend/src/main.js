import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api'
import { authState } from './auth'
import './styles.css'

setUnauthorizedHandler(() => {
  authState.user = null
  const path = router.currentRoute.value.fullPath
  if (!path.startsWith('/login')) {
    router.push({ path: '/login', query: { redirect: path } })
  }
})

createApp(App).use(router).mount('#app')
