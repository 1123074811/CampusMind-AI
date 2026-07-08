<script setup lang="ts">
import { computed, ref } from 'vue';
import StatusPill from '../components/StatusPill.vue';
import type { CrawlTask, TaskStatus } from '../adminTypes';

const props = defineProps<{
  tasks: CrawlTask[];
}>();

const statusFilter = ref<'ALL' | TaskStatus>('ALL');

const filteredTasks = computed(() => {
  if (statusFilter.value === 'ALL') {
    return props.tasks;
  }
  return props.tasks.filter((task) => task.status === statusFilter.value);
});
</script>

<template>
  <section class="task-workspace">
    <section class="task-console">
      <div class="panel-head">
        <div>
          <p class="eyebrow">Task Dispatch</p>
          <h3>采集任务编排</h3>
        </div>
        <div class="segmented-control" aria-label="任务状态过滤">
          <button type="button" :class="{ active: statusFilter === 'ALL' }" @click="statusFilter = 'ALL'">全部</button>
          <button type="button" :class="{ active: statusFilter === 'RUNNING' }" @click="statusFilter = 'RUNNING'">运行</button>
          <button type="button" :class="{ active: statusFilter === 'FAILED' }" @click="statusFilter = 'FAILED'">失败</button>
          <button type="button" :class="{ active: statusFilter === 'PENDING' }" @click="statusFilter = 'PENDING'">等待</button>
        </div>
      </div>

      <ol class="timeline-list">
        <li v-for="task in filteredTasks" :key="task.id" :data-status="task.status">
          <time>{{ task.time }}</time>
          <div>
            <strong>{{ task.name }}</strong>
            <small>{{ task.owner }} · {{ task.note }}</small>
          </div>
          <StatusPill :status="task.status" />
        </li>
      </ol>
    </section>

    <section class="dispatch-grid" aria-label="调度指标">
      <article>
        <span>队列吞吐</span>
        <strong>42/h</strong>
        <small>公开网页与用户导入合计</small>
      </article>
      <article>
        <span>失败重试</span>
        <strong>3</strong>
        <small>403 与 OCR 低置信度优先</small>
      </article>
      <article>
        <span>平均延迟</span>
        <strong>18s</strong>
        <small>从采集完成到事件入库</small>
      </article>
    </section>
  </section>
</template>
