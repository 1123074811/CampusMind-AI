<script setup lang="ts">
import { computed, ref } from 'vue';
import type { ReviewEvent } from '../adminTypes';

const props = defineProps<{ events: ReviewEvent[] }>();
defineEmits<{ refresh: [] }>();

const filter = ref('ALL');
const selectedId = ref(0);
const visible = computed(() => filter.value === 'ALL' ? props.events : props.events.filter((item) => item.aiStatus === filter.value));
const selected = computed(() => props.events.find((item) => item.id === selectedId.value) ?? visible.value[0]);
const statusLabel = (status: string) => ({ PENDING: '待处理', SUCCESS: '提取成功', REVIEW: '需复核', FAILED: '提取失败' }[status] ?? '待处理');
const typeLabel = (type: string) => ({ NOTICE: '通知', COURSE: '课程', EXAM: '考试', HOMEWORK: '作业', ACTIVITY: '活动', LECTURE: '讲座', COMPETITION: '竞赛', SERVICE: '服务', OTHER: '其他' }[type] ?? '其他');
const cardText = (key: string) => {
  const value = selected.value?.aiCard[key];
  return Array.isArray(value) ? value.join('、') : String(value || '未提取');
};
</script>

<template>
  <section class="main-grid">
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
          <span class="confidence">{{ Math.round(item.confidence * 100) }}%</span>
        </button>
      </div>
    </section>
    <aside v-if="selected" class="detail-panel" aria-label="智能卡片详情">
      <div class="detail-hero"><p class="eyebrow">Extracted Card</p><h3>{{ selected.title }}</h3>
        <span class="status-pill large" :data-status="selected.aiStatus">{{ statusLabel(selected.aiStatus) }}</span>
      </div>
      <p class="summary-text">{{ selected.summary || '暂未生成摘要，可直接查看原文。' }}</p>
      <dl class="stacked-list">
        <div><dt>报名开始</dt><dd>{{ cardText('registrationStartTime') }}</dd></div>
        <div><dt>报名截止</dt><dd>{{ cardText('registrationDeadline') }}</dd></div>
        <div><dt>所需材料</dt><dd>{{ cardText('requiredMaterials') }}</dd></div>
        <div><dt>参与方式</dt><dd>{{ cardText('participationMethod') }}</dd></div>
      </dl>
      <a v-if="selected.sourceUrl" class="solid-button link-button" :href="selected.sourceUrl" target="_blank" rel="noreferrer">查看原文</a>
    </aside>
  </section>
</template>
