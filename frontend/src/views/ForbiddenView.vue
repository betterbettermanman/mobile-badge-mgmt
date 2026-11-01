<script setup>
import { homePath } from '../auth'
</script>

<template>
  <div class="forbid">
    <div class="card stack" style="width:min(420px,100%)">
      <h1>无权访问该页面</h1>
      <p class="muted">当前账号不能查看这块内容。</p>
      <router-link class="btn primary" :to="homePath()" style="text-align:center;text-decoration:none">返回首页</router-link>
    </div>
  </div>
</template>
