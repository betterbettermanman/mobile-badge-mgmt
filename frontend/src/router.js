import { createRouter, createWebHistory } from 'vue-router'
import { authState, homePath, refreshMe } from './auth'
import LoginView from './views/LoginView.vue'
import ForbiddenView from './views/ForbiddenView.vue'
import AppShell from './components/AppShell.vue'
import AdminShell from './components/AdminShell.vue'
import SessionsView from './views/app/SessionsView.vue'
import SessionNewView from './views/app/SessionNewView.vue'
import SessionImportView from './views/app/SessionImportView.vue'
import SessionDetailView from './views/app/SessionDetailView.vue'
import ConsentView from './views/app/ConsentView.vue'
import DevicesView from './views/app/DevicesView.vue'
import ConfirmedView from './views/app/ConfirmedView.vue'
import MeView from './views/app/MeView.vue'
import OverviewView from './views/admin/OverviewView.vue'
import OrgView from './views/admin/OrgView.vue'
import MembersView from './views/admin/MembersView.vue'
import RolesView from './views/admin/RolesView.vue'
import PolicyView from './views/admin/PolicyView.vue'
import AdminDevicesView from './views/admin/DevicesView.vue'
import AdminSessionsView from './views/admin/SessionsView.vue'
import ExportView from './views/admin/ExportView.vue'
import WebhooksView from './views/admin/WebhooksView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/forbidden', component: ForbiddenView },
    {
      path: '/app',
      component: AppShell,
      children: [
        { path: '', redirect: '/app/sessions' },
        { path: 'sessions', component: SessionsView },
        { path: 'sessions/new', component: SessionNewView },
        { path: 'sessions/import', component: SessionImportView },
        { path: 'sessions/:id', component: SessionDetailView },
        { path: 'consent', component: ConsentView },
        { path: 'devices', component: DevicesView },
        { path: 'confirmed/profiles', component: ConfirmedView, props: { kind: 'profiles' } },
        { path: 'confirmed/needs', component: ConfirmedView, props: { kind: 'needs' } },
        { path: 'me', component: MeView }
      ]
    },
    {
      path: '/admin',
      component: AdminShell,
      children: [
        { path: '', component: OverviewView },
        { path: 'org', component: OrgView },
        { path: 'members', component: MembersView },
        { path: 'roles', component: RolesView },
        { path: 'consent-policy', component: PolicyView },
        { path: 'devices', component: AdminDevicesView },
        { path: 'sessions', component: AdminSessionsView },
        { path: 'sessions/:id', component: SessionDetailView },
        { path: 'export', component: ExportView },
        { path: 'webhooks', component: WebhooksView }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/login' }
  ]
})

router.beforeEach(async (to) => {
  if (!authState.ready) {
    try {
      await refreshMe()
    } catch {
      authState.user = null
      authState.ready = true
    }
  }
  if (to.path === '/login' && authState.user) {
    return homePath(authState.user)
  }
  if (to.meta.public) return true
  if (!authState.user) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path.startsWith('/admin') && authState.user.role === 'ADVISOR') {
    return '/forbidden'
  }
  return true
})

export default router
