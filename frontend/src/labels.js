export const roleLabel = {
  ADMIN: '管理员',
  SUPERVISOR: '主管',
  ADVISOR: '顾问'
}

export const sourceLabel = {
  phone: '手机',
  file: '文件',
  badge: '工牌'
}

export const captureLabel = {
  draft: '草稿',
  recording: '录音中',
  uploading: '上传中',
  upload_failed: '上传失败',
  uploaded: '已上传',
  cancelled: '已取消'
}

export const transcriptLabel = {
  queued: '排队中',
  processing: '转写中',
  succeeded: '转写成功',
  failed: '转写失败'
}

export const confirmLabel = {
  draft: '草案',
  confirmed: '已确认',
  rejected: '已驳回'
}

export const followLabel = {
  open: '待跟进',
  in_progress: '跟进中',
  done: '已完成',
  discarded: '已废弃'
}

export const consentLabel = {
  missing: '未授权',
  valid: '已授权',
  expired: '已过期',
  revoked: '已撤销'
}

export const deviceStatusLabel = {
  unbound: '未绑定',
  bound: '已绑定',
  disabled: '已停用'
}

export const jobLabel = {
  queued: '排队',
  processing: '处理中',
  succeeded: '完成',
  failed: '失败'
}

export function labelOf(map, key) {
  if (!key) return '—'
  return map[key] || key
}
