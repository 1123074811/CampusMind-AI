<script setup lang="ts">
import type { ReviewStatus, SourceStatus, TaskStatus } from '../adminTypes';

defineProps<{
  status: ReviewStatus | SourceStatus | TaskStatus | 'RULE' | 'LLM';
  large?: boolean;
}>();

function statusLabel(status: ReviewStatus | SourceStatus | TaskStatus | 'RULE' | 'LLM') {
  const labels: Record<string, string> = {
    AI_PUBLISHED: '已发布',
    CORRECTED: '需关注',
    REVIEWED: '已发布',
    REJECTED: '已归档',
    OFFLINE: '已归档',
    RUNNING: '运行中',
    HEALTHY: '健康',
    NEEDS_AUTH: '待授权',
    PAUSED: '暂停',
    SUCCESS: '成功',
    FAILED: '失败',
    PENDING: '等待',
    SKIPPED: '跳过',
    RULE: '规则兜底',
    LLM: '大模型'
  };
  return labels[status] ?? status;
}
</script>

<template>
  <span class="status-pill" :class="{ large }" :data-status="status">{{ statusLabel(status) }}</span>
</template>
