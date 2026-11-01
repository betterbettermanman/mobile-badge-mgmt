<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  open: Boolean,
  status: String,
  message: String
})
const emit = defineEmits(['close', 'submit'])
const checked = ref(false)
watch(() => props.open, (open) => {
  if (open) checked.value = false
})
</script>

<template>
  <div v-if="open" class="sheet-mask" @click.self="emit('close')">
    <form class="sheet" @submit.prevent="checked && emit('submit')">
      <h2>需要录音授权后才能开始</h2>
      <p class="muted">当前状态：{{ message || '未授权' }}</p>
      <p>请向客户说明并取得同意后继续。</p>
      <label class="check">
        <input v-model="checked" type="checkbox" />
        <span>客户已同意本次/本段服务录音</span>
      </label>
      <div class="row">
        <button class="btn primary" type="submit" :disabled="!checked">记录授权并继续</button>
        <button class="btn ghost" type="button" @click="emit('close')">取消</button>
      </div>
    </form>
  </div>
</template>
