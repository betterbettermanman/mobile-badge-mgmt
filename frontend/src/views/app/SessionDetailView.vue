<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../../api'
import { authState } from '../../auth'
import AnalysisStatusBar from '../../components/AnalysisStatusBar.vue'
import ConfirmOfficialDialog from '../../components/ConfirmOfficialDialog.vue'
import DeviceSourceTag from '../../components/DeviceSourceTag.vue'
import { captureLabel, confirmLabel, consentLabel, followLabel, labelOf, sessionAnalysisLabel, speakerLabel, transcriptLabel } from '../../labels'

const route = useRoute()
const tab = ref(route.query.tab || 'overview')
const session = ref(null)
const transcript = ref(null)
const profile = ref(null)
const analysis = ref(null)
const needs = ref([])
const needStatus = ref('')
const error = ref('')
const message = ref('')
const loading = ref(false)
const form = ref({})
const needTitle = ref('')
const needDetail = ref('')
const confirmKind = ref('')
const pendingNeedId = ref(null)
let pollTimer = 0

const readonly = computed(() => route.path.startsWith('/admin') || session.value?.ownerUserId !== authState.user?.id)
const id = computed(() => route.params.id)
const canConfirm = computed(() => !readonly.value && analysis.value?.canConfirm === true)
const previewOnly = computed(() => analysis.value?.uiStatus === 'processing_llm')
const confirmedNeeds = computed(() => needs.value.filter((item) => item.confirmationStatus === 'confirmed'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    await refresh(false)
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function refresh(silent) {
  const base = route.path.startsWith('/admin') ? '/api/admin/sessions/' : '/api/sessions/'
  session.value = await api(base + id.value)
  analysis.value = await api(`/api/sessions/${id.value}/analysis`)
  transcript.value = await api(`/api/sessions/${id.value}/transcript`)
  profile.value = await api(`/api/sessions/${id.value}/profile`)
  if (!silent) {
    form.value = {
      appellation: profile.value.appellation || '',
      intent: profile.value.intent || '',
      budget: profile.value.budget || '',
      decisionMaker: profile.value.decisionMaker || '',
      notes: profile.value.notes || ''
    }
  }
  await loadNeeds()
  schedulePoll()
}

async function loadNeeds() {
  const query = needStatus.value ? `?status=${needStatus.value}` : ''
  const rows = await api(`/api/sessions/${id.value}/needs${query}`)
  needs.value = needStatus.value === 'confirmed'
    ? rows.filter((item) => item.confirmationStatus === 'confirmed')
    : rows
}

function schedulePoll() {
  window.clearInterval(pollTimer)
  const status = analysis.value?.uiStatus
  if (status === 'queued' || status === 'processing_asr' || status === 'processing_llm') {
    pollTimer = window.setInterval(() => {
      refresh(true).catch((err) => {
        error.value = err.message
      })
    }, 2000)
  }
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
    message.value = '草案已保存（尚未确认入库）'
  } catch (err) {
    error.value = err.message
  }
}

function askConfirmProfile() {
  if (!canConfirm.value) return
  confirmKind.value = 'profile'
}

function askConfirmNeed(needId) {
  if (!canConfirm.value) return
  pendingNeedId.value = needId
  confirmKind.value = 'need'
}

async function confirmOfficial() {
  try {
    if (confirmKind.value === 'profile') {
      profile.value = await api(`/api/sessions/${id.value}/profile/confirm`, { method: 'POST' })
      form.value = {
        appellation: profile.value.appellation || '',
        intent: profile.value.intent || '',
        budget: profile.value.budget || '',
        decisionMaker: profile.value.decisionMaker || '',
        notes: profile.value.notes || ''
      }
    } else if (pendingNeedId.value) {
      await api(`/api/sessions/${id.value}/needs/${pendingNeedId.value}/confirm`, { method: 'POST' })
      needStatus.value = 'confirmed'
      await loadNeeds()
    }
    message.value = '已写入正式库'
  } catch (err) {
    error.value = err.message
  } finally {
    confirmKind.value = ''
    pendingNeedId.value = null
  }
}

async function unlockProfile() {
  if (!window.confirm('解锁后将退回草案，已确认列表不再显示。可修改后再次确认。是否继续？')) return
  profile.value = await api(`/api/sessions/${id.value}/profile/unlock`, { method: 'POST' })
  message.value = '已解锁回草案'
}

async function retryAnalysis() {
  if (!analysis.value?.canRetry) return
  try {
    analysis.value = await api(`/api/sessions/${id.value}/analysis/retry`, { method: 'POST' })
    message.value = `已重新入队（第 ${analysis.value.retryCount} 次）`
    await refresh(true)
  } catch (err) {
    error.value = err.message
  }
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

async function unlockNeed(needId) {
  await api(`/api/sessions/${id.value}/needs/${needId}/unlock`, { method: 'POST' })
  message.value = '已解锁回草案'
  await loadNeeds()
}

watch(needStatus, () => {
  if (session.value) loadNeeds().catch((err) => { error.value = err.message })
})
watch(() => route.params.id, load)
onMounted(load)
onBeforeUnmount(() => window.clearInterval(pollTimer))
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
        <span class="tag" :class="analysis?.uiStatus === 'failed' ? 'bad' : ''">{{ sessionAnalysisLabel(session) }}</span>
      </div>
      <AnalysisStatusBar :analysis="analysis" :allow-retry="!readonly" @retry="retryAnalysis" />
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
        <p class="muted">分析成功不等于业务确认。草案须人工确认后才进入正式库。</p>
      </section>

      <section v-if="tab === 'transcript'" class="card stack">
        <strong>{{ analysis?.title || labelOf(transcriptLabel, transcript?.status) }}</strong>
        <p class="muted">{{ transcript?.note }}</p>
        <p v-if="!transcript?.fullText" class="muted">
          {{ transcript?.status === 'failed' ? transcript.note : '转写尚未完成…' }}
        </p>
        <p v-if="transcript?.fullText" style="white-space:pre-wrap">{{ transcript.fullText }}</p>
        <div v-for="(line, index) in transcript?.sentences || []" :key="index" class="item">
          {{ speakerLabel(line.speaker) }}：{{ line.text }}
        </div>
        <p class="muted">说话人显示为顾问/客户或 spk0、spk1，不要求识别真实身份，也不能手改分句角色。</p>
      </section>

      <section v-if="tab === 'profile'" class="card stack">
        <span class="tag" :class="profile?.confirmationStatus === 'confirmed' ? '' : 'warn'">
          {{ profile?.confirmationStatus ? labelOf(confirmLabel, profile.confirmationStatus) : '未生成' }}
        </span>
        <p class="muted">{{ profile?.hint }}</p>
        <p v-if="previewOnly" class="banner">须分析完成（succeeded）后才可确认；「生成草案中（千问）」仅可预览。</p>
        <button v-if="canConfirm && profile?.editable" class="btn ghost" @click="generateProfile">由转写生成草案</button>
        <label class="field"><span>称呼</span><input v-model="form.appellation" :disabled="readonly || !profile?.editable || previewOnly" /></label>
        <label class="field"><span>意向</span><input v-model="form.intent" :disabled="readonly || !profile?.editable || previewOnly" /></label>
        <label class="field"><span>预算</span><input v-model="form.budget" :disabled="readonly || !profile?.editable || previewOnly" /></label>
        <label class="field"><span>决策人</span><input v-model="form.decisionMaker" :disabled="readonly || !profile?.editable || previewOnly" /></label>
        <label class="field"><span>备注</span><textarea v-model="form.notes" :disabled="readonly || !profile?.editable || previewOnly" /></label>
        <div v-if="!readonly" class="row">
          <button v-if="profile?.editable && canConfirm" class="btn" @click="saveProfile">保存草案</button>
          <button class="btn primary" :disabled="!canConfirm || !profile?.editable" @click="askConfirmProfile">
            {{ profile?.confirmationStatus === 'confirmed' ? '已写入正式库' : '确认写入正式库' }}
          </button>
          <button v-if="profile?.confirmationStatus === 'confirmed'" class="btn ghost" @click="unlockProfile">解锁回草案</button>
        </div>
        <p v-if="profile?.confirmationStatus !== 'confirmed'" class="muted">已确认列表为空。本条仍是草案，确认后才会出现在这里。</p>
      </section>

      <section v-if="tab === 'needs'" class="stack">
        <div class="chips">
          <button class="chip" :class="{ on: needStatus === '' }" @click="needStatus = ''">全部</button>
          <button class="chip" :class="{ on: needStatus === 'draft' }" @click="needStatus = 'draft'">草案</button>
          <button class="chip" :class="{ on: needStatus === 'confirmed' }" @click="needStatus = 'confirmed'">已确认</button>
        </div>
        <p v-if="previewOnly" class="banner">须分析完成（succeeded）后才可确认；「生成草案中（千问）」仅可预览。</p>
        <p v-if="needStatus === 'confirmed' && confirmedNeeds.length === 0" class="muted">已确认列表为空。确认前不得进入默认导出。</p>
        <div v-if="!readonly && canConfirm" class="card stack">
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
            <button v-if="item.confirmationStatus !== 'confirmed'" class="btn primary" :disabled="!canConfirm" @click="askConfirmNeed(item.id)">确认写入正式库</button>
            <button v-if="item.confirmationStatus === 'confirmed'" class="btn ghost" @click="unlockNeed(item.id)">解锁回草案</button>
          </div>
        </article>
      </section>
    </template>
    <ConfirmOfficialDialog :open="!!confirmKind" :kind="confirmKind" @cancel="confirmKind = ''" @confirm="confirmOfficial" />
  </div>
</template>
