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
          <h1>管理后台</h1>
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
    <section class="agent-strip" aria-label="系统状态">
      <p class="eyebrow">Console</p>
      <div class="pulse-row">
        <span class="pulse-dot"></span>
        <span>真实接口模式</span>
      </div>
    </section>
  </aside>
</template>
