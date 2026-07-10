<script setup lang="ts">
import type { ReviewStatus, SourceStatus, TaskStatus } from '../adminTypes';

defineProps<{
  status: ReviewStatus | SourceStatus | TaskStatus | 'RULE' | 'LLM' | 'DETAIL_SUCCESS' | 'LIST_ONLY' | 'PARSE_FAILED' | 'DETAIL_FAILED';
  large?: boolean;
}>();

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    AI_PUBLISHED: '已发布',
    CORRECTED: '需关注',
    REVIEWED: '已发布',
    REJECTED: '已驳回',
    OFFLINE: '已下线',
    RUNNING: '运行中',
    HEALTHY: '健康',
    NEEDS_AUTH: '待授权',
    PAUSED: '暂停',
    SUCCESS: '成功',
    FAILED: '失败',
    PENDING: '等待',
    SKIPPED: '跳过',
    DETAIL_SUCCESS: '详情成功',
    LIST_ONLY: '仅列表',
    PARSE_FAILED: '解析失败',
    DETAIL_FAILED: '详情失败',
    RULE: '规则兜底',
    LLM: '大模型'
  };
  return labels[status] ?? status;
}
</script>

<template>
  <span class="status-pill" :class="{ large }" :data-status="status">{{ statusLabel(status) }}</span>
</template>
