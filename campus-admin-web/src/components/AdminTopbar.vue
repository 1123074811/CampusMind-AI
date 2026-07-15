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
  review: { title: '校园事件管理', subtitle: '官网信息采集后直接展示，可按需修正、下线或恢复' },
  agent: { title: '智能体处理', subtitle: '查看精简卡片、关键字段、置信度和提取异常' },
  sources: { title: '数据源管理', subtitle: '查看公开网页来源健康度并手动触发采集' },
  tasks: { title: '采集任务', subtitle: '跟踪爬虫、导入和解析任务的执行状态' },
  users: { title: '用户管理', subtitle: '维护后台账号、用户状态和初始密码' },
  logs: { title: '日志管理', subtitle: '追踪内容维护、操作人和变更前后状态' },
  database: { title: '受控数据表管理', subtitle: '仅管理员可查看的业务数据表，不包含凭据和原始个人数据' },
  notifications: { title: '通知运营', subtitle: '监控投递状态、失败重试与撤回审计' },
  operations: { title: '运营大盘', subtitle: '采集、发布、AI、通知等全链路运营指标' }
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
