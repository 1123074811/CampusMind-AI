<script setup lang="ts">
import type { NavItem, NavKey } from '../adminTypes';

defineProps<{
  items: NavItem[];
  activeKey: NavKey;
}>();

defineEmits<{
  select: [key: NavKey];
}>();
</script>

<template>
  <aside class="sidebar" aria-label="后台导航">
    <div class="brand-block">
      <div class="brand-mark">CM</div>
      <div>
        <p class="eyebrow">CampusMind</p>
        <h1>审核后台</h1>
      </div>
    </div>

    <nav class="nav-list">
      <button
        v-for="item in items"
        :key="item.key"
        class="nav-item"
        :class="{ active: activeKey === item.key }"
        type="button"
        @click="$emit('select', item.key)"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.count }}</strong>
      </button>
    </nav>

    <section class="agent-strip" aria-label="智能体状态">
      <p class="eyebrow">Agent Pulse</p>
      <div class="pulse-row">
        <span class="pulse-dot"></span>
        <span>感知 / 认知 / 决策链路在线</span>
      </div>
    </section>
  </aside>
</template>
