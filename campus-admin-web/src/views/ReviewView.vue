<script setup lang="ts">
import { computed, ref } from 'vue';
import StatusPill from '../components/StatusPill.vue';
import type { EventType, ReviewEvent } from '../adminTypes';

const props = defineProps<{
  events: ReviewEvent[];
  selectedId: number;
}>();

defineEmits<{
  select: [id: number];
  archive: [];
  unarchive: [];
  refresh: [];
  edit: [id: number, data: { title?: string; summary?: string; eventType?: string }];
  delete: [id: number];
}>();

const typeFilter = ref<'ALL' | EventType>('ALL');
const searchText = ref('');
const detailDialog = ref<HTMLDialogElement | null>(null);
const editDialog = ref<HTMLDialogElement | null>(null);
const editingEvent = ref<ReviewEvent | null>(null);
const editTitle = ref('');
const editSummary = ref('');
const editEventType = ref('');

function openEdit(event: ReviewEvent) {
  editingEvent.value = event;
  editTitle.value = event.title;
  editSummary.value = event.summary;
  editEventType.value = event.type;
  editDialog.value?.showModal();
}

function submitEdit(emit: any) {
  if (!editingEvent.value) return;
  emit('edit', editingEvent.value.id, {
    title: editTitle.value,
    summary: editSummary.value,
    eventType: editEventType.value
  });
  editDialog.value?.close();
}

function confirmDelete(emit: any, id: number, title: string) {
  if (confirm(`确认删除事件「${title}」？`)) {
    emit('delete', id);
  }
}

const filteredEvents = computed(() => {
  const keyword = searchText.value.trim();
  return props.events.filter((item) => {
    const matchType = typeFilter.value === 'ALL' || item.type === typeFilter.value;
    const matchKeyword = !keyword || `${item.title}${item.source}${item.summary}${item.tags.join('')}`.includes(keyword);
    return matchType && matchKeyword;
  });
});

const selectedEvent = computed(() => {
  return props.events.find((item) => item.id === props.selectedId) ?? filteredEvents.value[0] ?? props.events[0];
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
</script>

<template>
  <section class="main-grid">
    <section class="review-panel" aria-label="校园事件列表">
      <div class="panel-head">
        <div>
          <p class="eyebrow">Event Stream</p>
          <h3>校园事件列表</h3>
        </div>
        <div class="filters">
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
          <input v-model="searchText" type="search" placeholder="搜索标题、来源、标签" />
          <button type="button" class="ghost-button tiny" @click="$emit('refresh')">刷新</button>
        </div>
      </div>

      <div class="event-list">
        <button
          v-for="item in filteredEvents"
          :key="item.id"
          class="event-row"
          :class="{ selected: selectedEvent?.id === item.id }"
          type="button"
          @click="$emit('select', item.id)"
        >
          <span class="event-type">{{ eventTypeLabel(item.type) }}</span>
          <span class="event-main">
            <strong>{{ item.title }}</strong>
            <small>{{ item.source }} · {{ item.startTime }} · {{ item.location }}</small>
          </span>
          <StatusPill :status="item.status" />
          <span class="confidence">
            <button class="row-action-btn" type="button" title="编辑" @click.stop="openEdit(item)">✎</button>
            <button class="row-action-btn danger" type="button" title="删除" @click.stop="confirmDelete($emit, item.id, item.title)">✕</button>
          </span>
        </button>
      </div>
    </section>

    <aside v-if="selectedEvent" class="detail-panel" aria-label="事件详情">
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
          <dd>{{ selectedEvent.location }}</dd>
        </div>
        <div>
          <dt>对象</dt>
          <dd>{{ selectedEvent.scope }}</dd>
        </div>
        <div>
          <dt>状态说明</dt>
          <dd>{{ selectedEvent.risk }}</dd>
        </div>
      </dl>

      <p class="summary-text">{{ selectedEvent.summary }}</p>

      <div class="tag-row">
        <span v-for="tag in selectedEvent.tags" :key="tag">{{ tag }}</span>
      </div>

      <div class="decision-actions">
        <button type="button" class="ghost-button" @click="openDetail">查看完整详情</button>
        <button v-if="selectedEvent.status !== 'OFFLINE'" type="button" class="ghost-button danger" @click="$emit('archive')">下线</button>
        <button v-else type="button" class="solid-button" @click="$emit('unarchive')">恢复展示</button>
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
        </dl>
        <p class="summary-text dialog-content">{{ selectedEvent.summary }}</p>
        <a v-if="selectedEvent.sourceUrl" class="source-url" :href="selectedEvent.sourceUrl" target="_blank" rel="noreferrer">打开原文</a>
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
        <form class="settings-form" @submit.prevent="submitEdit($emit)">
          <label><span>标题</span><input v-model="editTitle" type="text" required /></label>
          <label><span>事件类型</span>
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
          <label><span>摘要</span><textarea v-model="editSummary" rows="4" style="width:100%;min-height:100px;margin:0;border:1px solid var(--line);border-radius:12px;background:#fff;padding:12px;"></textarea></label>
          <button class="solid-button" type="submit">保存修改</button>
        </form>
      </template>
    </dialog>
  </section>
</template>
