<script setup lang="ts">
import { computed, ref } from 'vue';
import StatusPill from '../components/StatusPill.vue';
import type { CrawlItem, CrawlTask, TaskStatus } from '../adminTypes';

const props = defineProps<{
  tasks: CrawlTask[];
  crawlItems: CrawlItem[];
  crawlerRunning: boolean;
}>();

defineEmits<{
  runNow: [];
}>();

const statusFilter = ref<'ALL' | TaskStatus>('ALL');
const selectedTaskId = ref<number | null>(props.tasks[0]?.id ?? null);
const selectedItemId = ref<number | null>(props.crawlItems[0]?.id ?? null);
const detailMode = ref<'task' | 'item'>(props.crawlItems.length > 0 ? 'item' : 'task');

const filteredTasks = computed(() => {
  if (statusFilter.value === 'ALL') {
    return props.tasks;
  }
  return props.tasks.filter((task) => displayStatus(task.status) === statusFilter.value);
});

function displayStatus(status: TaskStatus): 'SUCCESS' | 'RUNNING' | 'FAILED' {
  if (status === 'SUCCESS') return 'SUCCESS';
  if (status === 'RUNNING' || status === 'PENDING') return 'RUNNING';
  return 'FAILED';
}

function formatItemTime(value: string | null) {
  if (!value) {
    return '时间待补充';
  }
  return new Date(value)
    .toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    })
    .replace(/\//g, '-');
}

function previewText(item: CrawlItem) {
  return item.detailContent || item.summary || item.parseError || '正文暂未解析';
}

const selectedTask = computed(() => {
  return props.tasks.find((task) => task.id === selectedTaskId.value) ?? props.tasks[0] ?? null;
});

const selectedItem = computed(() => {
  return props.crawlItems.find((item) => item.id === selectedItemId.value) ?? props.crawlItems[0] ?? null;
});

function selectTask(id: number) {
  selectedTaskId.value = id;
  detailMode.value = 'task';
}

function selectItem(id: number) {
  selectedItemId.value = id;
  detailMode.value = 'item';
}
</script>

<template>
  <section class="task-split-workspace">
    <div class="task-workspace">
      <section class="task-console">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Task Dispatch</p>
            <h3>采集任务编排</h3>
          </div>
          <div class="task-actions">
            <span class="schedule-badge">每小时自动采集</span>
            <button type="button" class="solid-button" :disabled="crawlerRunning" @click="$emit('runNow')">
              {{ crawlerRunning ? '采集中' : '立即采集' }}
            </button>
            <div class="segmented-control" aria-label="任务状态过滤">
              <button type="button" :class="{ active: statusFilter === 'ALL' }" @click="statusFilter = 'ALL'">全部</button>
              <button type="button" :class="{ active: statusFilter === 'SUCCESS' }" @click="statusFilter = 'SUCCESS'">成功</button>
              <button type="button" :class="{ active: statusFilter === 'RUNNING' }" @click="statusFilter = 'RUNNING'">运行中</button>
              <button type="button" :class="{ active: statusFilter === 'FAILED' }" @click="statusFilter = 'FAILED'">失败</button>
            </div>
          </div>
        </div>

        <ol class="timeline-list">
          <li
            v-for="task in filteredTasks"
            :key="task.id"
            :data-status="displayStatus(task.status)"
            :class="{ selected: detailMode === 'task' && selectedTaskId === task.id }"
            role="button"
            tabindex="0"
            @click="selectTask(task.id)"
            @keydown.enter="selectTask(task.id)"
          >
            <time>{{ task.time }}</time>
            <div>
              <strong>{{ task.name }}</strong>
              <small>{{ task.owner }} · {{ task.note }}</small>
            </div>
            <StatusPill :status="displayStatus(task.status)" />
          </li>
        </ol>
      </section>

      <section class="task-console">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Raw Capture</p>
            <h3>最近原文入库</h3>
          </div>
          <span class="schedule-badge">{{ props.crawlItems.length }} 条</span>
        </div>

        <ol class="crawl-item-list">
          <li
            v-for="item in props.crawlItems"
            :key="item.id"
            :data-status="item.parseStatus"
            :class="{ selected: detailMode === 'item' && selectedItemId === item.id }"
            role="button"
            tabindex="0"
            @click="selectItem(item.id)"
            @keydown.enter="selectItem(item.id)"
          >
            <time>{{ formatItemTime(item.fetchedAt) }}</time>
            <div class="crawl-item-main">
              <span>
                <strong>{{ item.detailTitle || item.title }}</strong>
                <small>{{ item.sourceName }} · {{ item.dateText || '日期待解析' }} · {{ item.favoriteCount }} 人收藏</small>
              </span>
              <p>{{ previewText(item) }}</p>
              <a :href="item.itemUrl" target="_blank" rel="noreferrer" @click.stop>{{ item.itemUrl }}</a>
            </div>
            <StatusPill :status="item.parseStatus === 'DETAIL_SUCCESS' ? 'SUCCESS' : item.parseStatus === 'DETAIL_FAILED' ? 'FAILED' : 'PENDING'" />
          </li>
        </ol>
      </section>
    </div>

    <aside class="inspector-panel" aria-label="任务详情">
      <template v-if="detailMode === 'item' && selectedItem">
        <p class="eyebrow">Capture Detail</p>
        <h3>{{ selectedItem.detailTitle || selectedItem.title }}</h3>
        <dl class="stacked-list">
          <div>
            <dt>来源</dt>
            <dd>{{ selectedItem.sourceName }}</dd>
          </div>
          <div>
            <dt>抓取时间</dt>
            <dd>{{ formatItemTime(selectedItem.fetchedAt) }}</dd>
          </div>
          <div>
            <dt>解析状态</dt>
            <dd><StatusPill :status="selectedItem.parseStatus" /></dd>
          </div>
          <div>
            <dt>收藏人数</dt>
            <dd>{{ selectedItem.favoriteCount }} 人</dd>
          </div>
          <div>
            <dt>HTTP</dt>
            <dd>{{ selectedItem.detailHttpStatus || '-' }}</dd>
          </div>
        </dl>
        <p class="summary-text">{{ previewText(selectedItem) }}</p>
        <div class="decision-actions">
          <a class="solid-button link-button" :href="selectedItem.itemUrl" target="_blank" rel="noreferrer">打开原文</a>
        </div>
      </template>

      <template v-else-if="selectedTask">
        <p class="eyebrow">Task Detail</p>
        <h3>{{ selectedTask.name }}</h3>
        <dl class="stacked-list">
          <div>
            <dt>执行时间</dt>
            <dd>{{ selectedTask.time }}</dd>
          </div>
          <div>
            <dt>负责人</dt>
            <dd>{{ selectedTask.owner }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd><StatusPill :status="displayStatus(selectedTask.status)" /></dd>
          </div>
        </dl>
        <p class="summary-text">{{ selectedTask.note }}</p>
      </template>

      <template v-else>
        <p class="eyebrow">Inspector</p>
        <h3>暂无详情</h3>
        <p class="summary-text">点击任务或原文入库记录查看详情。</p>
      </template>
    </aside>
  </section>
</template>
