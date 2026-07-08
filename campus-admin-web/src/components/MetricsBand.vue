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
      <small>{{ metrics.urgentCount }} 条需关注</small>
    </article>
    <article>
      <span>平均置信度</span>
      <strong>{{ metrics.avgConfidence }}%</strong>
      <small>来自当前事件集</small>
    </article>
    <article>
      <span>数据源成功率</span>
      <strong>{{ metrics.sourceSuccessRate }}%</strong>
      <small>{{ metrics.sourcesNeedAuth }} 个源需授权</small>
    </article>
    <article>
      <span>向量待同步</span>
      <strong>{{ metrics.vectorPending }}</strong>
      <small>未写入向量库</small>
    </article>
  </section>
</template>
