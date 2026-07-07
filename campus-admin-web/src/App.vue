<script setup lang="ts">
import { computed, ref } from 'vue';

type ReviewStatus = 'AI_PUBLISHED' | 'CORRECTED' | 'REVIEWED' | 'REJECTED';
type EventType = 'LECTURE' | 'EXAM' | 'HOMEWORK' | 'ACTIVITY' | 'NOTICE';
type SourceStatus = 'RUNNING' | 'HEALTHY' | 'NEEDS_AUTH' | 'PAUSED';
type TaskStatus = 'SUCCESS' | 'RUNNING' | 'FAILED' | 'PENDING';

interface ReviewEvent {
  id: number;
  title: string;
  source: string;
  type: EventType;
  status: ReviewStatus;
  confidence: number;
  location: string;
  startTime: string;
  scope: string;
  summary: string;
  risk: string;
  tags: string[];
}

interface DataSource {
  id: number;
  name: string;
  channel: string;
  status: SourceStatus;
  lastSync: string;
  successRate: number;
  pending: number;
}

interface CrawlTask {
  id: number;
  name: string;
  status: TaskStatus;
  owner: string;
  time: string;
  note: string;
}

const navItems = [
  { key: 'review', label: '事件审核', count: 18 },
  { key: 'sources', label: '数据源', count: 7 },
  { key: 'tasks', label: '采集任务', count: 11 },
  { key: 'agents', label: '智能体', count: 4 }
];

const reviewEvents = ref<ReviewEvent[]>([
  {
    id: 1001,
    title: '人工智能主题讲座通知',
    source: '软件学院官网',
    type: 'LECTURE',
    status: 'AI_PUBLISHED',
    confidence: 0.91,
    location: '图书馆报告厅',
    startTime: '07-08 19:00',
    scope: '软件学院本科生',
    summary: '软件学院将于 7 月 8 日举办 AI 主题讲座，面向软件学院本科生开放。',
    risk: '字段完整，建议快速确认',
    tags: ['AI', '讲座', '软件学院']
  },
  {
    id: 1002,
    title: '期末考试考场调整说明',
    source: '教务通知',
    type: 'EXAM',
    status: 'CORRECTED',
    confidence: 0.74,
    location: '一号教学楼',
    startTime: '07-11 09:30',
    scope: '2023 级',
    summary: '部分课程考试地点变更，学生需以最终考场清单为准。',
    risk: '地点存在冲突，需人工复核',
    tags: ['考试', '教务']
  },
  {
    id: 1003,
    title: '雨课堂作业提交提醒',
    source: '用户导入',
    type: 'HOMEWORK',
    status: 'AI_PUBLISHED',
    confidence: 0.68,
    location: '线上',
    startTime: '07-09 23:59',
    scope: 'SE101',
    summary: 'SE101 课程作业截止到 7 月 9 日 23:59，来源为用户粘贴 JSON。',
    risk: '来源为用户导入，建议抽查原文',
    tags: ['雨课堂', '作业']
  },
  {
    id: 1004,
    title: '创新创业竞赛报名开放',
    source: '学工系统',
    type: 'ACTIVITY',
    status: 'REVIEWED',
    confidence: 0.88,
    location: '学生事务中心',
    startTime: '07-15 18:00',
    scope: '全校学生',
    summary: '创新创业竞赛进入报名阶段，材料提交窗口开放至 7 月 15 日。',
    risk: '已审核，可观察报名链接有效性',
    tags: ['竞赛', '报名']
  }
]);

const dataSources = ref<DataSource[]>([
  { id: 1, name: '软件学院通知', channel: 'PUBLIC_WEB', status: 'RUNNING', lastSync: '2 分钟前', successRate: 98, pending: 3 },
  { id: 2, name: '教务处公告', channel: 'PUBLIC_WEB', status: 'HEALTHY', lastSync: '11 分钟前', successRate: 94, pending: 5 },
  { id: 3, name: '雨课堂导入', channel: 'USER_JSON', status: 'NEEDS_AUTH', lastSync: '24 分钟前', successRate: 81, pending: 8 },
  { id: 4, name: '用户截图 OCR', channel: 'USER_IMAGE', status: 'PAUSED', lastSync: '1 小时前', successRate: 76, pending: 2 }
]);

const crawlTasks = ref<CrawlTask[]>([
  { id: 501, name: '软件学院增量抓取', status: 'SUCCESS', owner: '感知 Agent', time: '18:20', note: '新增 3 条，重复 1 条' },
  { id: 502, name: '雨课堂 JSON 解析', status: 'RUNNING', owner: '认知 Agent', time: '18:18', note: '正在抽取课程作业字段' },
  { id: 503, name: '向量文本同步', status: 'PENDING', owner: '向量服务', time: '18:16', note: '等待事件审核结果' },
  { id: 504, name: '教务通知抓取', status: 'FAILED', owner: '感知 Agent', time: '18:11', note: '源站 403，需要检查白名单' }
]);

const activeNav = ref('review');
const selectedId = ref(reviewEvents.value[0].id);
const typeFilter = ref<'ALL' | EventType>('ALL');
const searchText = ref('');
const aiInput = ref('7月8日 19:00，软件学院将在图书馆报告厅举办人工智能主题讲座，面向软件学院本科生。');

const filteredEvents = computed(() => {
  const keyword = searchText.value.trim();
  return reviewEvents.value.filter((item) => {
    const matchType = typeFilter.value === 'ALL' || item.type === typeFilter.value;
    const matchKeyword = !keyword || `${item.title}${item.source}${item.summary}${item.tags.join('')}`.includes(keyword);
    return matchType && matchKeyword;
  });
});

const selectedEvent = computed(() => {
  return reviewEvents.value.find((item) => item.id === selectedId.value) ?? filteredEvents.value[0] ?? reviewEvents.value[0];
});

const urgentCount = computed(() => reviewEvents.value.filter((item) => item.confidence < 0.75 || item.status === 'CORRECTED').length);
const reviewCount = computed(() => reviewEvents.value.filter((item) => item.status === 'AI_PUBLISHED' || item.status === 'CORRECTED').length);
const avgConfidence = computed(() => {
  const sum = reviewEvents.value.reduce((total, item) => total + item.confidence, 0);
  return Math.round((sum / reviewEvents.value.length) * 100);
});
const aiPreview = computed(() => {
  const text = aiInput.value;
  const lecture = text.includes('讲座') || text.toLowerCase().includes('ai');
  const homework = text.includes('作业') || text.includes('截止');
  return {
    title: text.split(/[，。\n]/)[0] || '待抽取事件',
    type: homework ? 'HOMEWORK' : lecture ? 'LECTURE' : 'NOTICE',
    confidence: text.includes('地点') || text.includes('图书馆') ? 91 : 66,
    review: text.length < 20 ? '需要补充原文' : '可进入审核队列'
  };
});

function selectEvent(id: number) {
  selectedId.value = id;
}

function approveSelected() {
  const event = selectedEvent.value;
  if (!event) {
    return;
  }
  event.status = 'REVIEWED';
  event.risk = '已人工确认';
}

function rejectSelected() {
  const event = selectedEvent.value;
  if (!event) {
    return;
  }
  event.status = 'REJECTED';
  event.risk = '已标记为无效事件';
}

function statusLabel(status: ReviewStatus | SourceStatus | TaskStatus) {
  const labels: Record<string, string> = {
    AI_PUBLISHED: 'AI 预测',
    CORRECTED: '待复核',
    REVIEWED: '已确认',
    REJECTED: '已驳回',
    RUNNING: '运行中',
    HEALTHY: '健康',
    NEEDS_AUTH: '待授权',
    PAUSED: '暂停',
    SUCCESS: '成功',
    FAILED: '失败',
    PENDING: '等待'
  };
  return labels[status] ?? status;
}
</script>

<template>
  <main class="admin-shell">
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
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: activeNav === item.key }"
          type="button"
          @click="activeNav = item.key"
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

    <section class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">2026-07-07 · 18:30</p>
          <h2>校园事件运营工作台</h2>
        </div>
        <div class="top-actions">
          <button type="button" class="ghost-button">导出审计</button>
          <button type="button" class="solid-button">新增数据源</button>
        </div>
      </header>

      <section class="metrics-band" aria-label="今日指标">
        <article>
          <span>待处理</span>
          <strong>{{ reviewCount }}</strong>
          <small>{{ urgentCount }} 条高优先级</small>
        </article>
        <article>
          <span>平均置信度</span>
          <strong>{{ avgConfidence }}%</strong>
          <small>较昨日 +6%</small>
        </article>
        <article>
          <span>数据源成功率</span>
          <strong>91%</strong>
          <small>1 个源需授权</small>
        </article>
        <article>
          <span>向量待同步</span>
          <strong>12</strong>
          <small>审核后写入</small>
        </article>
      </section>

      <section class="main-grid">
        <section class="review-panel" aria-label="事件审核列表">
          <div class="panel-head">
            <div>
              <p class="eyebrow">Review Queue</p>
              <h3>AI 预测事件</h3>
            </div>
            <div class="filters">
              <select v-model="typeFilter" aria-label="事件类型过滤">
                <option value="ALL">全部类型</option>
                <option value="LECTURE">讲座</option>
                <option value="EXAM">考试</option>
                <option value="HOMEWORK">作业</option>
                <option value="ACTIVITY">活动</option>
                <option value="NOTICE">通知</option>
              </select>
              <input v-model="searchText" type="search" placeholder="搜索标题、来源、标签" />
            </div>
          </div>

          <div class="event-list">
            <button
              v-for="item in filteredEvents"
              :key="item.id"
              class="event-row"
              :class="{ selected: selectedEvent?.id === item.id }"
              type="button"
              @click="selectEvent(item.id)"
            >
              <span class="event-type">{{ item.type }}</span>
              <span class="event-main">
                <strong>{{ item.title }}</strong>
                <small>{{ item.source }} · {{ item.startTime }} · {{ item.location }}</small>
              </span>
              <span class="status-pill" :data-status="item.status">{{ statusLabel(item.status) }}</span>
              <span class="confidence">{{ Math.round(item.confidence * 100) }}%</span>
            </button>
          </div>
        </section>

        <aside class="detail-panel" aria-label="事件详情">
          <div class="detail-hero">
            <p class="eyebrow">Selected Event</p>
            <h3>{{ selectedEvent.title }}</h3>
            <span class="status-pill large" :data-status="selectedEvent.status">{{ statusLabel(selectedEvent.status) }}</span>
          </div>

          <dl class="detail-list">
            <div>
              <dt>时间</dt>
              <dd>{{ selectedEvent.startTime }}</dd>
            </div>
            <div>
              <dt>地点</dt>
              <dd>{{ selectedEvent.location }}</dd>
            </div>
            <div>
              <dt>对象</dt>
              <dd>{{ selectedEvent.scope }}</dd>
            </div>
            <div>
              <dt>风险</dt>
              <dd>{{ selectedEvent.risk }}</dd>
            </div>
          </dl>

          <p class="summary-text">{{ selectedEvent.summary }}</p>

          <div class="tag-row">
            <span v-for="tag in selectedEvent.tags" :key="tag">{{ tag }}</span>
          </div>

          <div class="decision-actions">
            <button type="button" class="ghost-button danger" @click="rejectSelected">驳回</button>
            <button type="button" class="solid-button" @click="approveSelected">确认发布</button>
          </div>
        </aside>
      </section>

      <section class="lower-grid">
        <section class="agent-panel" aria-label="AI 抽取预览">
          <div class="panel-head compact">
            <div>
              <p class="eyebrow">Cognition Agent</p>
              <h3>文本抽取预览</h3>
            </div>
            <span class="status-pill" data-status="RUNNING">规则兜底</span>
          </div>
          <textarea v-model="aiInput" aria-label="待解析文本"></textarea>
          <div class="extract-grid">
            <div>
              <span>标题</span>
              <strong>{{ aiPreview.title }}</strong>
            </div>
            <div>
              <span>类型</span>
              <strong>{{ aiPreview.type }}</strong>
            </div>
            <div>
              <span>置信度</span>
              <strong>{{ aiPreview.confidence }}%</strong>
            </div>
            <div>
              <span>审核建议</span>
              <strong>{{ aiPreview.review }}</strong>
            </div>
          </div>
        </section>

        <section class="source-panel" aria-label="数据源监控">
          <div class="panel-head compact">
            <div>
              <p class="eyebrow">Sources</p>
              <h3>数据源监控</h3>
            </div>
          </div>
          <div class="source-list">
            <article v-for="source in dataSources" :key="source.id" class="source-row">
              <div>
                <strong>{{ source.name }}</strong>
                <small>{{ source.channel }} · {{ source.lastSync }}</small>
              </div>
              <span class="status-pill" :data-status="source.status">{{ statusLabel(source.status) }}</span>
              <meter min="0" max="100" :value="source.successRate"></meter>
              <b>{{ source.pending }}</b>
            </article>
          </div>
        </section>

        <section class="task-panel" aria-label="采集任务">
          <div class="panel-head compact">
            <div>
              <p class="eyebrow">Tasks</p>
              <h3>任务时间线</h3>
            </div>
          </div>
          <ol class="task-list">
            <li v-for="task in crawlTasks" :key="task.id" :data-status="task.status">
              <time>{{ task.time }}</time>
              <div>
                <strong>{{ task.name }}</strong>
                <small>{{ task.owner }} · {{ task.note }}</small>
              </div>
              <span>{{ statusLabel(task.status) }}</span>
            </li>
          </ol>
        </section>
      </section>
    </section>
  </main>
</template>

<style scoped>
:global(*) {
  box-sizing: border-box;
}

:global(body) {
  margin: 0;
  min-width: 320px;
  background:
    linear-gradient(90deg, rgba(31, 49, 54, 0.035) 1px, transparent 1px),
    linear-gradient(180deg, rgba(31, 49, 54, 0.035) 1px, transparent 1px),
    #f4f0e7;
  background-size: 28px 28px;
  color: #172023;
  font-family: 'Aptos', 'Segoe UI', 'Microsoft YaHei UI', sans-serif;
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

.admin-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
}

.sidebar {
  min-height: 100vh;
  padding: 24px 18px;
  background: #172023;
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
  background: #e0593e;
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
  background: #f4f0e7;
  color: #172023;
  border-color: #f4f0e7;
}

.nav-item.active strong {
  background: #172023;
  color: #f4f0e7;
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
  gap: 20px;
}

.topbar,
.panel-head,
.metrics-band,
.main-grid,
.lower-grid {
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

.ghost-button,
.solid-button {
  min-height: 42px;
  border: 1px solid #172023;
  padding: 0 16px;
  background: transparent;
  color: #172023;
  font-weight: 800;
}

.solid-button {
  background: #172023;
  color: #fff9ed;
}

.ghost-button.danger {
  border-color: #9f3928;
  color: #9f3928;
}

.metrics-band {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid #c9c0af;
  background: rgba(255, 251, 241, 0.7);
}

.metrics-band article {
  min-height: 116px;
  padding: 18px;
  display: grid;
  align-content: space-between;
  border-right: 1px solid #c9c0af;
}

.metrics-band article:last-child {
  border-right: 0;
}

.metrics-band span,
.metrics-band small,
.event-main small,
.source-row small,
.task-list small,
.extract-grid span,
.detail-list dt {
  color: #68777b;
}

.metrics-band strong {
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: 40px;
  line-height: 1;
}

.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 20px;
  align-items: start;
}

.review-panel,
.detail-panel,
.agent-panel,
.source-panel,
.task-panel {
  border: 1px solid #c9c0af;
  background: rgba(255, 251, 241, 0.86);
}

.panel-head {
  padding: 18px;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid #c9c0af;
}

.panel-head.compact {
  padding: 16px;
}

.filters select,
.filters input {
  min-height: 40px;
  border: 1px solid #c9c0af;
  background: #fffaf0;
  padding: 0 12px;
  color: #172023;
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
  border-bottom: 1px solid #d8d0c0;
  background: transparent;
  color: #172023;
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr) 86px 52px;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  text-align: left;
}

.event-row:hover,
.event-row.selected {
  background: #efe5d2;
}

.event-type {
  font-size: 11px;
  font-weight: 900;
  color: #e0593e;
}

.event-main {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.event-main strong {
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
.status-pill[data-status='RUNNING'] {
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

.detail-panel {
  min-height: 100%;
  padding: 18px;
  display: grid;
  gap: 18px;
}

.detail-hero {
  display: grid;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid #c9c0af;
}

.detail-list {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-list div {
  min-height: 66px;
  border: 1px solid #d8d0c0;
  padding: 10px;
  display: grid;
  align-content: space-between;
}

.detail-list dt {
  font-size: 12px;
  font-weight: 800;
}

.detail-list dd {
  margin: 0;
  font-weight: 800;
}

.summary-text {
  line-height: 1.8;
  color: #314044;
  text-wrap: pretty;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-row span {
  border: 1px solid #c9c0af;
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

.lower-grid {
  display: grid;
  grid-template-columns: minmax(320px, 1.15fr) minmax(280px, 0.85fr) minmax(280px, 0.85fr);
  gap: 20px;
  align-items: stretch;
}

.agent-panel,
.source-panel,
.task-panel {
  min-height: 328px;
}

textarea {
  width: calc(100% - 32px);
  min-height: 112px;
  margin: 16px;
  resize: vertical;
  border: 1px solid #c9c0af;
  background: #fffaf0;
  color: #172023;
  padding: 12px;
  line-height: 1.7;
}

.extract-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  margin: 0 16px 16px;
  background: #c9c0af;
  border: 1px solid #c9c0af;
}

.extract-grid div {
  min-height: 72px;
  background: #fffaf0;
  padding: 12px;
  display: grid;
  align-content: space-between;
}

.extract-grid strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.source-list {
  display: grid;
}

.source-row {
  min-height: 62px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 76px 72px 24px;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid #d8d0c0;
}

.source-row div {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.source-row strong,
.source-row small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

meter {
  width: 72px;
  height: 8px;
}

.source-row b {
  text-align: right;
}

.task-list {
  margin: 0;
  padding: 8px 16px 16px;
  list-style: none;
  display: grid;
  gap: 0;
}

.task-list li {
  min-height: 62px;
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) 48px;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #d8d0c0;
}

.task-list time {
  color: #e0593e;
  font-weight: 900;
}

.task-list div {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.task-list strong,
.task-list small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-list span {
  font-size: 12px;
  font-weight: 900;
  text-align: right;
}

.task-list li[data-status='FAILED'] span {
  color: #9f3928;
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
  .lower-grid {
    grid-template-columns: 1fr;
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
  .nav-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metrics-band article:nth-child(2) {
    border-right: 0;
  }

  .metrics-band article {
    border-bottom: 1px solid #c9c0af;
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

  .detail-list,
  .extract-grid {
    grid-template-columns: 1fr;
  }

  .source-row {
    grid-template-columns: minmax(0, 1fr) 76px;
    min-height: 84px;
  }

  meter,
  .source-row b {
    display: none;
  }
}
</style>
