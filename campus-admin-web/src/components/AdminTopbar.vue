<script setup lang="ts">
import type { NavKey } from '../adminTypes';
import type { AdminUser } from '../adminTypes';

const props = defineProps<{
  activeKey: NavKey;
  apiMode: 'live' | 'fallback';
  apiMessage: string;
  loading: boolean;
  user: AdminUser;
}>();

defineEmits<{
  logout: [];
  refresh: [];
}>();

const titles: Record<NavKey, { eyebrow: string; title: string }> = {
  review: { eyebrow: 'Event Review', title: '校园事件管理台' },
  sources: { eyebrow: 'Source Registry', title: '数据源治理中心' },
  tasks: { eyebrow: 'Crawler Timeline', title: '采集任务调度台' }
};
</script>

<template>
  <header class="topbar">
    <div>
      <p class="eyebrow">{{ titles[props.activeKey].eyebrow }}</p>
      <h2>{{ titles[props.activeKey].title }}</h2>
      <p class="connection-line" :data-mode="apiMode">{{ apiMessage }}<span v-if="loading"> · 同步中</span></p>
    </div>
    <div class="top-actions">
      <span class="user-chip">{{ props.user.username }} · {{ props.user.role }}</span>
      <button type="button" class="solid-button" :disabled="loading" @click="$emit('refresh')">
        {{ loading ? '刷新中' : '刷新' }}
      </button>
      <button type="button" class="ghost-button" @click="$emit('logout')">退出</button>
    </div>
  </header>
</template>
