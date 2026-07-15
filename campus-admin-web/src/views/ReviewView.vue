<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import StatusPill from '../components/StatusPill.vue';
import type { EventImpact, EventType, ReviewEvent } from '../adminTypes';

const props = defineProps<{
  events: ReviewEvent[];
  selectedId: number;
  impact: EventImpact | null;
  impactLoading: boolean;
}>();

const emit = defineEmits<{
  select: [id: number];
  archive: [];
  unarchive: [];
  refresh: [];
  edit: [id: number, data: { title?: string; summary?: string; eventType?: string }];
  delete: [id: number];
  batchOffline: [ids: number[]];
  requestImpact: [id: number];
}>();

type QueueTab = 'LIVE' | 'ALL' | 'UPDATED' | 'OFFLINE';

const queueTab = ref<QueueTab>('LIVE');
const typeFilter = ref<'ALL' | EventType>('ALL');
const searchText = ref('');
const sourceFilter = ref('');
const submitterFilter = ref('');
const dateFilter = ref('');
const selectedIds = ref<number[]>([]);

/* ── Custom dropdown ── */
type FilterKey = 'type' | 'source' | 'submitter';
const openFilter = ref<FilterKey | null>(null);
const filterBarRef = ref<HTMLElement | null>(null);
const dateInputRef = ref<HTMLInputElement | null>(null);

function openDatePicker() {
  dateInputRef.value?.showPicker?.();
}

const typeOptions: Array<{ value: string; label: string }> = [
  { value: 'ALL', label: '全部' }, { value: 'LECTURE', label: '讲座' },
  { value: 'COURSE', label: '课程' }, { value: 'EXAM', label: '考试' },
  { value: 'HOMEWORK', label: '作业' }, { value: 'ACTIVITY', label: '活动' },
  { value: 'COMPETITION', label: '竞赛' }, { value: 'SERVICE', label: '服务' },
  { value: 'NOTICE', label: '通知' }, { value: 'OTHER', label: '其他' }
];

function labelOf(options: Array<{ value: string; label: string }>, val: string) {
  return options.find(o => o.value === val)?.label ?? val;
}
const typeLabel = computed(() => labelOf(typeOptions, typeFilter.value));
const sourceLabel = computed(() => sourceFilter.value || '全部');
const submitterLabel = computed(() => submitterFilter.value || '全部');
const sourceOptionsList = computed(() => [{ value: '', label: '全部' }, ...sourceOptions.value.map(s => ({ value: s, label: s }))]);
const submitterOptionsList = computed(() => [{ value: '', label: '全部' }, ...submitterOptions.value.map(s => ({ value: s, label: s }))]);

function toggleFilter(key: FilterKey) {
  openFilter.value = openFilter.value === key ? null : key;
}
function pickFilter(key: FilterKey, val: string) {
  if (key === 'type') typeFilter.value = val as typeof typeFilter.value;
  else if (key === 'source') sourceFilter.value = val;
  else if (key === 'submitter') submitterFilter.value = val;
  openFilter.value = null;
}
function onClickOutside(e: MouseEvent) {
  if (filterBarRef.value && !filterBarRef.value.contains(e.target as Node)) {
    openFilter.value = null;
  }
}
const detailDialog = ref<HTMLDialogElement | null>(null);
const editDialog = ref<HTMLDialogElement | null>(null);
const editingEvent = ref<ReviewEvent | null>(null);
const editTitle = ref('');
const editSummary = ref('');
const editEventType = ref('');

function cardText(event: ReviewEvent, key: string) {
  const value = event.aiCard?.[key];
  if (value == null) return '';
  if (Array.isArray(value)) return value.map(String).join('、');
  return String(value);
}

function confidenceOf(event: ReviewEvent): number | null {
  const raw = event.aiCard?.confidence ?? event.aiCard?.aiConfidence;
  if (typeof raw === 'number') return raw;
  if (typeof raw === 'string' && raw.trim() && !Number.isNaN(Number(raw))) return Number(raw);
  return null;
}

const queueCounts = computed(() => ({
  LIVE: props.events.filter((item) => item.status !== 'OFFLINE' && item.status !== 'REJECTED').length,
  ALL: props.events.length,
  UPDATED: props.events.filter((item) => item.status === 'CORRECTED').length,
  OFFLINE: props.events.filter((item) => item.status === 'OFFLINE').length
}));

const sourceOptions = computed(() => {
  return [...new Set(props.events.map((item) => item.source).filter(Boolean))].sort();
});

const submitterOptions = computed(() => {
  return [...new Set(props.events.map((item) => item.submittedBy).filter((v): v is string => !!v))].sort();
});

const dateOptions = computed(() => {
  return [...new Set(props.events
    .map((item) => item.startTime.match(/^\d{4}-\d{2}-\d{2}/)?.[0])
    .filter((value): value is string => !!value))].sort().reverse();
});

const filteredEvents = computed(() => {
  const keyword = searchText.value.trim().toLowerCase();
  return props.events.filter((item) => {
    if (queueTab.value === 'LIVE' && (item.status === 'OFFLINE' || item.status === 'REJECTED')) return false;
    if (queueTab.value === 'UPDATED' && item.status !== 'CORRECTED') return false;
    if (queueTab.value === 'OFFLINE' && item.status !== 'OFFLINE') return false;
    if (typeFilter.value !== 'ALL' && item.type !== typeFilter.value) return false;
    if (sourceFilter.value && item.source !== sourceFilter.value) return false;
    if (submitterFilter.value && item.submittedBy !== submitterFilter.value) return false;
    if (dateFilter.value && !item.startTime.startsWith(dateFilter.value)) return false;
    if (!keyword) return true;
    const haystack = `${item.title}${item.source}${item.summary}${item.tags.join('')}${item.submittedBy ?? ''}`.toLowerCase();
    return haystack.includes(keyword);
  });
});

const selectedEvent = computed(() => {
  return props.events.find((item) => item.id === props.selectedId)
    ?? filteredEvents.value[0]
    ?? props.events[0];
});

const allVisibleSelected = computed(() => {
  return filteredEvents.value.length > 0
    && filteredEvents.value.every((item) => selectedIds.value.includes(item.id));
});

watch(selectedEvent, (event) => {
  if (event) emit('requestImpact', event.id);
}, { immediate: true });

watch(filteredEvents, (events) => {
  selectedIds.value = selectedIds.value.filter((id) => events.some((item) => item.id === id));
});

function eventTypeLabel(type: EventType) {
  return {
    NOTICE: '通知', COURSE: '课程', EXAM: '考试', HOMEWORK: '作业', ACTIVITY: '活动',
    LECTURE: '讲座', COMPETITION: '竞赛', SERVICE: '服务', OTHER: '其他'
  }[type];
}

function openDetail() {
  detailDialog.value?.showModal();
}

function openEdit(event: ReviewEvent) {
  editingEvent.value = event;
  editTitle.value = event.title;
  editSummary.value = event.summary;
  editEventType.value = event.type;
  editDialog.value?.showModal();
}

function submitEdit() {
  if (!editingEvent.value) return;
  emit('edit', editingEvent.value.id, {
    title: editTitle.value,
    summary: editSummary.value,
    eventType: editEventType.value
  });
  editDialog.value?.close();
}

function confirmDelete(id: number, title: string) {
  if (confirm(`确认删除事件「${title}」？`)) {
    emit('delete', id);
  }
}

function toggleSelect(id: number) {
  if (selectedIds.value.includes(id)) {
    selectedIds.value = selectedIds.value.filter((value) => value !== id);
  } else {
    selectedIds.value = [...selectedIds.value, id];
  }
}

function toggleSelectAllVisible() {
  if (allVisibleSelected.value) {
    selectedIds.value = selectedIds.value.filter((id) => !filteredEvents.value.some((item) => item.id === id));
    return;
  }
  const merged = new Set([...selectedIds.value, ...filteredEvents.value.map((item) => item.id)]);
  selectedIds.value = [...merged];
}

function batchOffline() {
  if (!selectedIds.value.length) return;
  if (!confirm(`确认批量下线 ${selectedIds.value.length} 条？相关提醒将被撤回。`)) return;
  emit('batchOffline', [...selectedIds.value]);
  selectedIds.value = [];
}

function archiveWithImpact() {
  const impact = props.impact;
  if (impact && (impact.pendingReminders + impact.dueReminders + impact.affectedUsers) > 0) {
    const ok = confirm(
      `下线将影响：\n- 待提醒 ${impact.pendingReminders} 条\n- 已到期提醒 ${impact.dueReminders} 条\n- 活跃投递 ${impact.activeDeliveries} 条\n- 用户 ${impact.affectedUsers} 人\n\n确认下线并撤回提醒？`
    );
    if (!ok) return;
  }
  emit('archive');
}

function selectRelative(offset: number) {
  const list = filteredEvents.value;
  if (!list.length) return;
  const currentId = selectedEvent.value?.id;
  const index = Math.max(0, list.findIndex((item) => item.id === currentId));
  const next = list[Math.min(list.length - 1, Math.max(0, index + offset))];
  if (next) emit('select', next.id);
}

function onKeydown(event: KeyboardEvent) {
  const target = event.target as HTMLElement | null;
  if (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) return;
  if (event.key === 'j' || event.key === 'ArrowDown') {
    event.preventDefault();
    selectRelative(1);
  } else if (event.key === 'k' || event.key === 'ArrowUp') {
    event.preventDefault();
    selectRelative(-1);
  } else if (event.key === 'e' && selectedEvent.value) {
    event.preventDefault();
    openEdit(selectedEvent.value);
  } else if (event.key === 'x' && selectedEvent.value) {
    event.preventDefault();
    archiveWithImpact();
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown);
  document.addEventListener('click', onClickOutside, true);
});
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown);
  document.removeEventListener('click', onClickOutside, true);
});
</script>

<template>
  <section class="main-grid review-workbench" aria-label="事件管理">
    <section class="review-panel" aria-label="事件列表">
      <div class="panel-head">
        <div>
          <p class="eyebrow">Published Events</p>
          <h3>事件管理</h3>
        </div>
        <div class="filters">
          <button type="button" class="ghost-button tiny" @click="emit('refresh')">刷新</button>
        </div>
      </div>

      <div class="queue-tabs" role="tablist" aria-label="队列筛选">
        <button type="button" :class="{ active: queueTab === 'LIVE' }" @click="queueTab = 'LIVE'">
          正在展示 <strong>{{ queueCounts.LIVE }}</strong>
        </button>
        <button type="button" :class="{ active: queueTab === 'ALL' }" @click="queueTab = 'ALL'">
          全部 <strong>{{ queueCounts.ALL }}</strong>
        </button>
        <button type="button" :class="{ active: queueTab === 'UPDATED' }" @click="queueTab = 'UPDATED'">
          原文更新 <strong>{{ queueCounts.UPDATED }}</strong>
        </button>
        <button type="button" :class="{ active: queueTab === 'OFFLINE' }" @click="queueTab = 'OFFLINE'">
          已下线 <strong>{{ queueCounts.OFFLINE }}</strong>
        </button>
      </div>

      <!-- ── 筛选行 ── -->
      <div class="filter-bar" ref="filterBarRef">
        <div class="filter-group">
          <!-- 类型 -->
          <div class="fd-wrap">
            <button type="button" class="filter-chip" :class="{ active: openFilter === 'type' }" @click.stop="toggleFilter('type')">
              <svg class="filter-icon" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M3 3a1 1 0 011-1h12a1 1 0 011 1v3a1 1 0 01-.293.707L12 11.414V15a1 1 0 01-.293.707l-2 2A1 1 0 018 17v-5.586L3.293 6.707A1 1 0 013 6V3z" clip-rule="evenodd"/></svg>
              <span class="filter-label">类型</span>
              <span class="filter-value">{{ typeLabel }}</span>
              <svg class="chevron" :class="{ open: openFilter === 'type' }" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clip-rule="evenodd"/></svg>
            </button>
            <Transition name="dd">
              <div v-if="openFilter === 'type'" class="fd-panel" @click.stop>
                <button v-for="o in typeOptions" :key="o.value" type="button"
                  :class="{ picked: typeFilter === o.value }" @click="pickFilter('type', o.value)">
                  {{ o.label }}
                </button>
              </div>
            </Transition>
          </div>

          <!-- 来源 -->
          <div class="fd-wrap">
            <button type="button" class="filter-chip" :class="{ active: openFilter === 'source' }" @click.stop="toggleFilter('source')">
              <svg class="filter-icon" viewBox="0 0 20 20" fill="currentColor"><path d="M10.707 2.293a1 1 0 00-1.414 0l-7 7a1 1 0 001.414 1.414L4 10.414V17a1 1 0 001 1h2a1 1 0 001-1v-2a1 1 0 011-1h2a1 1 0 011 1v2a1 1 0 001 1h2a1 1 0 001-1v-6.586l.293.293a1 1 0 001.414-1.414l-7-7z"/></svg>
              <span class="filter-label">来源</span>
              <span class="filter-value">{{ sourceLabel }}</span>
              <svg class="chevron" :class="{ open: openFilter === 'source' }" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clip-rule="evenodd"/></svg>
            </button>
            <Transition name="dd">
              <div v-if="openFilter === 'source'" class="fd-panel" @click.stop>
                <button v-for="o in sourceOptionsList" :key="o.value || '__all'" type="button"
                  :class="{ picked: sourceFilter === o.value }" @click="pickFilter('source', o.value)">
                  {{ o.label }}
                </button>
              </div>
            </Transition>
          </div>

          <!-- 提交人 -->
          <div class="fd-wrap">
            <button type="button" class="filter-chip" :class="{ active: openFilter === 'submitter' }" @click.stop="toggleFilter('submitter')">
              <svg class="filter-icon" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clip-rule="evenodd"/></svg>
              <span class="filter-label">提交人</span>
              <span class="filter-value">{{ submitterLabel }}</span>
              <svg class="chevron" :class="{ open: openFilter === 'submitter' }" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clip-rule="evenodd"/></svg>
            </button>
            <Transition name="dd">
              <div v-if="openFilter === 'submitter'" class="fd-panel" @click.stop>
                <button v-for="o in submitterOptionsList" :key="o.value || '__all'" type="button"
                  :class="{ picked: submitterFilter === o.value }" @click="pickFilter('submitter', o.value)">
                  {{ o.label }}
                </button>
              </div>
            </Transition>
          </div>

          <!-- 日期 -->
          <label class="filter-chip filter-chip-date" @click="openDatePicker">
            <svg class="filter-icon" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M6 2a1 1 0 00-1 1v1H4a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V6a2 2 0 00-2-2h-1V3a1 1 0 10-2 0v1H7V3a1 1 0 00-1-1zm0 5a1 1 0 000 2h8a1 1 0 100-2H6z" clip-rule="evenodd"/></svg>
            <span class="filter-label">日期</span>
            <input ref="dateInputRef" v-model="dateFilter" type="date" aria-label="日期过滤" />
            <button v-if="dateFilter" class="chip-clear" type="button" @click.stop="dateFilter = ''" title="清除">×</button>
          </label>
        </div>

        <div class="filter-search">
          <svg class="search-icon" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd"/></svg>
          <input v-model="searchText" type="search" placeholder="搜索标题、来源、标签、提交人…" />
          <button v-if="searchText" class="search-clear" type="button" @click="searchText = ''" title="清除">
            <svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/></svg>
          </button>
        </div>
      </div>

      <div class="batch-bar">
        <label class="batch-check">
          <input type="checkbox" :checked="allVisibleSelected" @change="toggleSelectAllVisible" />
          全选当前列表
        </label>
        <span class="batch-count">已选 {{ selectedIds.length }}</span>
        <button type="button" class="ghost-button tiny danger" :disabled="!selectedIds.length" @click="batchOffline">批量下线</button>
      </div>

      <div class="event-list">
        <div
          v-for="item in filteredEvents"
          :key="item.id"
          class="event-row workbench-row"
          :class="{ selected: selectedEvent?.id === item.id }"
        >
          <label class="row-check" @click.stop>
            <input type="checkbox" :checked="selectedIds.includes(item.id)" @change="toggleSelect(item.id)" />
          </label>
          <button class="event-pick" type="button" @click="emit('select', item.id)">
            <span class="event-type">{{ eventTypeLabel(item.type) }}</span>
            <span class="event-main">
              <strong>{{ item.title }}</strong>
              <small>
                {{ item.source }}
                <template v-if="item.submittedBy"> · {{ item.submittedBy }}</template>
                · {{ item.startTime }}
                <template v-if="confidenceOf(item) != null"> · 置信度 {{ confidenceOf(item)?.toFixed(2) }}</template>
              </small>
            </span>
            <StatusPill :status="item.status" />
          </button>
          <span class="confidence">
            <button class="row-action-btn" type="button" title="编辑" @click.stop="openEdit(item)">✎</button>
            <button class="row-action-btn danger" type="button" title="删除" @click.stop="confirmDelete(item.id, item.title)">✕</button>
          </span>
        </div>
        <p v-if="!filteredEvents.length" class="empty-queue">当前筛选条件下没有事件</p>
      </div>
    </section>

    <aside v-if="selectedEvent" class="detail-panel" aria-label="事件详情与对比">
      <div class="detail-hero">
        <p class="eyebrow">Selected Event</p>
        <h3>{{ selectedEvent.title }}</h3>
        <StatusPill :status="selectedEvent.status" large />
      </div>

      <dl class="detail-list">
        <div>
          <dt>时间</dt>
          <dd>{{ selectedEvent.startTime }}</dd>
        </div>
        <div>
          <dt>地点</dt>
          <dd>{{ selectedEvent.location || cardText(selectedEvent, 'location') || '—' }}</dd>
        </div>
        <div>
          <dt>对象</dt>
          <dd>{{ selectedEvent.scope || cardText(selectedEvent, 'audience') || '—' }}</dd>
        </div>
        <div v-if="selectedEvent.submittedBy">
          <dt>提交用户</dt>
          <dd>
            {{ selectedEvent.submittedBy }}
            <template v-if="selectedEvent.submittedByUserId"> (ID: {{ selectedEvent.submittedByUserId }})</template>
          </dd>
        </div>
        <div>
          <dt>AI 状态</dt>
          <dd>{{ selectedEvent.aiStatus }}{{ selectedEvent.aiNeedReview ? ' · 提取异常，不影响原文展示' : '' }}</dd>
        </div>
        <div>
          <dt>状态说明</dt>
          <dd>{{ selectedEvent.risk || '—' }}</dd>
        </div>
      </dl>

      <section class="event-summary" aria-label="事件摘要">
        <h4>摘要</h4>
        <p>{{ selectedEvent.summary || '暂无摘要' }}</p>
        <a
          v-if="selectedEvent.sourceUrl"
          class="source-url"
          :href="selectedEvent.sourceUrl"
          target="_blank"
          rel="noreferrer"
        >打开原文 <span aria-hidden="true">↗</span></a>
        <p v-else class="muted">暂无原文链接</p>
      </section>

      <div class="impact-box" aria-live="polite">
        <h4>下线影响预估</h4>
        <p v-if="impactLoading">正在计算影响面…</p>
        <p v-else-if="impact && impact.eventId === selectedEvent.id">
          将撤回待提醒 {{ impact.pendingReminders }} / 已到期 {{ impact.dueReminders }}，
          活跃投递 {{ impact.activeDeliveries }}，影响用户 {{ impact.affectedUsers }} 人。
        </p>
        <p v-else class="muted">选择事件后显示提醒与投递影响。</p>
      </div>

      <div class="tag-row">
        <span v-for="tag in selectedEvent.tags" :key="tag">{{ tag }}</span>
      </div>

      <div class="decision-actions">
        <button type="button" class="ghost-button" @click="selectedEvent && openEdit(selectedEvent)">修正</button>
        <button type="button" class="ghost-button" @click="openDetail">完整详情</button>
        <button v-if="selectedEvent.status !== 'OFFLINE'" type="button" class="ghost-button danger" @click="archiveWithImpact">下线</button>
        <button v-else type="button" class="solid-button" @click="emit('unarchive')">恢复展示</button>
      </div>
    </aside>

    <dialog ref="detailDialog" class="event-dialog">
      <template v-if="selectedEvent">
        <div class="dialog-head">
          <div>
            <p class="eyebrow">Information Detail</p>
            <h3>{{ selectedEvent.title }}</h3>
          </div>
          <button type="button" class="ghost-button tiny" @click="detailDialog?.close()">关闭</button>
        </div>
        <dl class="detail-list dialog-list">
          <div><dt>类型</dt><dd>{{ eventTypeLabel(selectedEvent.type) }}</dd></div>
          <div><dt>发布时间</dt><dd>{{ selectedEvent.startTime }}</dd></div>
          <div><dt>发布单位</dt><dd>{{ selectedEvent.organizer }}</dd></div>
          <div><dt>适用对象</dt><dd>{{ selectedEvent.scope }}</dd></div>
          <div v-if="selectedEvent.submittedBy">
            <dt>提交用户</dt>
            <dd>
              {{ selectedEvent.submittedBy }}
              <template v-if="selectedEvent.submittedByUserId"> (ID: {{ selectedEvent.submittedByUserId }})</template>
            </dd>
          </div>
        </dl>
        <p class="summary-text dialog-content">{{ selectedEvent.summary }}</p>
        <a
          v-if="selectedEvent.sourceUrl"
          class="source-url"
          :href="selectedEvent.sourceUrl"
          target="_blank"
          rel="noreferrer"
        >打开原文</a>
      </template>
    </dialog>

    <dialog ref="editDialog" class="event-dialog">
      <template v-if="editingEvent">
        <div class="dialog-head">
          <div>
            <p class="eyebrow">Edit Event</p>
            <h3>编辑事件</h3>
          </div>
          <button type="button" class="ghost-button tiny" @click="editDialog?.close()">关闭</button>
        </div>
        <form class="settings-form" @submit.prevent="submitEdit">
          <label><span>标题</span><input v-model="editTitle" type="text" required /></label>
          <label>
            <span>事件类型</span>
            <select v-model="editEventType">
              <option value="NOTICE">通知</option>
              <option value="COURSE">课程</option>
              <option value="EXAM">考试</option>
              <option value="HOMEWORK">作业</option>
              <option value="ACTIVITY">活动</option>
              <option value="LECTURE">讲座</option>
              <option value="COMPETITION">竞赛</option>
              <option value="SERVICE">服务</option>
              <option value="OTHER">其他</option>
            </select>
          </label>
          <label>
            <span>摘要</span>
            <textarea
              v-model="editSummary"
              rows="4"
              style="width:100%;min-height:100px;margin:0;border:1px solid var(--line);border-radius:12px;background:#fff;padding:12px;"
            ></textarea>
          </label>
          <button class="solid-button" type="submit">保存修改</button>
        </form>
      </template>
    </dialog>
  </section>
</template>

<style scoped>
/* ═══════ Filter Bar ═══════ */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  background: linear-gradient(135deg, #f8f9fb 0%, #f1f3f7 100%);
  border-bottom: 1px solid var(--line-soft, #e8e4dc);
}
.filter-group {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ── Filter Chip (trigger button) ── */
.fd-wrap { position: relative; }

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 36px;
  padding: 0 10px 0 10px;
  border-radius: 10px;
  border: 1px solid var(--line, #d9d4c9);
  background: #fff;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
  cursor: pointer;
  transition: border-color .15s, box-shadow .15s, background .15s;
  white-space: nowrap;
  font-size: 13px;
  font-family: inherit;
  color: var(--ink, #293336);
  line-height: 1;
}
.filter-chip:hover {
  border-color: #b0a898;
  box-shadow: 0 2px 6px rgba(0,0,0,0.07);
}
.filter-chip.active {
  border-color: #7c8a6e;
  box-shadow: 0 0 0 3px rgba(124,138,110,0.15);
  background: #f9faf8;
}

.filter-icon {
  width: 15px; height: 15px;
  flex-shrink: 0;
  color: #9ca3af;
  pointer-events: none;
}
.filter-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #9ca3af;
  pointer-events: none;
  user-select: none;
}
.filter-value {
  font-weight: 600;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
}
.chevron {
  width: 14px; height: 14px;
  flex-shrink: 0;
  color: #b0b8c0;
  transition: transform .2s;
  pointer-events: none;
}
.chevron.open { transform: rotate(180deg); }

/* ── Dropdown Panel ── */
.fd-panel {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  z-index: 200;
  min-width: 140px;
  max-height: 240px;
  overflow-y: auto;
  padding: 4px;
  border-radius: 12px;
  border: 1px solid var(--line, #d9d4c9);
  background: #fff;
  box-shadow: 0 8px 24px rgba(0,0,0,0.10), 0 2px 8px rgba(0,0,0,0.06);
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.fd-panel button {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  color: var(--ink, #293336);
  cursor: pointer;
  text-align: left;
  transition: background .12s;
  white-space: nowrap;
}
.fd-panel button:hover { background: #f3f4f6; }
.fd-panel button.picked {
  background: #eef2e9;
  color: #4a6340;
  font-weight: 700;
}
.fd-panel button.picked::before {
  content: '';
  display: inline-block;
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #6b8f5e;
  flex-shrink: 0;
}

/* dropdown transition */
.dd-enter-active, .dd-leave-active { transition: opacity .15s, transform .15s; }
.dd-enter-from, .dd-leave-to { opacity: 0; transform: translateY(-4px); }

/* ── Date chip ── */
.filter-chip-date {
  position: relative;
}
.filter-chip-date input[type="date"] {
  border: none; outline: none;
  background: transparent;
  font-size: 13px; font-weight: 600;
  color: var(--ink, #293336);
  padding: 0;
  min-height: unset; height: 100%;
  cursor: pointer;
  width: 120px;
  font-family: inherit;
}
.filter-chip-date input[type="date"]::-webkit-calendar-picker-indicator {
  opacity: 0.4; cursor: pointer; transition: opacity .15s;
}
.filter-chip-date input[type="date"]::-webkit-calendar-picker-indicator:hover { opacity: 0.8; }

.chip-clear {
  display: inline-flex; align-items: center; justify-content: center;
  width: 18px; height: 18px;
  border: none; background: #eee; border-radius: 50%;
  font-size: 13px; color: #888; cursor: pointer;
  padding: 0; line-height: 1;
  transition: background .12s;
}
.chip-clear:hover { background: #ddd; }

/* ── Search ── */
.filter-search {
  position: relative;
  display: flex; align-items: center;
  flex: 1 1 0;
  min-width: 120px;
}
.filter-search .search-icon {
  position: absolute; left: 10px;
  width: 16px; height: 16px;
  color: #9ca3af; pointer-events: none;
}
.filter-search input {
  width: 100%; height: 36px;
  padding: 0 30px 0 32px;
  border-radius: 10px;
  border: 1px solid var(--line, #d9d4c9);
  background: #fff;
  font-size: 13px;
  color: var(--ink, #293336);
  outline: none;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
  transition: border-color .15s, box-shadow .15s;
}
.filter-search input::placeholder { color: #b8bec6; }
.filter-search input:focus {
  border-color: #7c8a6e;
  box-shadow: 0 0 0 3px rgba(124,138,110,0.15);
}
.search-clear {
  position: absolute; right: 6px;
  display: flex; align-items: center; justify-content: center;
  width: 22px; height: 22px;
  border: none; background: #f0f0f0; border-radius: 50%;
  cursor: pointer; padding: 0; transition: background .15s;
}
.search-clear:hover { background: #e0e0e0; }
.search-clear svg { width: 14px; height: 14px; color: #6b7280; }

/* ── Responsive ── */
@media (max-width: 600px) {
  .filter-bar { padding: 10px 12px; gap: 8px; flex-wrap: wrap; }
  .filter-group { flex-wrap: wrap; }
  .filter-search { flex: 1 1 100%; min-width: unset; }
  .filter-chip { height: 34px; font-size: 12px; }
}

/* ── Event title overflow fix ── */
.event-main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}
</style>
