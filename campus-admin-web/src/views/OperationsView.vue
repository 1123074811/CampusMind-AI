<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import type { AdminSession, DashboardMetrics, DataSource, CrawlTask, DeliveryStats, ReviewEvent } from '../adminTypes';
import { fetchDeliveryStats } from '../api/admin';

const props = defineProps<{
  session?: AdminSession | null;
  events?: ReviewEvent[];
  sources?: DataSource[];
  tasks?: CrawlTask[];
  metrics?: DashboardMetrics;
}>();

const deliveryStats = ref<DeliveryStats | null>(null);
const deliveryError = ref('');

const crawlStats = computed(() => {
  const tasks = props.tasks ?? [];
  const total = tasks.length;
  if (total === 0) return { total: 0, success: 0, failed: 0, running: 0, rate: 0 };
  const success = tasks.filter((t) => t.status === 'SUCCESS').length;
  const failed = tasks.filter((t) => t.status === 'FAILED').length;
  const running = tasks.filter((t) => t.status === 'RUNNING').length;
  return { total, success, failed, running, rate: Math.round((success / total) * 100) };
});

const publicationStats = computed(() => {
  const events = props.events ?? [];
  const aiIssues = events.filter((e) => e.aiNeedReview || e.aiStatus === 'REVIEW' || e.aiStatus === 'FAILED').length;
  const total = events.length;
  const published = events.filter((e) => e.status !== 'OFFLINE' && e.status !== 'REJECTED').length;
  return { total, aiIssues, published };
});

const aiStats = computed(() => {
  const m = props.metrics;
  if (!m) return { pending: 0, processing: 0, success: 0, failed: 0, rate: 0 };
  const pending = m.aiPendingCount ?? 0;
  const processing = m.aiProcessingCount ?? 0;
  const success = m.aiSuccessCount ?? 0;
  const failed = m.aiFailedCount ?? 0;
  const total = success + failed;
  const rate = total > 0 ? Math.round((success / total) * 100) : 0;
  return { pending, processing, success, failed, rate };
});

const sourceHealth = computed(() => {
  const sources = props.sources ?? [];
  const total = sources.length;
  const healthy = sources.filter((s) => s.status === 'RUNNING' || s.status === 'HEALTHY').length;
  const paused = sources.filter((s) => s.status === 'PAUSED').length;
  const needAuth = sources.filter((s) => s.status === 'NEEDS_AUTH').length;
  const rate = total > 0 ? Math.round((healthy / total) * 100) : 0;
  return { total, healthy, paused, needAuth, rate };
});

const notificationStats = computed(() => {
  const s = deliveryStats.value;
  if (!s) return { total: 0, failed: 0, rate: 0 };
  const rate = s.total > 0 ? Math.round(((s.total - s.failed) / s.total) * 100) : 100;
  return { total: s.total, failed: s.failed, rate };
});

function healthColor(rate: number): string {
  if (rate >= 80) return 'var(--green, #16845f)';
  if (rate >= 50) return 'var(--warning, #c98f26)';
  return 'var(--danger, #c8472f)';
}

onMounted(async () => {
  if (props.session) {
    try {
      deliveryStats.value = await fetchDeliveryStats(props.session);
    } catch (e) {
      deliveryError.value = e instanceof Error ? e.message : '通知统计加载失败';
    }
  }
});
</script>

<template>
  <section class="task-workspace" aria-label="运营大盘">
    <div class="task-console operations-console">
      <div class="panel-head">
        <h3>全链路运营指标</h3>
        <span v-if="deliveryError" style="color: var(--danger); font-size: 13px;">{{ deliveryError }}</span>
      </div>

      <div class="operations-grid">
        <article class="ops-card">
          <header>采集成功率</header>
          <div class="ops-number" :style="{ color: healthColor(crawlStats.rate) }">
            {{ crawlStats.rate }}%
          </div>
          <div class="ops-detail">
            <span>{{ crawlStats.success }} 成功</span>
            <span>{{ crawlStats.failed }} 失败</span>
            <span>{{ crawlStats.running }} 运行中</span>
          </div>
          <div class="ops-bar">
            <div class="ops-bar-fill" :style="{ width: crawlStats.rate + '%', background: healthColor(crawlStats.rate) }"></div>
          </div>
        </article>

        <article class="ops-card">
          <header>发布状态</header>
          <div class="ops-number" :style="{ color: publicationStats.aiIssues > 10 ? 'var(--warning)' : 'var(--green)' }">
            {{ publicationStats.published }}
          </div>
          <div class="ops-detail">
            <span>正在展示 {{ publicationStats.published }}</span>
            <span>AI 异常 {{ publicationStats.aiIssues }}</span>
          </div>
          <div class="ops-bar">
            <div
              class="ops-bar-fill"
              :style="{
                width: (publicationStats.total > 0 ? (publicationStats.published / publicationStats.total * 100) : 100) + '%',
                background: 'var(--green)'
              }"
            ></div>
          </div>
        </article>

        <article class="ops-card">
          <header>AI 处理成功率</header>
          <div class="ops-number" :style="{ color: healthColor(aiStats.rate) }">
            {{ aiStats.rate }}%
          </div>
          <div class="ops-detail">
            <span>{{ aiStats.success }} 成功</span>
            <span>{{ aiStats.failed }} 失败</span>
            <span v-if="aiStats.pending > 0">{{ aiStats.pending }} 排队</span>
          </div>
          <div class="ops-bar">
            <div class="ops-bar-fill" :style="{ width: aiStats.rate + '%', background: healthColor(aiStats.rate) }"></div>
          </div>
        </article>

        <article class="ops-card">
          <header>通知投递成功率</header>
          <div class="ops-number" :style="{ color: healthColor(notificationStats.rate) }">
            {{ notificationStats.rate }}%
          </div>
          <div class="ops-detail">
            <span>{{ notificationStats.total - notificationStats.failed }} 成功</span>
            <span>{{ notificationStats.failed }} 失败</span>
          </div>
          <div class="ops-bar">
            <div class="ops-bar-fill" :style="{ width: notificationStats.rate + '%', background: healthColor(notificationStats.rate) }"></div>
          </div>
        </article>

        <article class="ops-card">
          <header>数据源健康度</header>
          <div class="ops-number" :style="{ color: healthColor(sourceHealth.rate) }">
            {{ sourceHealth.rate }}%
          </div>
          <div class="ops-detail">
            <span>{{ sourceHealth.healthy }} 正常</span>
            <span>{{ sourceHealth.paused }} 暂停</span>
            <span v-if="sourceHealth.needAuth > 0">{{ sourceHealth.needAuth }} 需授权</span>
          </div>
          <div class="ops-bar">
            <div class="ops-bar-fill" :style="{ width: sourceHealth.rate + '%', background: healthColor(sourceHealth.rate) }"></div>
          </div>
        </article>

        <article class="ops-card">
          <header>事件总量</header>
          <div class="ops-number">{{ publicationStats.total }}</div>
          <div class="ops-detail">
            <span>正在展示 {{ publicationStats.published }}</span>
            <span>AI 异常 {{ publicationStats.aiIssues }}</span>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.operations-console {
  width: min(100%, 1540px);
}
.operations-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  padding: 20px;
}
.ops-card {
  border: 1px solid var(--line, rgba(12,23,20,0.1));
  border-radius: 20px;
  background: var(--surface, rgba(255,255,255,0.86));
  padding: 20px;
  display: grid;
  gap: 10px;
  align-content: start;
}
.ops-card header {
  font-size: 14px;
  font-weight: 800;
  color: var(--ink-muted, #66736f);
}
.ops-number {
  font-family: Georgia, "Songti SC", serif;
  font-size: 42px;
  line-height: 1;
  font-weight: 700;
}
.ops-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: var(--ink-muted, #66736f);
  font-weight: 700;
}
.ops-bar {
  height: 8px;
  border-radius: 999px;
  background: #ecf0ee;
  overflow: hidden;
}
.ops-bar-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 300ms ease;
}
@media (max-width: 1000px) {
  .operations-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .operations-grid {
    grid-template-columns: 1fr;
  }
}
</style>
