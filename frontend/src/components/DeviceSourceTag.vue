<script setup>
import { computed } from 'vue'
import { sourceLabel } from '../labels'

const props = defineProps({
  source: { type: String, required: true }
})
const off = computed(() => props.source === 'badge')
const text = computed(() => sourceLabel[props.source] || props.source)
</script>

<template>
  <span class="tag" :class="{ off }" :title="off ? '真工牌采集将在二期开放，一期请使用手机录音或文件导入' : ''">
    {{ text }}<template v-if="off"> · 二期</template>
  </span>
</template>
