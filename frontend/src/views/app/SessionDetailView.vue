<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../../api'
import { authState } from '../../auth'
import DeviceSourceTag from '../../components/DeviceSourceTag.vue'
import { captureLabel, confirmLabel, consentLabel, followLabel, labelOf, transcriptLabel } from '../../labels'

const route = useRoute()
const tab = ref(route.query.tab || 'overview')
const session = ref(null)
const transcript = ref(null)
const profile = ref(null)
const needs = ref([])
const needStatus = ref('')
const error = ref('')
const message = ref('')
const loading = ref(false)
const form = ref({})
const needTitle = ref('')
const needDetail = ref('')

const readonly = computed(() => route.path.startsWith('/admin') || session.value?.ownerUserId !== authState.user?.id)
const id = computed(() => route.params.id)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const base = route.path.startsWith('/admin') ? '/api/admin/sessions/' : '/api/sessions/'
    session.value = await api(base + id.value)
    transcript.value = await api(`/api/sessions/${id.value}/transcript`)
    profile.value = await api(`/api/sessions/${id.value}/profile`)
    form.value = {
      appellation: profile.value.appellation || '',
      intent: profile.value.intent || '',
      budget: profile.value.budget || '',
      decisionMaker: profile.value.decisionMaker || '',
      notes: profile.value.notes || ''
    }
    await loadNeeds()
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function loadNeeds() {
  const query = needStatus.value ? `?status=${needStatus.value}` : ''
  const rows = await api(`/api/sessions/${id.value}/needs${query}`)
  needs.value = needStatus.value === 'confirmed'
    ? rows.filter((item) => item.confirmationStatus === 'confirmed')
    : rows
}

async function saveProfile() {
  message.value = ''
  profile.value = await api(`/api/sessions/${id.value}/profile`, { method: 'PUT', body: form.value })
  message.value = '草案已保存（尚未确认入库）'
}

async function generateProfile() {
  try {
    profile.value = await api(`/api/sessions/${id.value}/profile/generate`, { method: 'POST' })
    form.value = {
      appellation: profile.value.appellation || '',
      intent: profile.value.intent || '',
      budget: profile.value.budget || '',
      decisionMaker: profile.value.decisionMaker || '',
      notes: profile.value.notes || ''
    }
    message.value = '已生成草案，确认前不会出现在已确认视图'
  } catch (err) {
    error.value = err.message
  }
}

async function confirmProfile() {
  if (!window.confirm('确认后将进入已确认画像，可被检索与导出。是否确认？')) return
  try {
    profile.value = await api(`/api/sessions/${id.value}/profile/confirm`, { method: 'POST' })
    message.value = '已确认入库'
  } catch (err) {
    error.value = err.message
  }
}

async function unlockProfile() {
  if (!window.confirm('解锁后将退回草案，已确认列表不再显示。是否继续？')) return
  profile.value = await api(`/api/sessions/${id.value}/profile/unlock`, { method: 'POST' })
  message.value = '已退回草案'
}

async function rerun() {
  if (!window.confirm('将重新提交 Mock ASR。是否继续？')) return
  session.value = await api(`/api/sessions/${id.value}/transcript/rerun`, { method: 'POST' })
  transcript.value = await api(`/api/sessions/${id.value}/transcript`)
}

async function addNeed() {
  if (!needTitle.value.trim()) return
  await api(`/api/sessions/${id.value}/needs`, {
    method: 'POST',
    body: { title: needTitle.value, detail: needDetail.value }
  })
  needTitle.value = ''
  needDetail.value = ''
  message.value = '草案已保存（尚未确认入库）'
  await loadNeeds()
}

async function confirmNeed(needId) {
  if (!window.confirm('确认后该需求进入已确认列表，可被检索与导出。是否确认？')) return
  await api(`/api/sessions/${id.value}/needs/${needId}/confirm`, { method: 'POST' })
  message.value = '已确认入库'
  await loadNeeds()
}

async function unlockNeed(needId) {
  await api(`/api/sessions/${id.value}/needs/${needId}/unlock`, { method: 'POST' })
  await loadNeeds()
}

watch(needStatus, loadNeeds)
watch(() => route.params.id, load)
onMounted(load)
</script>

<template>
  <div class="page">
    <router-link :to="route.path.startsWith('/admin') ? '/admin/sessions' : '/app/sessions'">返回</router-link>
    <p v-if="loading" class="muted">加载中…</p>
    <p v-if="error" class="error-text">{{ error }}</p>
    <template v-if="session">
      <div class="row between">
        <h1>{{ session.customerLabel || '会话详情' }}</h1>
        <DeviceSourceTag :source="session.source" />
      </div>
      <div class="row">
        <span class="tag">授权{{ labelOf(consentLabel, session.consentStatus) }}</span>
        <span class="tag neutral">上传{{ labelOf(captureLabel, session.captureStatus) }}</span>
        <span class="tag">转写{{ labelOf(transcriptLabel, session.transcriptStatus) }}</span>
      </div>
      <div class="tabs">
        <button class="chip" :class="{ on: tab === 'overview' }" @click="tab = 'overview'">概览</button>
        <button class="chip" :class="{ on: tab === 'transcript' }" @click="tab = 'transcript'">转写</button>
        <button class="chip" :class="{ on: tab === 'profile' }" @click="tab = 'profile'">画像</button>
        <button class="chip" :class="{ on: tab === 'needs' }" @click="tab = 'needs'">需求</button>
      </div>
      <p v-if="message" class="banner ok">{{ message }}</p>
      <p v-if="readonly" class="banner">当前为只读。主管和管理员不能代确认他人画像或需求。</p>

      <section v-if="tab === 'overview'" class="card stack">
        <div>顾问：{{ session.ownerName }}</div>
        <div>门店：{{ session.storeName || '—' }}</div>
        <p v-if="session.failureReason" class="error-text">{{ session.failureReason }}</p>
        <button v-if="!readonly && session.transcriptStatus === 'failed'" class="btn" @click="rerun">重新转写</button>
      </section>

      <section v-if="tab === 'transcript'" class="card stack">
        <div class="row between">
          <strong>{{ labelOf(transcriptLabel, transcript?.status) }}</strong>
          <button v-if="!readonly && session.captureStatus === 'uploaded'" class="btn ghost" @click="rerun">重新转写</button>
        </div>
        <p class="muted">{{ transcript?.note }}</p>
        <p v-if="transcript?.fullText" style="white-space:pre-wrap">{{ transcript.fullText }}</p>
        <div v-for="(line, index) in transcript?.sentences || []" :key="index" class="item">
          {{ line.speaker === 'advisor' ? '顾问' : line.speaker === 'customer' ? '客户' : '未知' }}：{{ line.text }}
        </div>
        <p class="muted">一期不提供分句角色手动纠正。</p>
      </section>

      <section v-if="tab === 'profile'" class="card stack">
        <span class="tag" :class="profile?.confirmationStatus === 'confirmed' ? '' : 'warn'">
          {{ profile?.confirmationStatus ? labelOf(confirmLabel, profile.confirmationStatus) : '未生成' }}
        </span>
        <p class="muted">{{ profile?.hint }}</p>
        <button v-if="!readonly && profile?.editable" class="btn ghost" @click="generateProfile">由转写生成草案</button>
        <label class="field"><span>称呼</span><input v-model="form.appellation" :disabled="readonly || !profile?.editable" /></label>
        <label class="field"><span>意向</span><input v-model="form.intent" :disabled="readonly || !profile?.editable" /></label>
        <label class="field"><span>预算</span><input v-model="form.budget" :disabled="readonly || !profile?.editable" /></label>
        <label class="field"><span>决策人</span><input v-model="form.decisionMaker" :disabled="readonly || !profile?.editable" /></label>
        <label class="field"><span>备注</span><textarea v-model="form.notes" :disabled="readonly || !profile?.editable" /></label>
        <div v-if="!readonly" class="row">
          <button v-if="profile?.editable" class="btn" @click="saveProfile">保存草案</button>
          <button v-if="profile?.editable" class="btn primary" @click="confirmProfile">确认入库</button>
          <button v-if="profile?.confirmationStatus === 'confirmed'" class="btn ghost" @click="unlockProfile">解锁再编辑</button>
        </div>
      </section>

      <section v-if="tab === 'needs'" class="stack">
        <div class="chips">
          <button class="chip" :class="{ on: needStatus === '' }" @click="needStatus = ''">全部</button>
          <button class="chip" :class="{ on: needStatus === 'draft' }" @click="needStatus = 'draft'">草案</button>
          <button class="chip" :class="{ on: needStatus === 'confirmed' }" @click="needStatus = 'confirmed'">已确认</button>
        </div>
        <p class="muted">已确认子列表不含草案。</p>
        <div v-if="!readonly" class="card stack">
          <input v-model="needTitle" placeholder="需求标题" />
          <input v-model="needDetail" placeholder="补充说明" />
          <button class="btn" @click="addNeed">保存草案</button>
        </div>
        <article v-for="item in needs" :key="item.id" class="card stack">
          <div class="row between">
            <strong>{{ item.title }}</strong>
            <span class="tag" :class="item.confirmationStatus === 'draft' ? 'warn' : ''">{{ labelOf(confirmLabel, item.confirmationStatus) }}</span>
          </div>
          <p class="muted">{{ item.detail }} · {{ labelOf(followLabel, item.followStatus) }} · {{ item.origin === 'model' ? '模型草案' : '人工' }}</p>
          <div v-if="!readonly" class="row">
            <button v-if="item.confirmationStatus !== 'confirmed'" class="btn primary" @click="confirmNeed(item.id)">确认</button>
            <button v-if="item.confirmationStatus === 'confirmed'" class="btn ghost" @click="unlockNeed(item.id)">解锁</button>
          </div>
        </article>
      </section>
    </template>
  </div>
</template>
