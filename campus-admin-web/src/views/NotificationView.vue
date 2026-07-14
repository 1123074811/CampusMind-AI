<script setup lang="ts">
import { onMounted, ref, watch, computed } from 'vue';
import type { AdminSession, DeliveryStats, DeliveryRecord } from '../adminTypes';
import { fetchDeliveryStats, fetchDeliveries, retryDelivery, withdrawDelivery } from '../api/admin';

const props = defineProps<{
  session?: AdminSession | null;
}>();

const stats = ref<DeliveryStats | null>(null);
const records = ref<DeliveryRecord[]>([]);
const statusFilter = ref('ALL');
const page = ref(0);
const pageSize = 50;
const loading = ref(false);
const error = ref('');
const message = ref('');

const statusOptions = [
  { key: 'ALL', label: '全部' },
  { key: 'SENT', label: '已发送' },
  { key: 'FAILED', label: '失败' },
  { key: 'RETRY', label: '重试中' },
  { key: 'PENDING', label: '等待中' },
  { key: 'WITHDRAWN', label: '已撤回' },
];

const filteredCount = computed(() => {
  if (!stats.value) return 0;
  if (statusFilter.value === 'ALL') return stats.value.total;
  return (stats.value as Record<string, number>)[statusFilter.value.toLowerCase()] ?? 0;
});

async function loadStats() {
  if (!props.session) return;
  try {
    stats.value = await fetchDeliveryStats(props.session);
  } catch (e) {
    error.value = e instanceof Error ? e.message : '统计加载失败';
  }
}

async function loadRecords() {
  if (!props.session) return;
  loading.value = true;
  error.value = '';
  try {
    records.value = await fetchDeliveries(props.session, statusFilter.value, page.value, pageSize);
  } catch (e) {
    error.value = e instanceof Error ? e.message : '投递记录加载失败';
    records.value = [];
  } finally {
    loading.value = false;
  }
}

function changeStatus(status: string) {
  statusFilter.value = status;
  page.value = 0;
  loadRecords();
}

function nextPage() {
  if (records.value.length >= pageSize) {
    page.value++;
    loadRecords();
  }
}

function prevPage() {
  if (page.value > 0) {
    page.value--;
    loadRecords();
  }
}

async function handleRetry(id: number) {
  if (!props.session) return;
  try {
    await retryDelivery(props.session, id);
    message.value = `投递 #${id} 已进入重试队列`;
    await loadStats();
    await loadRecords();
  } catch (e) {
    message.value = e instanceof Error ? e.message : '重试失败';
  }
}

async function handleWithdraw(id: number) {
  if (!props.session) return;
  if (!confirm(`确认撤回投递 #${id}？`)) return;
  try {
    await withdrawDelivery(props.session, id);
    message.value = `投递 #${id} 已撤回`;
    await loadStats();
    await loadRecords();
  } catch (e) {
    message.value = e instanceof Error ? e.message : '撤回失败';
  }
}

function statusLabel(status: string) {
  return { PENDING: '等待中', SENDING: '发送中', SENT: '已发送', RETRY: '重试中', FAILED: '失败', WITHDRAWN: '已撤回' }[status] ?? status;
}

function channelLabel(channel: string) {
  return { IN_APP: '站内', WEBHOOK: 'Webhook', PUSH: '推送' }[channel] ?? channel;
}

onMounted(async () => {
  await loadStats();
  await loadRecords();
});

watch(() => props.session, () => {
  if (props.session) {
    loadStats();
    loadRecords();
  }
});
</script>

<template>
  <section class="task-workspace" aria-label="通知运营">
    <div v-if="stats" class="metrics-band notification-stats">
      <article>
        <small>总投递</small>
        <strong>{{ stats.total }}</strong>
      </article>
      <article class="accent-green">
        <small>已发送</small>
        <strong>{{ stats.sent }}</strong>
      </article>
      <article :class="stats.failed > 0 ? 'accent-red' : ''">
        <small>失败</small>
        <strong>{{ stats.failed }}</strong>
      </article>
      <article>
        <small>已撤回</small>
        <strong>{{ stats.withdrawn }}</strong>
      </article>
    </div>

    <div class="task-console notification-console">
      <div class="panel-head">
        <h3>投递记录</h3>
        <div class="task-actions">
          <span v-if="message" class="notification-message">{{ message }}</span>
          <button type="button" class="ghost-button tiny" @click="loadStats().then(() => loadRecords())">刷新</button>
        </div>
      </div>

      <div class="queue-tabs">
        <button
          v-for="opt in statusOptions"
          :key="opt.key"
          :class="{ active: statusFilter === opt.key }"
          @click="changeStatus(opt.key)"
        >
          {{ opt.label }}
          <strong v-if="stats">
            {{ opt.key === 'ALL' ? stats.total : ((stats as Record<string, number>)[opt.key.toLowerCase()] ?? 0) }}
          </strong>
        </button>
      </div>

      <div v-if="error" class="empty-queue">{{ error }}</div>
      <div v-else-if="loading" class="empty-queue">加载中...</div>
      <div v-else-if="records.length === 0" class="empty-queue">
        暂无{{ statusFilter === 'ALL' ? '' : statusLabel(statusFilter) }}投递记录
      </div>

      <ul v-else class="timeline-list delivery-list">
        <li v-for="record in records" :key="record.id">
          <time>{{ record.createdAt?.slice(5, 16) ?? '-' }}</time>
          <div>
            <strong>投递 #{{ record.id }}</strong>
            <span>
              用户 #{{ record.userId }} · 提醒 #{{ record.reminderId }} · {{ channelLabel(record.channel) }}
              · 尝试 {{ record.attemptCount }} 次
            </span>
            <span v-if="record.lastError" class="delivery-error">{{ record.lastError }}</span>
          </div>
          <span class="status-pill" :data-status="record.status">{{ statusLabel(record.status) }}</span>
          <div class="delivery-actions">
            <button
              v-if="record.status === 'FAILED'"
              type="button"
              class="row-action-btn"
              title="重试"
              @click="handleRetry(record.id)"
            >↻</button>
            <button
              v-if="record.status !== 'WITHDRAWN'"
              type="button"
              class="row-action-btn danger"
              title="撤回"
              @click="handleWithdraw(record.id)"
            >✕</button>
          </div>
        </li>
      </ul>

      <div v-if="records.length > 0" class="delivery-pagination">
        <button type="button" class="ghost-button tiny" :disabled="page === 0" @click="prevPage">上一页</button>
        <span class="batch-count">第 {{ page + 1 }} 页 · 本页 {{ records.length }} 条</span>
        <button type="button" class="ghost-button tiny" :disabled="records.length < pageSize" @click="nextPage">下一页</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.notification-stats {
  width: min(100%, 1540px);
}
.delivery-list li {
  grid-template-columns: 116px minmax(0, 1fr) 92px auto;
  gap: 12px;
}
.delivery-error {
  display: block;
  color: var(--danger, #c8472f);
  font-size: 12px;
  font-weight: 700;
  margin-top: 2px;
}
.delivery-actions {
  display: flex;
  gap: 6px;
}
.delivery-pagination {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  border-top: 1px solid var(--line-soft, rgba(12,23,20,0.07));
}
.notification-message {
  color: var(--green, #16845f);
  font-size: 13px;
  font-weight: 700;
}
</style>
