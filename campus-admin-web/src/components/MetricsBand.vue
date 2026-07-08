<script setup lang="ts">
import type { DashboardMetrics } from '../adminTypes';

defineProps<{
  metrics: DashboardMetrics;
}>();
</script>

<template>
  <section class="metrics-band" aria-label="今日指标">
    <article>
      <span>待处理</span>
      <strong>{{ metrics.reviewCount }}</strong>
      <small>{{ metrics.urgentCount }} 条高优先级</small>
    </article>
    <article>
      <span>平均置信度</span>
      <strong>{{ metrics.avgConfidence }}%</strong>
      <small>较昨日 +6%</small>
    </article>
    <article>
      <span>数据源成功率</span>
      <strong>{{ metrics.sourceSuccessRate }}%</strong>
      <small>{{ metrics.sourcesNeedAuth }} 个源需授权</small>
    </article>
    <article>
      <span>向量待同步</span>
      <strong>{{ metrics.vectorPending }}</strong>
      <small>审核后写入</small>
    </article>
  </section>
</template>
