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
  approve: [];
  reject: [];
  refresh: [];
}>();

const typeFilter = ref<'ALL' | EventType>('ALL');
const searchText = ref('');

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
</script>

<template>
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
          <span class="event-type">{{ item.type }}</span>
          <span class="event-main">
            <strong>{{ item.title }}</strong>
            <small>{{ item.source }} · {{ item.startTime }} · {{ item.location }}</small>
          </span>
          <StatusPill :status="item.status" />
          <span class="confidence">{{ Math.round(item.confidence * 100) }}%</span>
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
          <dt>风险</dt>
          <dd>{{ selectedEvent.risk }}</dd>
        </div>
      </dl>

      <p class="summary-text">{{ selectedEvent.summary }}</p>

      <div class="tag-row">
        <span v-for="tag in selectedEvent.tags" :key="tag">{{ tag }}</span>
      </div>

      <div class="decision-actions">
        <button type="button" class="ghost-button danger" @click="$emit('reject')">驳回</button>
        <button type="button" class="solid-button" @click="$emit('approve')">确认发布</button>
      </div>
    </aside>
  </section>
</template>
