<script setup>
defineProps({
  open: Boolean,
  kind: { type: String, default: 'profile' }
})
defineEmits(['cancel', 'confirm'])
</script>

<template>
  <div v-if="open" class="sheet-mask" @click.self="$emit('cancel')">
    <div class="sheet">
      <h2>{{ kind === 'need' ? '确认写入需求正式库？' : '确认写入客户画像正式库？' }}</h2>
      <p>
        确认后本条才会进入「已确认」列表与默认可导出范围，并可能触发 Webhook（profile.confirmed / need.confirmed）。未确认前仅保留为草案，不会出站。如需修改可解锁回草案后再确认。
      </p>
      <div class="row">
        <button class="btn primary" type="button" @click="$emit('confirm')">确认写入</button>
        <button class="btn ghost" type="button" @click="$emit('cancel')">取消</button>
      </div>
    </div>
  </div>
</template>
