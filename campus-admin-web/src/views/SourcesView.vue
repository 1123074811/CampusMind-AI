<script setup lang="ts">
import { computed, ref } from 'vue';
import StatusPill from '../components/StatusPill.vue';
import type { DataSource, SourceStatus } from '../adminTypes';

const props = defineProps<{
  dataSources: DataSource[];
  crawlingSourceId: number | null;
}>();

defineEmits<{
  crawl: [id: number];
}>();

const statusFilter = ref<'ALL' | SourceStatus>('ALL');
const selectedSourceId = ref(props.dataSources[0]?.id ?? 0);

const filteredSources = computed(() => {
  if (statusFilter.value === 'ALL') {
    return props.dataSources;
  }
  return props.dataSources.filter((source) => source.status === statusFilter.value);
});

const selectedSource = computed(() => {
  return props.dataSources.find((source) => source.id === selectedSourceId.value) ?? props.dataSources[0];
});

function sourceChannelLabel(channel: string) {
  return {
    PUBLIC_WEB: '公开网页',
    RAIN_CLASSROOM: '雨课堂',
    USER_TEXT: '用户文本',
    USER_IMAGE: '用户截图'
  }[channel] ?? channel;
}
</script>

<template>
  <section class="split-workspace">
    <section class="source-board" aria-label="数据源列表">
      <div class="panel-head">
        <div>
          <p class="eyebrow">Source Registry</p>
          <h3>接入源健康度</h3>
        </div>
        <div class="segmented-control" aria-label="数据源状态过滤">
          <button type="button" :class="{ active: statusFilter === 'ALL' }" @click="statusFilter = 'ALL'">全部</button>
          <button type="button" :class="{ active: statusFilter === 'RUNNING' }" @click="statusFilter = 'RUNNING'">运行</button>
          <button type="button" :class="{ active: statusFilter === 'NEEDS_AUTH' }" @click="statusFilter = 'NEEDS_AUTH'">授权</button>
          <button type="button" :class="{ active: statusFilter === 'PAUSED' }" @click="statusFilter = 'PAUSED'">暂停</button>
        </div>
      </div>

      <div class="source-cards">
        <article
          v-for="source in filteredSources"
          :key="source.id"
          class="source-card"
          :class="{ selected: selectedSource?.id === source.id }"
          role="button"
          tabindex="0"
          @click="selectedSourceId = source.id"
          @keydown.enter="selectedSourceId = source.id"
        >
          <span class="source-card-head">
            <strong>{{ source.name }}</strong>
            <StatusPill :status="source.status" />
          </span>
          <span class="source-meta">{{ sourceChannelLabel(source.channel) }} · {{ source.lastSync }}</span>
          <a class="source-url" :href="source.sourceUrl" target="_blank" rel="noreferrer" @click.stop>{{ source.sourceUrl }}</a>
          <span class="bar-track"><i :style="{ width: `${source.successRate}%` }"></i></span>
          <span class="source-foot">
            <b>{{ source.successRate }}%</b>
            <small>{{ source.pending }} 条待处理</small>
          </span>
        </article>
      </div>
    </section>

    <aside v-if="selectedSource" class="inspector-panel" aria-label="数据源详情">
      <p class="eyebrow">Inspector</p>
      <h3>{{ selectedSource.name }}</h3>
      <dl class="stacked-list">
        <div>
          <dt>通道类型</dt>
          <dd>{{ sourceChannelLabel(selectedSource.channel) }}</dd>
        </div>
        <div>
          <dt>源网址</dt>
          <dd><a :href="selectedSource.sourceUrl" target="_blank" rel="noreferrer">{{ selectedSource.sourceUrl }}</a></dd>
        </div>
        <div>
          <dt>最近同步</dt>
          <dd>{{ selectedSource.lastSync }}</dd>
        </div>
        <div>
          <dt>成功率</dt>
          <dd>{{ selectedSource.successRate }}%</dd>
        </div>
        <div>
          <dt>待处理</dt>
          <dd>{{ selectedSource.pending }} 条</dd>
        </div>
      </dl>
      <div class="decision-actions">
        <button
          v-if="selectedSource.channel === 'PUBLIC_WEB' && selectedSource.status !== 'PAUSED'"
          type="button"
          class="solid-button"
          :disabled="props.crawlingSourceId === selectedSource.id"
          @click="$emit('crawl', selectedSource.id)"
        >
          {{ props.crawlingSourceId === selectedSource.id ? '采集中' : '立即采集此源' }}
        </button>
      </div>
    </aside>
  </section>
</template>
