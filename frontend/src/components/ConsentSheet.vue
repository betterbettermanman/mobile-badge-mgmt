<script setup>
import { ref, watch } from 'vue'
import { consentLabel, labelOf, VENDOR_SENTENCE } from '../labels'

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
      <p class="muted">当前状态：{{ message || labelOf(consentLabel, status) || '未授权' }}</p>
      <div class="vendor-box">
        <p>为完成转写与客户画像/需求分析，</p>
        <p>{{ VENDOR_SENTENCE }}。</p>
        <p>数据仅用于本组织服务，不会自动写入正式库；画像与需求需您人工确认。</p>
      </div>
      <p class="muted">记录后在有效期内无需每次开录重新勾选；过期或撤销后再开录需重新授权。</p>
      <label class="check">
        <input v-model="checked" type="checkbox" />
        <span>我已向客户说明，并取得录音同意</span>
      </label>
      <div class="row">
        <button class="btn primary" type="submit" :disabled="!checked">记录授权并继续</button>
        <button class="btn ghost" type="button" @click="emit('close')">取消</button>
      </div>
    </form>
  </div>
</template>
