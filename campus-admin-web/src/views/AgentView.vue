<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { AiConfig, DashboardMetrics, ReviewEvent } from '../adminTypes';

const props = defineProps<{
  events: ReviewEvent[];
  metrics: DashboardMetrics;
  aiConfig: AiConfig | null;
  configLoading: boolean;
  configMessage: string;
}>();

const emit = defineEmits<{
  refresh: [];
  refreshConfig: [];
  saveConfig: [payload: { mode: 'rule' | 'llm'; baseUrl: string; model: string; apiKey?: string }];
}>();

const activeTab = ref<'cards' | 'settings'>('cards');
const filter = ref('ALL');
const selectedId = ref(0);

/* ---- Config form state ---- */
const cfgMode = ref<'rule' | 'llm'>('rule');
const cfgBaseUrl = ref('');
const cfgModel = ref('');
const cfgApiKey = ref('');
const showKey = ref(false);

watch(() => props.aiConfig, (config) => {
  if (!config) return;
  cfgMode.value = config.mode;
  cfgBaseUrl.value = config.baseUrl;
  cfgModel.value = config.model;
}, { immediate: true });

function saveConfig() {
  emit('saveConfig', {
    mode: cfgMode.value,
    baseUrl: cfgBaseUrl.value.trim(),
    model: cfgModel.value.trim(),
    apiKey: cfgApiKey.value || undefined
  });
  cfgApiKey.value = '';
}

/* ---- Event list ---- */
const visible = computed(() => filter.value === 'ALL' ? props.events : props.events.filter((item) => item.aiStatus === filter.value));
const selected = computed(() => props.events.find((item) => item.id === selectedId.value) ?? visible.value[0]);
const statusLabel = (status: string) => ({ PENDING: '待处理', SUCCESS: '提取成功', REVIEW: '需复核', FAILED: '提取失败' }[status] ?? '待处理');
const typeLabel = (type: string) => ({ NOTICE: '通知', COURSE: '课程', EXAM: '考试', HOMEWORK: '作业', ACTIVITY: '活动', LECTURE: '讲座', COMPETITION: '竞赛', SERVICE: '服务', OTHER: '其他' }[type] ?? '其他');

/* ---- Metric computations ---- */
const pendingCount = computed(() => props.events.filter((e) => e.aiStatus === 'PENDING').length);
const successCount = computed(() => props.events.filter((e) => e.aiStatus === 'SUCCESS').length);
const reviewCount = computed(() => props.events.filter((e) => e.aiStatus === 'REVIEW').length);
const failedCount = computed(() => props.events.filter((e) => e.aiStatus === 'FAILED').length);
const totalCount = computed(() => props.events.length);
const successRate = computed(() => {
  if (totalCount.value === 0) return 0;
  return Math.round((successCount.value / totalCount.value) * 100);
});

/* ---- AI batch processing stats from dashboard metrics ---- */
const aiPending = computed(() => props.metrics?.aiPendingCount ?? 0);
const aiProcessing = computed(() => props.metrics?.aiProcessingCount ?? 0);
const aiSuccess = computed(() => props.metrics?.aiSuccessCount ?? 0);
const aiFailed = computed(() => props.metrics?.aiFailedCount ?? 0);
const aiTotalCount = computed(() => aiPending.value + aiProcessing.value + aiSuccess.value + aiFailed.value);

/* ---- Action suggestions ---- */
const suggestions = computed(() => {
  const items: { text: string; type: 'warn' | 'info' | 'ok' }[] = [];
  if (aiPending.value > 10) items.push({ text: `${aiPending.value} 条待 AI 处理，建议检查数据源`, type: 'warn' });
  if (aiFailed.value > 0) items.push({ text: `${aiFailed.value} 条 AI 处理失败，建议重试`, type: 'warn' });
  if (pendingCount.value > 5) items.push({ text: `${pendingCount.value} 条待处理，建议尽快审核`, type: 'warn' });
  if (reviewCount.value > 0) items.push({ text: `${reviewCount.value} 条需复核，建议人工校验`, type: 'warn' });
  if (failedCount.value > 0) items.push({ text: `${failedCount.value} 条提取失败，建议检查数据源`, type: 'warn' });
  if (props.aiConfig?.mode === 'rule') items.push({ text: '当前为规则模式，切换 LLM 可提升质量', type: 'info' });
  if (pendingCount.value === 0 && failedCount.value === 0 && aiPending.value === 0) items.push({ text: '所有卡片处理完毕', type: 'ok' });
  if (items.length === 0) items.push({ text: '暂无待操作事项', type: 'ok' });
  return items.slice(0, 3);
});

const cardValue = (key: string) => {
  const value = selected.value?.aiCard[key];
  return Array.isArray(value) ? value.filter(Boolean).join('、') : String(value || '');
};
const detailRows = computed(() => {
  if (!selected.value) return [];
  const base = [
    ['时间', cardValue('startTime') || selected.value.startTime],
    ['结束时间', cardValue('endTime') || selected.value.endTime],
    ['地点', cardValue('location') || selected.value.location],
    ['主办方', cardValue('organizer') || selected.value.organizer],
    ['适用对象', cardValue('targetScopes') || selected.value.scope],
    ['提交用户', selected.value.submittedBy || ''],
    ['关键日期', cardValue('keyDates')],
    ['需办理事项', cardValue('requiredActions')],
    ['附件', cardValue('attachments')]
  ];
  const registration = ['COMPETITION', 'ACTIVITY'].includes(selected.value.type) ? [
    ['报名开始', cardValue('registrationStartTime')],
    ['报名截止', cardValue('registrationDeadline')],
    ['持续时间', cardValue('eventDuration')],
    ['所需材料', cardValue('requiredMaterials')],
    ['参与方式', cardValue('participationMethod')],
    ['组队要求', cardValue('teamRequirement')],
    ['报名网址', cardValue('registrationUrl')]
  ] : [];
  return [...base, ...registration].filter(([, value]) => value && value !== '-' && value !== '待补充');
});
</script>

<template>
  <div class="agent-page">
    <!-- 顶部实用指标卡片 -->
    <section class="agent-metrics" aria-label="智能体指标">
      <article class="metric-card">
        <p class="eyebrow">AI 批处理状态</p>
        <strong>{{ aiTotalCount }}</strong>
        <div class="metric-sub">
          <span class="metric-tag amber">{{ aiPending }} 待处理</span>
          <span v-if="aiProcessing" class="metric-tag amber">{{ aiProcessing }} 处理中</span>
          <span class="metric-tag green">{{ aiSuccess }} 成功</span>
          <span v-if="aiFailed" class="metric-tag red">{{ aiFailed }} 失败</span>
        </div>
      </article>
      <article class="metric-card">
        <p class="eyebrow">处理概览</p>
        <strong>{{ pendingCount }}</strong>
        <div class="metric-sub">
          <span class="metric-tag green">{{ successCount }} 成功</span>
          <span v-if="reviewCount" class="metric-tag amber">{{ reviewCount }} 复核</span>
          <span v-if="failedCount" class="metric-tag red">{{ failedCount }} 失败</span>
        </div>
      </article>
      <article class="metric-card">
        <p class="eyebrow">提取成功率</p>
        <strong>{{ successRate }}<small class="metric-unit">%</small></strong>
        <div class="metric-sub">
          <span class="metric-bar-track"><i class="metric-bar-fill" :style="{ width: successRate + '%' }"></i></span>
          <small>{{ totalCount }} 条事件</small>
        </div>
      </article>
      <article class="metric-card metric-card-config" @click="activeTab = 'settings'">
        <p class="eyebrow">模型配置</p>
        <strong class="config-mode-label">{{ aiConfig?.mode === 'llm' ? 'LLM' : '规则' }}</strong>
        <div class="metric-sub">
          <small v-if="aiConfig?.mode === 'llm'">{{ aiConfig.model }}</small>
          <small v-else>规则兜底模式</small>
          <button class="metric-config-btn" type="button" @click.stop="activeTab = 'settings'">配置</button>
        </div>
      </article>
      <article class="metric-card metric-card-suggest">
        <p class="eyebrow">待操作建议</p>
        <ul class="suggest-list">
          <li v-for="(s, i) in suggestions" :key="i" :class="'suggest-' + s.type">{{ s.text }}</li>
        </ul>
      </article>
    </section>

    <!-- Tab 切换 -->
    <div class="agent-tabs">
      <button type="button" class="agent-tab" :class="{ active: activeTab === 'cards' }" @click="activeTab = 'cards'">
        智能卡片
        <span class="agent-tab-count">{{ events.length }}</span>
      </button>
      <button type="button" class="agent-tab" :class="{ active: activeTab === 'settings' }" @click="activeTab = 'settings'">
        模型配置
      </button>
    </div>

    <!-- 智能卡片视图 -->
    <section v-if="activeTab === 'cards'" class="main-grid">
      <section class="review-panel" aria-label="智能提取任务">
        <div class="panel-head">
          <div><p class="eyebrow">AI Cards</p><h3>智能信息卡片</h3></div>
          <div class="filters">
            <select v-model="filter" aria-label="智能提取状态">
              <option value="ALL">全部状态</option><option value="SUCCESS">提取成功</option>
              <option value="REVIEW">需复核</option><option value="PENDING">待处理</option><option value="FAILED">提取失败</option>
            </select>
            <button class="ghost-button tiny" type="button" @click="$emit('refresh')">刷新</button>
          </div>
        </div>
        <div class="event-list">
          <button v-for="item in visible" :key="item.id" type="button" class="event-row"
            :class="{ selected: selected?.id === item.id }" @click="selectedId = item.id">
            <span class="event-type">{{ typeLabel(item.type) }}</span>
            <span class="event-main"><strong>{{ item.title }}</strong><small>{{ item.source }} · {{ item.summary }}</small></span>
            <span class="status-pill" :data-status="item.aiStatus">{{ statusLabel(item.aiStatus) }}</span>
            <span class="confidence">
              <button class="row-action-btn" type="button" title="编辑" @click.stop="selectedId = item.id">✎</button>
            </span>
          </button>
        </div>
      </section>
      <aside v-if="selected" class="detail-panel" aria-label="智能卡片详情">
        <div class="detail-hero"><p class="eyebrow">Extracted Card</p><h3>{{ selected.title }}</h3>
          <span class="status-pill large" :data-status="selected.aiStatus">{{ statusLabel(selected.aiStatus) }}</span>
        </div>
        <p class="summary-text">{{ selected.summary || '暂未生成摘要，可直接查看原文。' }}</p>
        <dl v-if="detailRows.length" class="stacked-list">
          <div v-for="([label, value]) in detailRows" :key="label"><dt>{{ label }}</dt><dd>{{ value }}</dd></div>
        </dl>
        <a v-if="selected.sourceUrl" class="solid-button link-button" :href="selected.sourceUrl" target="_blank" rel="noreferrer">查看原文</a>
      </aside>
    </section>

    <!-- 模型配置视图 -->
    <section v-else class="split-workspace">
      <section class="review-panel" aria-label="智能体配置">
        <div class="panel-head">
          <div><p class="eyebrow">Model Runtime</p><h3>智能体配置</h3></div>
          <button class="ghost-button tiny" type="button" @click="$emit('refreshConfig')">刷新</button>
        </div>
        <form class="settings-form" autocomplete="off" @submit.prevent="saveConfig">
          <label><span>分析模式</span>
            <select v-model="cfgMode" name="ai_mode">
              <option value="llm">大模型智能分析</option>
              <option value="rule">规则兜底模式</option>
            </select>
          </label>
          <template v-if="cfgMode === 'llm'">
            <label><span>Base URL</span><input v-model="cfgBaseUrl" type="url" name="ai_base_url" placeholder="https://api.deepseek.com" autocomplete="url" required /></label>
            <label><span>模型名</span><input v-model="cfgModel" type="text" name="ai_model" placeholder="deepseek-chat" autocomplete="off" required /></label>
            <label class="key-field"><span>API Key</span><div class="key-input-wrap"><input v-model="cfgApiKey" :type="showKey ? 'text' : 'password'" name="ai_api_key" :placeholder="aiConfig?.apiKeyConfigured ? '已配置，留空则不修改' : '请输入 API Key'" :required="!aiConfig?.apiKeyConfigured" autocomplete="new-password" spellcheck="false" /><button type="button" class="key-toggle-btn" @click="showKey = !showKey" :title="showKey ? '隐藏' : '显示'">{{ showKey ? '隐藏' : '显示' }}</button></div></label>
          </template>
          <button class="solid-button" type="submit" :disabled="configLoading">保存配置</button>
        </form>
      </section>
      <aside class="detail-panel" aria-label="配置生效说明">
        <div class="detail-hero"><p class="eyebrow">Runtime Status</p><h3>{{ aiConfig?.mode === 'llm' ? '大模型模式' : '规则兜底模式' }}</h3></div>
        <dl class="stacked-list">
          <div><dt>API Key</dt><dd>{{ aiConfig?.apiKeyConfigured ? '已配置（已脱敏）' : '未配置' }}</dd></div>
          <div><dt>当前模型</dt><dd>{{ aiConfig?.mode === 'llm' ? aiConfig.model : '规则引擎' }}</dd></div>
          <div><dt>生效方式</dt><dd>保存后即时生效</dd></div>
        </dl>
        <p class="summary-text">{{ configMessage || '配置保存在 .env 并即时热加载到 AI 服务，无需重启。API Key 不会在前端展示。' }}</p>
      </aside>
    </section>
  </div>
</template>

<style scoped>
/* Page root — simple vertical stack, no grid columns */
.agent-page {
  width: min(100%, 1540px);
  display: grid;
  gap: 18px;
  align-content: start;
}

/* Agent metrics cards */
.agent-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  width: min(100%, 1540px);
}
.metric-card {
  min-height: 130px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 22px;
  background: var(--surface);
  box-shadow: 0 16px 46px rgba(16, 27, 40, 0.06);
  display: grid;
  align-content: space-between;
  gap: 6px;
}
.metric-card strong {
  font-family: Georgia, "Songti SC", "STSong", serif;
  font-size: 42px;
  line-height: 1;
}
.metric-unit {
  font-size: 24px;
  color: var(--ink-muted);
  margin-left: 2px;
}
.metric-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.metric-sub small {
  color: var(--ink-muted);
  font-size: 13px;
}
.metric-tag {
  font-size: 12px;
  font-weight: 800;
  padding: 2px 8px;
  border-radius: 999px;
}
.metric-tag.green { background: var(--soft-green); color: var(--green); }
.metric-tag.amber { background: var(--soft-amber); color: #966a16; }
.metric-tag.red { background: var(--soft-red); color: var(--danger); }
.metric-bar-track {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: #ecf0ee;
  overflow: hidden;
  min-width: 60px;
}
.metric-bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--green), #7ac79a);
  transition: width 300ms ease;
}
.metric-card-config {
  cursor: pointer;
  transition: transform 160ms ease, box-shadow 160ms ease;
}
.metric-card-config:hover {
  transform: translateY(-2px);
  box-shadow: 0 20px 50px rgba(16, 27, 40, 0.1);
}
.config-mode-label {
  font-size: 36px !important;
}
.metric-config-btn {
  font-size: 12px;
  font-weight: 800;
  padding: 3px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--ink);
  cursor: pointer;
  margin-left: auto;
  transition: background 160ms ease;
}
.metric-config-btn:hover {
  background: var(--ink);
  color: #fffaf2;
}
.suggest-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 6px;
}
.suggest-list li {
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
  padding: 4px 0;
}
.suggest-warn { color: var(--warning); }
.suggest-info { color: var(--ink-muted); }
.suggest-ok { color: var(--green); }

/* Tab bar */
.agent-tabs {
  display: flex;
  gap: 6px;
  width: min(100%, 1540px);
}
.agent-tab {
  min-height: 44px;
  padding: 0 22px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--ink);
  font-weight: 800;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: background 160ms ease, transform 160ms ease;
}
.agent-tab:hover {
  transform: translateY(-1px);
}
.agent-tab.active {
  background: var(--ink);
  color: #fffaf2;
  border-color: var(--ink);
}
.agent-tab-count {
  min-width: 24px;
  height: 22px;
  display: inline-grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(12, 23, 20, 0.08);
  font-size: 12px;
  font-weight: 900;
}
.agent-tab.active .agent-tab-count {
  background: rgba(255, 250, 242, 0.18);
}

/* API Key password field + toggle */
.key-field {
  display: grid;
  gap: 6px;
}
.key-input-wrap {
  display: flex;
  gap: 8px;
  align-items: center;
}
.key-input-wrap input {
  flex: 1;
  min-width: 0;
}
.key-toggle-btn {
  flex-shrink: 0;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--ink-muted);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background 160ms ease, color 160ms ease;
  white-space: nowrap;
}
.key-toggle-btn:hover {
  background: var(--ink);
  color: #fffaf2;
}

@media (max-width: 1400px) {
  .agent-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 1180px) {
  .agent-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .agent-metrics { grid-template-columns: 1fr; }
}
</style>
