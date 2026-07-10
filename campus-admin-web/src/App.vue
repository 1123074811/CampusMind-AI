<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { fetchDashboard, reviewEvent } from './api/dashboard';
import {
  createAdminUser,
  fetchAdminLogs,
  fetchAdminUsers,
  resetAdminUserPassword,
  updateAdminUserStatus
} from './api/admin';
import { clearSession, loadSession } from './api/auth';
import { crawlPublicSources, crawlSource, fetchCrawlItems } from './api/crawler';
import AdminSidebar from './components/AdminSidebar.vue';
import AdminTopbar from './components/AdminTopbar.vue';
import MetricsBand from './components/MetricsBand.vue';
import type { AdminAuditLog, AdminManagedUser, AdminSession, CrawlItem, CrawlTask, DashboardMetrics, DataSource, NavItem, NavKey, ReviewEvent } from './adminTypes';
import LoginView from './views/LoginView.vue';
import ReviewView from './views/ReviewView.vue';
import SourcesView from './views/SourcesView.vue';
import TasksView from './views/TasksView.vue';
import UsersView from './views/UsersView.vue';
import LogsView from './views/LogsView.vue';
import AgentView from './views/AgentView.vue';

const session = ref<AdminSession | null>(loadSession());
const activeNav = ref<NavKey>('review');
const selectedId = ref(0);
const reviewEvents = ref<ReviewEvent[]>([]);
const dataSources = ref<DataSource[]>([]);
const crawlTasks = ref<CrawlTask[]>([]);
const crawlItems = ref<CrawlItem[]>([]);
const adminUsers = ref<AdminManagedUser[]>([]);
const auditLogs = ref<AdminAuditLog[]>([]);
const loading = ref(false);
const crawlerRunning = ref(false);
const crawlingSourceId = ref<number | null>(null);
const apiMode = ref<'live' | 'fallback'>('fallback');
const apiMessage = ref('等待连接后端');
const dashboardMetrics = ref<DashboardMetrics>({
  reviewCount: 0,
  urgentCount: 0,
  avgConfidence: 0,
  sourceSuccessRate: 0,
  sourcesNeedAuth: 0,
  vectorPending: 0
});

const urgentCount = computed(() => reviewEvents.value.filter((item) => item.confidence < 0.75 || item.status === 'CORRECTED').length);
const reviewCount = computed(() => reviewEvents.value.filter((item) => item.status === 'AI_PUBLISHED' || item.status === 'CORRECTED').length);
const avgConfidence = computed(() => {
  if (reviewEvents.value.length === 0) {
    return 0;
  }
  const sum = reviewEvents.value.reduce((total, item) => total + item.confidence, 0);
  return Math.round((sum / reviewEvents.value.length) * 100);
});

const navItems = computed<NavItem[]>(() => [
  { key: 'review', label: '校园事件', count: reviewEvents.value.length },
  { key: 'agent', label: '智能体', count: reviewEvents.value.filter((item) => item.aiStatus !== 'SUCCESS').length },
  { key: 'sources', label: '数据源', count: visibleDataSources.value.length },
  { key: 'tasks', label: '采集任务', count: visibleCrawlTasks.value.length },
  { key: 'users', label: '用户管理', count: adminUsers.value.length },
  { key: 'logs', label: '日志管理', count: auditLogs.value.length }
]);

const validSourceIds = computed(() => new Set(visibleDataSources.value.map((source) => source.id)));

const visibleDataSources = computed(() => {
  return dataSources.value.filter((source) => source.channel === 'PUBLIC_WEB' && source.id >= 9411);
});

const visibleCrawlTasks = computed(() => {
  return crawlTasks.value.filter((task) => {
    const matchedSource = visibleDataSources.value.find((source) => task.name.startsWith(source.name));
    return matchedSource ? validSourceIds.value.has(matchedSource.id) : !task.note.includes('Invalid selector_config JSON');
  });
});

const metrics = computed<DashboardMetrics>(() => {
  if (apiMode.value === 'live') {
    return dashboardMetrics.value;
  }

  return {
    reviewCount: 0,
    urgentCount: 0,
    avgConfidence: 0,
    sourceSuccessRate: 0,
    sourcesNeedAuth: 0,
    vectorPending: 0
  };
});

function selectEvent(id: number) {
  selectedId.value = id;
}

async function loadDashboard() {
  if (!session.value) {
    return;
  }

  loading.value = true;
  try {
    const dashboard = await fetchDashboard(session.value);
    dashboardMetrics.value = dashboard.metrics;
    reviewEvents.value = dashboard.events;
    dataSources.value = dashboard.dataSources;
    crawlTasks.value = dashboard.tasks;
    try {
      crawlItems.value = await fetchCrawlItems(session.value);
    } catch (error) {
      crawlItems.value = [];
    }
    try {
      adminUsers.value = (await fetchAdminUsers(session.value)).items;
    } catch (error) {
      adminUsers.value = [];
    }
    try {
      auditLogs.value = (await fetchAdminLogs(session.value)).items;
    } catch (error) {
      auditLogs.value = [];
    }
    selectedId.value = dashboard.events[0]?.id ?? selectedId.value;
    apiMode.value = 'live';
    apiMessage.value = '已连接后端数据库';
  } catch (error) {
    apiMode.value = 'fallback';
    apiMessage.value = '后端未连接';
    reviewEvents.value = [];
    dataSources.value = [];
    crawlTasks.value = [];
    crawlItems.value = [];
    adminUsers.value = [];
    auditLogs.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadUsers() {
  if (!session.value) {
    return;
  }
  try {
    adminUsers.value = (await fetchAdminUsers(session.value)).items;
    apiMessage.value = '用户列表已刷新';
  } catch (error) {
    apiMessage.value = error instanceof Error ? error.message : '用户列表加载失败';
  }
}

async function loadLogs(action = 'ALL') {
  if (!session.value) {
    return;
  }
  try {
    auditLogs.value = (await fetchAdminLogs(session.value, action)).items;
    apiMessage.value = '审计日志已刷新';
  } catch (error) {
    apiMessage.value = error instanceof Error ? error.message : '审计日志加载失败';
  }
}

async function createUser(payload: { username: string; phone: string; role: string; password: string }) {
  try {
    await createAdminUser(session.value, payload);
    apiMessage.value = `${payload.username} 已创建`;
    await loadUsers();
  } catch (error) {
    apiMessage.value = error instanceof Error ? error.message : '创建用户失败';
  }
}

async function toggleUser(user: AdminManagedUser) {
  try {
    await updateAdminUserStatus(session.value, user.id, user.status === 1 ? 0 : 1);
    apiMessage.value = `${user.username} 状态已更新`;
    await loadUsers();
  } catch (error) {
    apiMessage.value = error instanceof Error ? error.message : '用户状态更新失败';
  }
}

async function resetUserPassword(user: AdminManagedUser) {
  try {
    await resetAdminUserPassword(session.value, user.id);
    apiMessage.value = `${user.username} 密码已重置为 123456`;
  } catch (error) {
    apiMessage.value = error instanceof Error ? error.message : '重置密码失败';
  }
}

async function reviewSelected(status: 'REVIEWED' | 'REJECTED' | 'CORRECTED' | 'OFFLINE', comment: string) {
  const event = reviewEvents.value.find((item) => item.id === selectedId.value);
  if (!event) {
    return;
  }

  if (apiMode.value === 'live') {
    try {
      const updated = await reviewEvent(session.value, event.id, status, comment);
      Object.assign(event, updated);
      dashboardMetrics.value.reviewCount = reviewEvents.value.filter((item) => item.status === 'AI_PUBLISHED' || item.status === 'CORRECTED').length;
      dashboardMetrics.value.urgentCount = reviewEvents.value.filter((item) => item.confidence < 0.75 || item.status === 'CORRECTED').length;
      return;
    } catch (error) {
      apiMessage.value = '事件状态写入失败，已保留当前页面状态';
    }
  }

  event.status = status;
  event.risk = status === 'REVIEWED' ? '已人工确认' : '已标记为无效事件';
}

function approveSelected() {
  reviewSelected('REVIEWED', '恢复展示');
}

function rejectSelected() {
  reviewSelected('OFFLINE', '管理员下线');
}

async function runCrawlerNow() {
  if (crawlerRunning.value) {
    return;
  }

  const now = new Date();
  const time = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
  const tempTaskId = Date.now();
  crawlTasks.value = [
    {
      id: tempTaskId,
      name: '手动触发全量采集',
      status: 'RUNNING',
      owner: '爬虫调度器',
      time,
      note: '管理员手动触发，完成后交给智能体清洗'
    },
    ...crawlTasks.value
  ];
  activeNav.value = 'tasks';
  crawlerRunning.value = true;
  try {
    const sourceIds = visibleDataSources.value
      .filter((source) => source.channel === 'PUBLIC_WEB' && source.status !== 'PAUSED')
      .map((source) => source.id);
    if (apiMode.value !== 'live' || sourceIds.length === 0) {
      updateTempCrawlerTask(tempTaskId, 'FAILED', '没有可采集的数据源');
      return;
    }

    const batch = await crawlPublicSources(session.value, sourceIds);
    updateTempCrawlerTask(
      tempTaskId,
      batch.failedCount > 0 ? 'FAILED' : 'SUCCESS',
      batch.failedCount > 0
        ? `${batch.failedCount} 个数据源采集失败，查看下方任务明细`
        : `真实爬虫完成，新增或更新 ${batch.persistedCount} 条网页数据`
    );
    await loadDashboard();
  } catch (error) {
    updateTempCrawlerTask(tempTaskId, 'FAILED', error instanceof Error ? error.message : '爬虫服务调用失败');
  } finally {
    crawlerRunning.value = false;
  }
}

async function crawlSelectedSource(sourceId: number) {
  if (crawlingSourceId.value !== null) {
    return;
  }
  crawlingSourceId.value = sourceId;
  activeNav.value = 'sources';
  try {
    const result = await crawlSource(session.value, sourceId);
    apiMessage.value = `${result.sourceName} 采集完成，新增或更新 ${result.persistedCount} 条`;
    await loadDashboard();
  } catch (error) {
    apiMessage.value = error instanceof Error ? error.message : '数据源采集失败';
  } finally {
    crawlingSourceId.value = null;
  }
}

function updateTempCrawlerTask(id: number, status: 'SUCCESS' | 'FAILED', note: string) {
  const task = crawlTasks.value.find((item) => item.id === id);
  if (!task) {
    return;
  }
  task.status = status;
  task.note = note;
}

function handleAuthenticated(nextSession: AdminSession) {
  session.value = nextSession;
  apiMessage.value = '已登录，正在同步后端数据';
  loadDashboard();
}

function logout() {
  clearSession();
  session.value = null;
  apiMode.value = 'fallback';
  apiMessage.value = '等待连接后端';
  reviewEvents.value = [];
  dataSources.value = [];
  crawlTasks.value = [];
  crawlItems.value = [];
  adminUsers.value = [];
  auditLogs.value = [];
}

onMounted(() => {
  if (session.value) {
    loadDashboard();
  }
});
</script>

<template>
  <LoginView v-if="!session" @authenticated="handleAuthenticated" />

  <main v-else class="admin-shell">
    <AdminSidebar :items="navItems" :active-key="activeNav" @select="activeNav = $event" />

    <section class="workspace">
      <AdminTopbar
        v-if="session"
        :active-key="activeNav"
        :api-mode="apiMode"
        :api-message="apiMessage"
        :loading="loading"
        :user="session.user"
        @refresh="loadDashboard"
        @logout="logout"
      />
      <MetricsBand :metrics="metrics" />

      <ReviewView
        v-if="activeNav === 'review'"
        :events="reviewEvents"
        :selected-id="selectedId"
        @select="selectEvent"
        @unarchive="approveSelected"
        @archive="rejectSelected"
        @refresh="loadDashboard"
      />
      <AgentView
        v-else-if="activeNav === 'agent'"
        :events="reviewEvents"
        @refresh="loadDashboard"
      />
      <SourcesView
        v-else-if="activeNav === 'sources'"
        :data-sources="visibleDataSources"
        :crawling-source-id="crawlingSourceId"
        @crawl="crawlSelectedSource"
      />
      <TasksView
        v-else-if="activeNav === 'tasks'"
        :tasks="visibleCrawlTasks"
        :crawl-items="crawlItems"
        :crawler-running="crawlerRunning"
        @run-now="runCrawlerNow"
      />
      <UsersView
        v-else-if="activeNav === 'users'"
        :users="adminUsers"
        :loading="loading"
        @refresh="loadUsers"
        @create="createUser"
        @toggle="toggleUser"
        @reset-password="resetUserPassword"
      />
      <LogsView
        v-else-if="activeNav === 'logs'"
        :logs="auditLogs"
        @refresh="loadLogs"
      />
    </section>
  </main>
</template>

<style>
:root {
  --ink: #172023;
  --ink-muted: #68777b;
  --paper: #f4f0e7;
  --paper-soft: #fffaf0;
  --line: #c9c0af;
  --line-soft: #d8d0c0;
  --accent: #e0593e;
  --green: #34754b;
  --warning: #b28b38;
  --danger: #9f3928;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  min-width: 320px;
  background:
    linear-gradient(90deg, rgba(31, 49, 54, 0.035) 1px, transparent 1px),
    linear-gradient(180deg, rgba(31, 49, 54, 0.035) 1px, transparent 1px),
    var(--paper);
  background-size: 28px 28px;
  color: var(--ink);
  font-family: Aptos, 'Segoe UI', 'Microsoft YaHei UI', sans-serif;
}

button,
input,
select,
textarea {
  font: inherit;
}

button {
  cursor: pointer;
}

h1,
h2,
h3,
p {
  margin: 0;
}

h1 {
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: 24px;
  line-height: 1.1;
  font-weight: 700;
}

h2 {
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: clamp(28px, 3vw, 44px);
  line-height: 1.02;
  text-wrap: balance;
}

h3 {
  font-size: 20px;
  line-height: 1.25;
  text-wrap: balance;
}

.admin-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
}

.sidebar {
  min-height: 100vh;
  padding: 24px 18px;
  background: var(--ink);
  color: #f7f0e2;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 32px;
}

.brand-block {
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 14px;
  align-items: center;
}

.brand-mark {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  background: var(--accent);
  color: #fff9ed;
  border: 1px solid rgba(255, 255, 255, 0.18);
  font-weight: 900;
  letter-spacing: 0;
}

.eyebrow {
  margin: 0 0 6px;
  color: #73838a;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.sidebar .eyebrow {
  color: #b8c0b6;
}

.nav-list {
  display: grid;
  align-content: start;
  gap: 8px;
}

.nav-item {
  min-height: 46px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: transparent;
  color: #dce3dc;
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  padding: 0 12px;
  text-align: left;
}

.nav-item strong {
  min-width: 32px;
  height: 24px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.08);
  font-size: 12px;
}

.nav-item.active {
  background: var(--paper);
  color: var(--ink);
  border-color: var(--paper);
}

.nav-item.active strong {
  background: var(--ink);
  color: var(--paper);
}

.agent-strip {
  border-top: 1px solid rgba(255, 255, 255, 0.14);
  padding-top: 18px;
}

.pulse-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  line-height: 1.5;
}

.pulse-dot {
  width: 9px;
  height: 9px;
  background: #7eb77f;
  box-shadow: 0 0 0 6px rgba(126, 183, 127, 0.15);
}

.workspace {
  min-width: 0;
  padding: 24px;
  display: grid;
  align-content: start;
  gap: 20px;
}

.topbar,
.metrics-band,
.main-grid,
.split-workspace,
.task-workspace,
.agent-workspace {
  width: min(100%, 1520px);
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 20px;
}

.top-actions,
.decision-actions,
.filters,
.task-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-chip {
  min-height: 42px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  background: rgba(255, 251, 241, 0.62);
  padding: 0 12px;
  color: var(--ink);
  font-size: 13px;
  font-weight: 900;
}

.schedule-badge {
  min-height: 40px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  background: #e7f1ea;
  color: var(--green);
  padding: 0 12px;
  font-size: 13px;
  font-weight: 900;
}

.connection-line {
  margin-top: 10px;
  color: var(--ink-muted);
  font-size: 13px;
  font-weight: 700;
}

.connection-line::before {
  content: '';
  width: 8px;
  height: 8px;
  display: inline-block;
  margin-right: 8px;
  background: var(--warning);
}

.connection-line[data-mode='live']::before {
  background: var(--green);
}

.ghost-button,
.solid-button {
  min-height: 42px;
  border: 1px solid var(--ink);
  padding: 0 16px;
  background: transparent;
  color: var(--ink);
  font-weight: 800;
}

.ghost-button.tiny {
  min-height: 40px;
  padding: 0 12px;
}

.solid-button {
  background: var(--ink);
  color: #fff9ed;
}

.ghost-button.danger {
  border-color: var(--danger);
  color: var(--danger);
}

.metrics-band {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--line);
  background: rgba(255, 251, 241, 0.7);
}

.metrics-band article {
  min-height: 116px;
  padding: 18px;
  display: grid;
  align-content: space-between;
  border-right: 1px solid var(--line);
}

.metrics-band article:last-child {
  border-right: 0;
}

.metrics-band span,
.metrics-band small,
.event-main small,
.source-row small,
.timeline-list small,
.extract-grid span,
.detail-list dt,
.stacked-list dt,
.source-meta,
.source-foot small,
.dispatch-grid small,
.agent-chain small {
  color: var(--ink-muted);
}

.metrics-band strong {
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: 40px;
  line-height: 1;
}

.main-grid,
.split-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 20px;
  align-items: start;
}

.review-panel,
.detail-panel,
.source-board,
.inspector-panel,
.task-console,
.agent-lab {
  border: 1px solid var(--line);
  background: rgba(255, 251, 241, 0.86);
}

.panel-head {
  padding: 18px;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid var(--line);
}

.filters select,
.filters input {
  min-height: 40px;
  border: 1px solid var(--line);
  background: var(--paper-soft);
  padding: 0 12px;
  color: var(--ink);
}

.filters.vertical {
  display: grid;
  align-items: stretch;
}

.filters input {
  width: 220px;
}

.event-list {
  display: grid;
}

.event-row {
  min-height: 82px;
  border: 0;
  border-bottom: 1px solid var(--line-soft);
  background: transparent;
  color: var(--ink);
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr) 86px 52px;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  text-align: left;
}

.event-row:hover,
.event-row.selected,
.source-card:hover,
.source-card.selected {
  background: #efe5d2;
}

.event-type {
  font-size: 11px;
  font-weight: 900;
  color: var(--accent);
}

.event-main {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.event-main strong,
.source-card strong,
.timeline-list strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-pill {
  min-height: 28px;
  display: inline-grid;
  place-items: center;
  border: 1px solid #b9af9e;
  padding: 0 9px;
  font-size: 12px;
  font-weight: 800;
  color: #293336;
  background: #f7f0e2;
}

.status-pill.large {
  width: fit-content;
  min-height: 32px;
}

.status-pill[data-status='AI_PUBLISHED'],
.status-pill[data-status='RULE'],
.status-pill[data-status='LLM'] {
  background: #e7f1ea;
  border-color: #9cb7a1;
}

.status-pill[data-status='CORRECTED'],
.status-pill[data-status='NEEDS_AUTH'],
.status-pill[data-status='RUNNING'],
.status-pill[data-status='PENDING'] {
  background: #fff0c4;
  border-color: #d4b75c;
}

.status-pill[data-status='REJECTED'],
.status-pill[data-status='FAILED'] {
  background: #f6d8d0;
  border-color: #d59586;
}

.confidence {
  font-weight: 900;
  text-align: right;
}

.detail-panel,
.inspector-panel {
  min-height: 0;
  padding: 18px;
  display: grid;
  gap: 18px;
  align-content: start;
}

.detail-hero {
  display: grid;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.detail-list {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-list div,
.stacked-list div {
  min-height: 66px;
  border: 1px solid var(--line-soft);
  padding: 10px;
  display: grid;
  align-content: space-between;
}

.detail-list dt,
.stacked-list dt {
  font-size: 12px;
  font-weight: 800;
}

.detail-list dd,
.stacked-list dd {
  margin: 0;
  font-weight: 800;
}

.mini-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.summary-text {
  line-height: 1.8;
  color: #314044;
  text-wrap: pretty;
}

.tag-row,
.rule-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-row span,
.rule-stack span {
  border: 1px solid var(--line);
  padding: 5px 9px;
  font-size: 12px;
  color: #4d5d61;
}

.decision-actions {
  justify-content: stretch;
  margin-top: auto;
}

.decision-actions button {
  flex: 1;
}

.segmented-control {
  min-height: 40px;
  display: inline-grid;
  grid-auto-flow: column;
  border: 1px solid var(--line);
  background: var(--paper-soft);
}

.segmented-control button {
  min-width: 58px;
  border: 0;
  border-right: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  font-weight: 800;
}

.segmented-control button:last-child {
  border-right: 0;
}

.segmented-control button.active {
  background: var(--ink);
  color: #fff9ed;
}

.source-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  background: var(--line);
}

.source-card {
  min-height: 176px;
  border: 0;
  background: rgba(255, 251, 241, 0.96);
  color: var(--ink);
  padding: 16px;
  display: grid;
  align-content: space-between;
  gap: 12px;
  text-align: left;
}

.source-card-head,
.source-foot {
  min-width: 0;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.source-meta {
  font-size: 13px;
  font-weight: 700;
}

.bar-track {
  height: 10px;
  border: 1px solid var(--line);
  background: var(--paper);
}

.bar-track i {
  height: 100%;
  display: block;
  background: var(--accent);
}

.stacked-list {
  margin: 0;
  display: grid;
  gap: 10px;
}

.task-workspace,
.agent-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 20px;
}

.task-split-workspace {
  width: min(100%, 1520px);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 20px;
  align-items: start;
}

.timeline-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
}

.timeline-list li,
.crawl-item-list li {
  min-height: 74px;
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 86px;
  align-items: center;
  gap: 14px;
  border-bottom: 1px solid var(--line-soft);
  padding: 0 18px;
}

.timeline-list li,
.crawl-item-list li {
  cursor: pointer;
}

.timeline-list li:hover,
.timeline-list li.selected,
.crawl-item-list li:hover,
.crawl-item-list li.selected {
  background: #efe5d2;
}

.timeline-list time {
  color: var(--accent);
  font-weight: 900;
}

.timeline-list div,
.crawl-item-main {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.crawl-item-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
}

.crawl-item-list li {
  min-height: 118px;
  align-items: start;
  padding-top: 16px;
  padding-bottom: 16px;
}

.crawl-item-main span {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.crawl-item-main p,
.crawl-item-main a {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink-muted);
  font-size: 13px;
  line-height: 1.5;
}

.crawl-item-main a {
  color: var(--accent);
  text-decoration: none;
}

.source-url,
.stacked-list a {
  display: block;
  min-width: 0;
  overflow: hidden;
  color: var(--accent);
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-dialog {
  width: min(720px, calc(100vw - 32px));
  max-height: calc(100vh - 64px);
  border: 0;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  padding: 24px;
}

.event-dialog::backdrop {
  background: rgba(12, 23, 20, 0.38);
}

.dialog-head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
}

.dialog-content {
  white-space: pre-wrap;
  line-height: 1.7;
}

.link-button {
  min-height: 42px;
  display: grid;
  place-items: center;
  text-decoration: none;
}

.dispatch-grid,
.agent-chain {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  border: 1px solid var(--line);
  background: var(--line);
}

.dispatch-grid article,
.agent-chain article {
  min-height: 128px;
  padding: 16px;
  background: rgba(255, 251, 241, 0.9);
  display: grid;
  align-content: space-between;
}

.dispatch-grid strong {
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: 36px;
}

.agent-workspace {
  grid-template-columns: minmax(0, 1fr) 420px;
  align-items: start;
}

textarea {
  width: calc(100% - 32px);
  min-height: 168px;
  margin: 16px;
  resize: vertical;
  border: 1px solid var(--line);
  background: var(--paper-soft);
  color: var(--ink);
  padding: 12px;
  line-height: 1.7;
}

.extract-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  margin: 0 16px 16px;
  background: var(--line);
  border: 1px solid var(--line);
}

.extract-grid div {
  min-height: 82px;
  background: var(--paper-soft);
  padding: 12px;
  display: grid;
  align-content: space-between;
}

.extract-grid strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.agent-chain {
  grid-template-columns: 1fr;
}

.agent-chain span {
  color: var(--accent);
  font-weight: 900;
}

.login-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 430px;
  color: var(--ink);
}

.login-hero {
  min-height: 100vh;
  padding: 28px;
  background:
    linear-gradient(90deg, rgba(255, 250, 240, 0.05) 1px, transparent 1px),
    linear-gradient(180deg, rgba(255, 250, 240, 0.05) 1px, transparent 1px),
    var(--ink);
  background-size: 28px 28px;
  color: #f7f0e2;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 28px;
}

.login-brand {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.login-statement {
  width: min(760px, 100%);
  align-self: center;
}

.login-statement h2 {
  font-size: clamp(42px, 7vw, 82px);
  max-width: 820px;
}

.login-matrix {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(255, 255, 255, 0.16);
}

.login-matrix article {
  min-height: 132px;
  padding: 16px;
  display: grid;
  align-content: space-between;
  background: rgba(255, 250, 240, 0.06);
}

.login-matrix span {
  color: var(--accent);
  font-weight: 900;
}

.login-matrix small {
  color: #b8c0b6;
  line-height: 1.6;
}

.login-panel {
  min-height: 100vh;
  padding: 28px;
  display: grid;
  align-content: center;
  gap: 18px;
  border-left: 1px solid var(--line);
  background: rgba(255, 251, 241, 0.88);
}

.login-form {
  display: grid;
  gap: 18px;
}

.login-form label {
  display: grid;
  gap: 8px;
  font-weight: 900;
}

.login-form label span {
  color: var(--ink-muted);
  font-size: 12px;
}

.login-form input {
  width: 100%;
  min-height: 48px;
  border: 1px solid var(--line);
  background: var(--paper-soft);
  color: var(--ink);
  padding: 0 12px;
  font-weight: 800;
}

.login-submit,
.login-demo {
  width: 100%;
}

.login-submit:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}

.login-error {
  border: 1px solid #d59586;
  background: #f6d8d0;
  color: var(--danger);
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 800;
}

.login-footnote {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.login-footnote span {
  border: 1px solid var(--line);
  padding: 5px 9px;
  font-size: 12px;
  color: var(--ink-muted);
  font-weight: 800;
}

@media (max-width: 1180px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }

  .sidebar {
    min-height: auto;
    grid-template-rows: auto;
    grid-template-columns: 1fr;
  }

  .nav-list {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .agent-strip {
    display: none;
  }

  .main-grid,
  .split-workspace,
  .task-split-workspace,
  .agent-workspace,
  .login-shell {
    grid-template-columns: 1fr;
  }

  .login-panel,
  .login-hero {
    min-height: auto;
  }
}

@media (max-width: 760px) {
  .workspace {
    padding: 14px;
  }

  .topbar,
  .panel-head {
    align-items: stretch;
    flex-direction: column;
  }

  .top-actions,
  .filters,
  .task-actions {
    flex-wrap: wrap;
  }

  .metrics-band,
  .nav-list,
  .source-cards,
  .dispatch-grid,
  .detail-list,
  .extract-grid,
  .login-matrix {
    grid-template-columns: 1fr;
  }

  .login-hero,
  .login-panel {
    padding: 18px;
  }

  .metrics-band article {
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .event-row {
    grid-template-columns: 64px minmax(0, 1fr);
    min-height: 112px;
  }

  .confidence {
    text-align: left;
  }

  .filters input,
  .filters select {
    width: 100%;
  }

  .timeline-list li {
    grid-template-columns: 54px minmax(0, 1fr);
    min-height: 102px;
  }

  .crawl-item-list li {
    grid-template-columns: 54px minmax(0, 1fr);
  }
}

/* Huashu Design · Direction A: Executive Command */
:root {
  --ink: #0c1714;
  --ink-muted: #66736f;
  --paper: #edf2f4;
  --paper-soft: #ffffff;
  --line: rgba(12, 23, 20, 0.1);
  --line-soft: rgba(12, 23, 20, 0.07);
  --accent: #f26b45;
  --green: #16845f;
  --warning: #c98f26;
  --danger: #c8472f;
  --surface: rgba(255, 255, 255, 0.86);
  --surface-solid: #ffffff;
  --soft-green: #eaf5ef;
  --soft-amber: #fff4ce;
  --soft-red: #fde5df;
  --shadow-soft: 0 24px 70px rgba(16, 27, 40, 0.1);
  --radius-xl: 28px;
  --radius-lg: 22px;
  --radius-md: 16px;
}

body {
  background:
    radial-gradient(circle at 18% 8%, rgba(38, 95, 242, 0.08), transparent 30%),
    radial-gradient(circle at 86% 18%, rgba(22, 132, 95, 0.08), transparent 28%),
    linear-gradient(180deg, #f6f8f7 0%, var(--paper) 100%);
  color: var(--ink);
  font-family: "Segoe UI Variable", "Microsoft YaHei UI", "PingFang SC", sans-serif;
}

h1,
h2,
.metrics-band strong,
.dispatch-grid strong {
  font-family: Georgia, "Songti SC", "STSong", serif;
}

h1 {
  font-size: 27px;
}

h2 {
  font-size: clamp(34px, 3.2vw, 52px);
  letter-spacing: 0;
}

h3 {
  font-size: 22px;
}

.admin-shell {
  grid-template-columns: 282px minmax(0, 1fr);
}

.sidebar {
  margin: 20px 0 20px 20px;
  min-height: calc(100vh - 40px);
  border-radius: var(--radius-xl);
  padding: 26px 18px;
  background: #0c1714;
  color: #f5f1e8;
  box-shadow: var(--shadow-soft);
}

.brand-block {
  grid-template-columns: 52px 1fr;
  gap: 14px;
}

.brand-mark {
  width: 52px;
  height: 52px;
  border: 0;
  border-radius: 16px;
  background: var(--accent);
  box-shadow: 0 16px 34px rgba(242, 107, 69, 0.28);
}

.sidebar .eyebrow {
  color: rgba(245, 241, 232, 0.58);
}

.nav-list {
  gap: 9px;
}

.nav-item {
  min-height: 50px;
  border: 0;
  border-radius: 16px;
  padding: 0 14px;
  color: rgba(245, 241, 232, 0.78);
  font-weight: 850;
}

.nav-item strong {
  border-radius: 10px;
  background: rgba(245, 241, 232, 0.1);
}

.nav-item.active {
  background: #f5f1e8;
  color: var(--ink);
}

.nav-item.active strong {
  background: var(--ink);
  color: #f5f1e8;
}

.agent-strip {
  padding: 16px;
  border: 1px solid rgba(245, 241, 232, 0.13);
  border-radius: 18px;
}

.pulse-dot {
  border-radius: 999px;
  background: #8ad3a4;
  box-shadow: 0 0 0 7px rgba(138, 211, 164, 0.14);
}

.workspace {
  padding: 28px;
  gap: 22px;
}

.topbar,
.metrics-band,
.main-grid,
.split-workspace,
.task-split-workspace,
.task-workspace,
.agent-workspace {
  width: min(100%, 1540px);
}

.topbar {
  padding: 4px 4px 2px;
  align-items: center;
}

.connection-line {
  margin-top: 12px;
}

.connection-line::before {
  border-radius: 999px;
}

.user-chip,
.schedule-badge,
.ghost-button,
.solid-button,
.filters select,
.filters input,
.segmented-control,
.status-pill,
.tag-row span,
.rule-stack span,
.login-footnote span {
  border-radius: 999px;
}

.user-chip {
  border-color: var(--line);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 10px 30px rgba(16, 27, 40, 0.05);
}

.ghost-button,
.solid-button {
  min-height: 44px;
  border-color: var(--line);
  padding: 0 18px;
  transition: transform 160ms ease, box-shadow 160ms ease, background 160ms ease;
}

.ghost-button {
  background: rgba(255, 255, 255, 0.72);
}

.solid-button {
  border-color: var(--ink);
  background: var(--ink);
  color: #fffaf2;
  box-shadow: 0 16px 34px rgba(12, 23, 20, 0.16);
}

.ghost-button:hover,
.solid-button:hover,
.nav-item:hover {
  transform: translateY(-1px);
}

.schedule-badge {
  border-color: rgba(22, 132, 95, 0.14);
  background: var(--soft-green);
  color: var(--green);
}

.metrics-band {
  gap: 12px;
  border: 0;
  background: transparent;
}

.metrics-band article {
  min-height: 122px;
  border: 1px solid var(--line);
  border-radius: 22px;
  background: var(--surface);
  box-shadow: 0 16px 46px rgba(16, 27, 40, 0.06);
}

.metrics-band article:last-child {
  border-right: 1px solid var(--line);
}

.metrics-band strong {
  font-size: 43px;
}

.review-panel,
.detail-panel,
.source-board,
.inspector-panel,
.task-console,
.agent-lab {
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: 0 18px 60px rgba(16, 27, 40, 0.07);
  overflow: hidden;
}

.main-grid,
.split-workspace,
.task-split-workspace,
.agent-workspace {
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 18px;
}

.panel-head {
  min-height: 70px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--line-soft);
}

.filters select,
.filters input {
  border-color: var(--line);
  background: #f8faf9;
}

.filters input {
  width: 260px;
}

.event-row {
  min-height: 86px;
  grid-template-columns: 92px minmax(0, 1fr) 92px 58px;
  border-bottom-color: var(--line-soft);
  padding: 0 20px;
}

.event-row:hover,
.event-row.selected,
.source-card:hover,
.source-card.selected,
.timeline-list li:hover,
.timeline-list li.selected,
.crawl-item-list li:hover,
.crawl-item-list li.selected {
  background: #f1f6ef;
}

.event-type,
.timeline-list time,
.crawl-item-main a {
  color: var(--accent);
}

.event-main strong,
.source-card strong,
.timeline-list strong,
.crawl-item-main strong {
  font-weight: 900;
}

.status-pill {
  border-color: rgba(12, 23, 20, 0.1);
  background: #f7f8f5;
}

.status-pill.large {
  width: fit-content;
  min-width: 82px;
  min-height: 34px;
  padding: 0 14px;
  align-self: start;
  justify-self: start;
}

.status-pill[data-status='AI_PUBLISHED'],
.status-pill[data-status='RULE'],
.status-pill[data-status='LLM'],
.status-pill[data-status='SUCCESS'],
.status-pill[data-status='DETAIL_SUCCESS'],
.status-pill[data-status='HEALTHY'] {
  background: var(--soft-green);
  border-color: rgba(22, 132, 95, 0.18);
  color: var(--green);
}

.status-pill[data-status='CORRECTED'],
.status-pill[data-status='NEEDS_AUTH'],
.status-pill[data-status='RUNNING'],
.status-pill[data-status='PENDING'],
.status-pill[data-status='REVIEW'],
.status-pill[data-status='LIST_ONLY'] {
  background: var(--soft-amber);
  border-color: rgba(201, 143, 38, 0.2);
  color: #966a16;
}

.status-pill[data-status='REJECTED'],
.status-pill[data-status='FAILED'],
.status-pill[data-status='DETAIL_FAILED'],
.status-pill[data-status='OFFLINE'],
.status-pill[data-status='PAUSED'] {
  background: var(--soft-red);
  border-color: rgba(200, 71, 47, 0.2);
  color: var(--danger);
}

.detail-panel,
.inspector-panel {
  padding: 20px;
  gap: 18px;
  align-self: start;
  position: sticky;
  top: 24px;
  max-height: calc(100vh - 48px);
  overflow: auto;
}

.detail-hero {
  gap: 14px;
  align-items: start;
}

.detail-list,
.stacked-list {
  gap: 12px;
}

.detail-list div,
.stacked-list div,
.extract-grid div {
  border: 0;
  border-radius: var(--radius-md);
  background: #f6f7f4;
}

.detail-list div {
  min-height: 88px;
  padding: 14px;
  align-content: start;
  gap: 18px;
}

.stacked-list div {
  min-height: 76px;
  padding: 14px;
  align-content: start;
  gap: 12px;
}

.summary-text {
  color: #34433f;
}

.tag-row span,
.rule-stack span {
  border-color: var(--line);
  background: #f8faf9;
}

.segmented-control {
  overflow: hidden;
  border-color: var(--line);
  background: #f8faf9;
}

.segmented-control button.active {
  background: var(--ink);
  color: #fffaf2;
}

.source-cards {
  gap: 12px;
  padding: 16px;
  background: transparent;
}

.source-card {
  min-height: 184px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 14px 34px rgba(16, 27, 40, 0.05);
}

.bar-track {
  height: 11px;
  border: 0;
  border-radius: 999px;
  overflow: hidden;
  background: #ecf0ee;
}

.bar-track i {
  border-radius: inherit;
  background: linear-gradient(90deg, var(--green), #7ac79a);
}

.task-workspace,
.agent-workspace {
  gap: 18px;
}

.timeline-list li,
.crawl-item-list li {
  grid-template-columns: 116px minmax(0, 1fr) 92px;
  padding: 0 20px;
  border-bottom-color: var(--line-soft);
}

.timeline-list time,
.crawl-item-list time {
  font-family: "Cascadia Mono", Consolas, monospace;
  font-size: 12px;
  font-weight: 900;
}

.crawl-item-list li {
  min-height: 122px;
}

.dispatch-grid,
.agent-chain,
.extract-grid {
  gap: 12px;
  border: 0;
  background: transparent;
}

.dispatch-grid article,
.agent-chain article,
.extract-grid div {
  border: 1px solid var(--line);
  border-radius: 20px;
  background: var(--surface);
  box-shadow: 0 14px 34px rgba(16, 27, 40, 0.05);
}

.dispatch-grid strong {
  font-size: 40px;
}

textarea {
  border-color: var(--line);
  border-radius: 18px;
  background: #f8faf9;
}

.login-shell {
  background:
    radial-gradient(circle at 20% 18%, rgba(242, 107, 69, 0.18), transparent 25%),
    linear-gradient(135deg, #f8faf9, #edf2f4);
}

.login-hero {
  margin: 24px 0 24px 24px;
  min-height: calc(100vh - 48px);
  border-radius: var(--radius-xl);
  background:
    radial-gradient(circle at 80% 14%, rgba(242, 107, 69, 0.18), transparent 28%),
    #0c1714;
  box-shadow: var(--shadow-soft);
}

.login-panel {
  min-height: 100vh;
  border-left: 0;
  background: transparent;
}

.login-form input {
  border-color: var(--line);
  border-radius: 16px;
  background: #ffffff;
}

.login-matrix {
  gap: 12px;
  border: 0;
  background: transparent;
}

.login-matrix article {
  border: 1px solid rgba(245, 241, 232, 0.12);
  border-radius: 18px;
  background: rgba(245, 241, 232, 0.07);
}

.login-error {
  border-radius: 16px;
}

@media (max-width: 1180px) {
  .sidebar,
  .login-hero {
    margin: 0;
    border-radius: 0;
    min-height: auto;
  }

  .nav-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .workspace {
    padding: 14px;
  }

  h2 {
    font-size: 34px;
  }

  .metrics-band {
    gap: 10px;
  }

  .metrics-band article {
    min-height: 104px;
  }

  .event-row,
  .timeline-list li,
  .crawl-item-list li {
    grid-template-columns: 1fr;
    gap: 8px;
    padding: 14px;
  }

  .filters input {
    width: 100%;
  }
}
</style>
