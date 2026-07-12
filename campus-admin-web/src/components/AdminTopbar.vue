<script setup lang="ts">
import type { AdminUser, NavKey } from '../adminTypes';

const props = defineProps<{
  activeKey: NavKey;
  apiMode: 'live' | 'fallback';
  apiMessage: string;
  loading: boolean;
  user: AdminUser;
}>();

defineEmits<{
  refresh: [];
  logout: [];
}>();

const titles: Record<NavKey, { title: string; subtitle: string }> = {
  review: { title: '校园事件审核', subtitle: '集中处理待确认、待下线和需纠错的信息' },
  agent: { title: '智能体处理', subtitle: '查看精简卡片、关键字段、置信度和待复核信息' },
  aiSettings: { title: '智能体配置', subtitle: '配置 OpenAI 兼容的大模型服务与规则兜底模式' },
  sources: { title: '数据源管理', subtitle: '查看公开网页来源健康度并手动触发采集' },
  tasks: { title: '采集任务', subtitle: '跟踪爬虫、导入和解析任务的执行状态' },
  users: { title: '用户管理', subtitle: '维护后台账号、用户状态和初始密码' },
  logs: { title: '日志管理', subtitle: '追踪审核操作、操作人和变更前后状态' }
};

function roleLabel(role: string) {
  return { ADMIN: '管理员', OPERATOR: '运营人员', STUDENT: '学生' }[role] ?? role;
}
</script>

<template>
  <header class="topbar">
    <div>
      <p class="eyebrow">Admin Console</p>
      <h2>{{ titles[props.activeKey].title }}</h2>
      <p class="connection-line" :data-mode="props.apiMode">
        {{ props.loading ? '正在同步后端数据' : props.apiMessage }}
      </p>
      <p class="connection-line" data-mode="live">
        {{ titles[props.activeKey].subtitle }}
      </p>
    </div>
    <div class="top-actions">
      <span class="user-chip">{{ props.user.username }} · {{ roleLabel(props.user.role) }}</span>
      <button type="button" class="ghost-button" @click="$emit('refresh')">刷新</button>
      <button type="button" class="ghost-button" @click="$emit('logout')">退出</button>
    </div>
  </header>
</template>
