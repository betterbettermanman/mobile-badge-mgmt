<script setup>
const props = defineProps({
  analysis: { type: Object, default: null },
  allowRetry: { type: Boolean, default: false }
})
const emit = defineEmits(['retry'])

const steps = [
  { key: 'queued', label: '排队' },
  { key: 'processing_asr', label: '转写中（火山）' },
  { key: 'processing_llm', label: '生成草案中（千问）' }
]

function stepState(key) {
  const current = props.analysis?.uiStatus
  if (!current || current === 'failed') return 'todo'
  if (current === 'succeeded') return 'done'
  const order = ['queued', 'processing_asr', 'processing_llm']
  const currentIndex = order.indexOf(current)
  const index = order.indexOf(key)
  if (index < currentIndex) return 'done'
  if (index === currentIndex) return 'current'
  return 'todo'
}
</script>

<template>
  <section v-if="analysis?.uiStatus" class="card analysis-bar" :class="analysis.uiStatus">
    <div class="row between">
      <strong>{{ analysis.title }}</strong>
      <button
        v-if="allowRetry && analysis.uiStatus === 'failed'"
        class="btn"
        type="button"
        :disabled="!analysis.canRetry"
        @click="emit('retry')"
      >
        {{ analysis.canRetry ? '重试分析' : '已达重试上限' }}
      </button>
    </div>
    <p class="muted">{{ analysis.subtitle }}</p>
    <ol v-if="analysis.uiStatus !== 'failed'" class="stepper">
      <li v-for="step in steps" :key="step.key" :class="stepState(step.key)">{{ step.label }}</li>
    </ol>
    <p v-if="analysis.uiStatus === 'failed' && !analysis.canRetry" class="error-text">
      已达重试上限（3 次），请联系管理员排查配额/配置或稍后由运维介入。
    </p>
  </section>
</template>
