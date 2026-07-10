<script setup lang="ts">
import { computed, ref } from 'vue';
import StatusPill from '../components/StatusPill.vue';
import type { AdminAuditLog } from '../adminTypes';

const props = defineProps<{
  logs: AdminAuditLog[];
}>();

defineEmits<{
  refresh: [action: string];
}>();

const actionFilter = ref('ALL');
const actions = ['ALL', 'MANUAL_CRAWL', 'AUTO_CRAWL', 'REVIEW', 'CORRECT', 'REJECT', 'OFFLINE'];
const actionLabels: Record<string, string> = {
  ALL: '全部',
  MANUAL_CRAWL: '手动抓取',
  AUTO_CRAWL: '自动抓取',
  REVIEW: '审核通过',
  CORRECT: '人工修正',
  REJECT: '驳回',
  OFFLINE: '下线'
};

const visibleLogs = computed(() => {
  if (actionFilter.value === 'ALL') {
    return props.logs;
  }
  return props.logs.filter((log) => log.action === actionFilter.value);
});

function actionStatus(action: string) {
  if (action === 'REVIEW') {
    return 'REVIEWED';
  }
  if (action === 'CORRECT') {
    return 'CORRECTED';
  }
  if (action === 'REJECT') {
    return 'REJECTED';
  }
  if (action === 'OFFLINE') {
    return 'OFFLINE';
  }
  if (action === 'MANUAL_CRAWL' || action === 'AUTO_CRAWL') {
    return 'SUCCESS';
  }
  return 'PENDING';
}
</script>

<template>
  <section class="task-workspace">
    <section class="task-console" aria-label="日志列表">
      <div class="panel-head">
        <div>
          <p class="eyebrow">Audit Trail</p>
          <h3>日志管理</h3>
        </div>
        <div class="segmented-control" aria-label="日志类型过滤">
          <button
            v-for="action in actions"
            :key="action"
            type="button"
            :class="{ active: actionFilter === action }"
            @click="actionFilter = action; $emit('refresh', action)"
          >
            {{ actionLabels[action] ?? action }}
          </button>
        </div>
      </div>

      <ul class="timeline-list">
        <li v-for="log in visibleLogs" :key="log.id">
          <time>{{ new Date(log.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</time>
          <div>
            <strong>{{ log.eventId ? `事件 #${log.eventId}` : actionLabels[log.action] }} · {{ log.operatorId ? `操作人 #${log.operatorId}` : '系统任务' }}</strong>
            <small>{{ log.comment || '无备注' }}</small>
          </div>
          <StatusPill :status="actionStatus(log.action)" />
        </li>
      </ul>
    </section>
  </section>
</template>
