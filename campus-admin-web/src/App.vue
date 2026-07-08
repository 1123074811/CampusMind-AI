<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { fetchDashboard, reviewEvent } from './api/dashboard';
import { clearSession, loadSession } from './api/auth';
import AdminSidebar from './components/AdminSidebar.vue';
import AdminTopbar from './components/AdminTopbar.vue';
import MetricsBand from './components/MetricsBand.vue';
import { initialCrawlTasks, initialDataSources, initialReviewEvents, navItems } from './adminDemoData';
import type { AdminSession, CrawlTask, DashboardMetrics, DataSource, NavKey, ReviewEvent } from './adminTypes';
import AgentsView from './views/AgentsView.vue';
import LoginView from './views/LoginView.vue';
import ReviewView from './views/ReviewView.vue';
import SourcesView from './views/SourcesView.vue';
import TasksView from './views/TasksView.vue';

const session = ref<AdminSession | null>(loadSession());
const activeNav = ref<NavKey>('review');
const selectedId = ref(initialReviewEvents[0].id);
const reviewEvents = ref<ReviewEvent[]>(initialReviewEvents);
const dataSources = ref<DataSource[]>(initialDataSources);
const crawlTasks = ref<CrawlTask[]>(initialCrawlTasks);
const loading = ref(false);
const apiMode = ref<'live' | 'fallback'>('fallback');
const apiMessage = ref('使用内置演示数据');
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
  const sum = reviewEvents.value.reduce((total, item) => total + item.confidence, 0);
  return Math.round((sum / reviewEvents.value.length) * 100);
});

const metrics = computed<DashboardMetrics>(() => {
  if (apiMode.value === 'live') {
    return dashboardMetrics.value;
  }

  return {
    reviewCount: reviewCount.value,
    urgentCount: urgentCount.value,
    avgConfidence: avgConfidence.value,
    sourceSuccessRate: 91,
    sourcesNeedAuth: 1,
    vectorPending: 12
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
    selectedId.value = dashboard.events[0]?.id ?? selectedId.value;
    apiMode.value = 'live';
    apiMessage.value = '已连接后端数据库';
  } catch (error) {
    apiMode.value = 'fallback';
    apiMessage.value = '后端未连接，使用演示数据';
  } finally {
    loading.value = false;
  }
}

async function reviewSelected(status: 'REVIEWED' | 'REJECTED', comment: string) {
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
      apiMessage.value = '审核写入失败，已保留当前页面状态';
    }
  }

  event.status = status;
  event.risk = status === 'REVIEWED' ? '已人工确认' : '已标记为无效事件';
}

function approveSelected() {
  reviewSelected('REVIEWED', '字段完整，确认发布');
}

function rejectSelected() {
  reviewSelected('REJECTED', '后台人工驳回');
}

function handleAuthenticated(nextSession: AdminSession) {
  session.value = nextSession;
  apiMessage.value = nextSession.demo ? '演示会话，使用内置数据' : '已登录，正在同步后端数据';
  loadDashboard();
}

function logout() {
  clearSession();
  session.value = null;
  apiMode.value = 'fallback';
  apiMessage.value = '使用内置演示数据';
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
        @logout="logout"
      />
      <MetricsBand :metrics="metrics" />

      <ReviewView
        v-if="activeNav === 'review'"
        :events="reviewEvents"
        :selected-id="selectedId"
        @select="selectEvent"
        @approve="approveSelected"
        @reject="rejectSelected"
        @refresh="loadDashboard"
      />
      <SourcesView v-else-if="activeNav === 'sources'" :data-sources="dataSources" />
      <TasksView v-else-if="activeNav === 'tasks'" :tasks="crawlTasks" />
      <AgentsView v-else />
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
.filters {
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
.status-pill[data-status='RUNNING'],
.status-pill[data-status='RULE'],
.status-pill[data-status='LLM'] {
  background: #e7f1ea;
  border-color: #9cb7a1;
}

.status-pill[data-status='CORRECTED'],
.status-pill[data-status='NEEDS_AUTH'],
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
  min-height: 100%;
  padding: 18px;
  display: grid;
  gap: 18px;
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

.timeline-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
}

.timeline-list li {
  min-height: 74px;
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 86px;
  align-items: center;
  gap: 14px;
  border-bottom: 1px solid var(--line-soft);
  padding: 0 18px;
}

.timeline-list time {
  color: var(--accent);
  font-weight: 900;
}

.timeline-list div {
  min-width: 0;
  display: grid;
  gap: 4px;
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
  .filters {
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
}
</style>
