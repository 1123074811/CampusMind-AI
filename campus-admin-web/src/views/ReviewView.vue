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
  approve: [];
  reject: [];
  refresh: [];
  edit: [id: number, data: { title?: string; summary?: string; eventType?: string }];
  delete: [id: number];
  batchReview: [ids: number[], status: 'REVIEWED' | 'OFFLINE', comment: string];
  requestImpact: [id: number];
}>();

type QueueTab = 'QUEUE' | 'ALL' | 'PUBLISHED' | 'OFFLINE';

const queueTab = ref<QueueTab>('QUEUE');
const typeFilter = ref<'ALL' | EventType>('ALL');
const searchText = ref('');
const sourceFilter = ref('');
const submitterFilter = ref('');
const confidenceFilter = ref<'ALL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN'>('ALL');
const selectedIds = ref<number[]>([]);
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

function confidenceBucket(event: ReviewEvent) {
  const value = confidenceOf(event);
  if (value == null) return 'UNKNOWN';
  if (value >= 0.8) return 'HIGH';
  if (value >= 0.5) return 'MEDIUM';
  return 'LOW';
}

function needsHuman(event: ReviewEvent) {
  return event.aiNeedReview || event.aiStatus === 'REVIEW' || event.aiStatus === 'FAILED' || event.status === 'CORRECTED';
}

const queueCounts = computed(() => ({
  QUEUE: props.events.filter((item) => needsHuman(item) && item.status !== 'OFFLINE').length,
  ALL: props.events.length,
  PUBLISHED: props.events.filter((item) => item.status === 'AI_PUBLISHED' || item.status === 'REVIEWED').length,
  OFFLINE: props.events.filter((item) => item.status === 'OFFLINE').length
}));

const sourceOptions = computed(() => {
  return [...new Set(props.events.map((item) => item.source).filter(Boolean))].sort();
});

const submitterOptions = computed(() => {
  return [...new Set(props.events.map((item) => item.submittedBy).filter((v): v is string => !!v))].sort();
});

const filteredEvents = computed(() => {
  const keyword = searchText.value.trim().toLowerCase();
  return props.events.filter((item) => {
    if (queueTab.value === 'QUEUE' && !(needsHuman(item) && item.status !== 'OFFLINE')) return false;
    if (queueTab.value === 'PUBLISHED' && !(item.status === 'AI_PUBLISHED' || item.status === 'REVIEWED')) return false;
    if (queueTab.value === 'OFFLINE' && item.status !== 'OFFLINE') return false;
    if (typeFilter.value !== 'ALL' && item.type !== typeFilter.value) return false;
    if (sourceFilter.value && item.source !== sourceFilter.value) return false;
    if (submitterFilter.value && item.submittedBy !== submitterFilter.value) return false;
    if (confidenceFilter.value !== 'ALL' && confidenceBucket(item) !== confidenceFilter.value) return false;
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

function batchApprove() {
  if (!selectedIds.value.length) return;
  if (!confirm(`确认批量通过 ${selectedIds.value.length} 条？`)) return;
  emit('batchReview', [...selectedIds.value], 'REVIEWED', '批量通过');
  selectedIds.value = [];
}

function batchOffline() {
  if (!selectedIds.value.length) return;
  if (!confirm(`确认批量下线 ${selectedIds.value.length} 条？相关提醒将被撤回。`)) return;
  emit('batchReview', [...selectedIds.value], 'OFFLINE', '批量下线');
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
  } else if (event.key === 'a' && !event.ctrlKey && !event.metaKey) {
    event.preventDefault();
    emit('approve');
  } else if (event.key === 'r') {
    event.preventDefault();
    emit('reject');
  } else if (event.key === 'e' && selectedEvent.value) {
    event.preventDefault();
    openEdit(selectedEvent.value);
  } else if (event.key === 'x' && selectedEvent.value) {
    event.preventDefault();
    archiveWithImpact();
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown));
onUnmounted(() => window.removeEventListener('keydown', onKeydown));
</script>

<template>
  <section class="main-grid review-workbench" aria-label="审核工作台">
    <section class="review-panel" aria-label="审核队列">
      <div class="panel-head">
        <div>
          <p class="eyebrow">Review Queue</p>
          <h3>审核工作台</h3>
          <p class="queue-hint">快捷键：J/K 切换 · A 通过 · R 驳回 · E 修正 · X 下线</p>
        </div>
        <div class="filters">
          <button type="button" class="ghost-button tiny" @click="emit('refresh')">刷新</button>
        </div>
      </div>

      <div class="queue-tabs" role="tablist" aria-label="队列筛选">
        <button type="button" :class="{ active: queueTab === 'QUEUE' }" @click="queueTab = 'QUEUE'">
          待人工 <strong>{{ queueCounts.QUEUE }}</strong>
        </button>
        <button type="button" :class="{ active: queueTab === 'ALL' }" @click="queueTab = 'ALL'">
          全部 <strong>{{ queueCounts.ALL }}</strong>
        </button>
        <button type="button" :class="{ active: queueTab === 'PUBLISHED' }" @click="queueTab = 'PUBLISHED'">
          已发布 <strong>{{ queueCounts.PUBLISHED }}</strong>
        </button>
        <button type="button" :class="{ active: queueTab === 'OFFLINE' }" @click="queueTab = 'OFFLINE'">
          已下线 <strong>{{ queueCounts.OFFLINE }}</strong>
        </button>
      </div>

      <div class="filters workbench-filters">
        <select v-model="typeFilter" aria-label="事件类型过滤">
          <option value="ALL">全部类型</option>
          <option value="LECTURE">讲座</option>
          <option value="COURSE">课程</option>
          <option value="EXAM">考试</option>
          <option value="HOMEWORK">作业</option>
          <option value="ACTIVITY">活动</option>
          <option value="COMPETITION">竞赛</option>
          <option value="SERVICE">服务</option>
          <option value="NOTICE">通知</option>
          <option value="OTHER">其他</option>
        </select>
        <select v-model="sourceFilter" aria-label="来源过滤">
          <option value="">全部来源</option>
          <option v-for="source in sourceOptions" :key="source" :value="source">{{ source }}</option>
        </select>
        <select v-model="submitterFilter" aria-label="提交人过滤">
          <option value="">全部提交人</option>
          <option v-for="name in submitterOptions" :key="name" :value="name">{{ name }}</option>
        </select>
        <select v-model="confidenceFilter" aria-label="置信度过滤">
          <option value="ALL">全部置信度</option>
          <option value="HIGH">高 (≥0.8)</option>
          <option value="MEDIUM">中 (0.5-0.8)</option>
          <option value="LOW">低 (&lt;0.5)</option>
          <option value="UNKNOWN">未知</option>
        </select>
        <input v-model="searchText" type="search" placeholder="搜索标题、来源、标签、提交人" />
      </div>

      <div class="batch-bar">
        <label class="batch-check">
          <input type="checkbox" :checked="allVisibleSelected" @change="toggleSelectAllVisible" />
          全选当前列表
        </label>
        <span class="batch-count">已选 {{ selectedIds.length }}</span>
        <button type="button" class="ghost-button tiny" :disabled="!selectedIds.length" @click="batchApprove">批量通过</button>
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
            <span v-if="needsHuman(item)" class="need-review-flag">待审</span>
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
          <dd>{{ selectedEvent.aiStatus }}{{ selectedEvent.aiNeedReview ? ' · 需复核' : '' }}</dd>
        </div>
        <div>
          <dt>状态说明</dt>
          <dd>{{ selectedEvent.risk || '—' }}</dd>
        </div>
      </dl>

      <section class="diff-grid" aria-label="原文与抽取对比">
        <article>
          <h4>当前展示摘要</h4>
          <p>{{ selectedEvent.summary || '暂无摘要' }}</p>
        </article>
        <article>
          <h4>AI 抽取字段</h4>
          <ul>
            <li><strong>标题</strong>{{ cardText(selectedEvent, 'title') || selectedEvent.title }}</li>
            <li><strong>类型</strong>{{ cardText(selectedEvent, 'eventType') || selectedEvent.type }}</li>
            <li><strong>地点</strong>{{ cardText(selectedEvent, 'location') || selectedEvent.location || '—' }}</li>
            <li><strong>组织方</strong>{{ cardText(selectedEvent, 'organizer') || selectedEvent.organizer || '—' }}</li>
            <li><strong>对象</strong>{{ cardText(selectedEvent, 'audience') || selectedEvent.scope || '—' }}</li>
            <li><strong>行动</strong>{{ cardText(selectedEvent, 'requiredActions') || '—' }}</li>
          </ul>
        </article>
        <article>
          <h4>原文入口</h4>
          <p>{{ selectedEvent.source }}</p>
          <a
            v-if="selectedEvent.sourceUrl"
            class="source-url"
            :href="selectedEvent.sourceUrl"
            target="_blank"
            rel="noreferrer"
          >打开原文</a>
          <p v-else class="muted">暂无原文链接</p>
        </article>
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
        <button type="button" class="solid-button" @click="emit('approve')">通过展示</button>
        <button type="button" class="ghost-button" @click="selectedEvent && openEdit(selectedEvent)">修正</button>
        <button type="button" class="ghost-button" @click="openDetail">完整详情</button>
        <button v-if="selectedEvent.status !== 'OFFLINE'" type="button" class="ghost-button danger" @click="archiveWithImpact">下线</button>
        <button v-else type="button" class="solid-button" @click="emit('unarchive')">恢复展示</button>
        <button type="button" class="ghost-button danger" @click="emit('reject')">驳回下线</button>
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
