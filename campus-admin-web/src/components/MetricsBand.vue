<script setup lang="ts">
import type { PageMetric } from '../adminTypes';

defineProps<{
  items: PageMetric[];
}>();
</script>

<template>
  <section class="metrics-band" aria-label="页面指标">
    <article v-for="(item, i) in items" :key="i" :class="item.accent ? 'accent-' + item.accent : ''">
      <span>{{ item.label }}</span>
      <strong>{{ item.value }}</strong>
      <small v-if="item.hint">{{ item.hint }}</small>
    </article>
  </section>
</template>
