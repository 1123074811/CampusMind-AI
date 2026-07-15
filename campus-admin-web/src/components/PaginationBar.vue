<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(defineProps<{
  page: number;
  pageSize: number;
  total: number;
  pageSizes?: number[];
}>(), {
  pageSizes: () => [10, 20, 50]
});

const emit = defineEmits<{
  change: [page: number, pageSize: number];
}>();

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));

function changePage(page: number) {
  emit('change', Math.min(Math.max(page, 0), pageCount.value - 1), props.pageSize);
}

function changeSize(event: Event) {
  emit('change', 0, Number((event.target as HTMLSelectElement).value));
}
</script>

<template>
  <nav class="pagination-bar" aria-label="分页">
    <span>共 {{ total }} 条</span>
    <button type="button" class="ghost-button tiny" :disabled="page <= 0" @click="changePage(page - 1)">上一页</button>
    <strong>第 {{ page + 1 }} / {{ pageCount }} 页</strong>
    <button type="button" class="ghost-button tiny" :disabled="page + 1 >= pageCount" @click="changePage(page + 1)">下一页</button>
    <label>
      每页
      <select :value="pageSize" @change="changeSize">
        <option v-for="size in pageSizes" :key="size" :value="size">{{ size }}</option>
      </select>
      条
    </label>
  </nav>
</template>

<style scoped>
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid var(--line-soft);
  color: var(--ink-muted);
  font-size: 12px;
}
.pagination-bar strong { color: var(--ink); }
.pagination-bar label { display: flex; align-items: center; gap: 6px; }
.pagination-bar select { min-height: 32px; padding: 4px 24px 4px 8px; }
@media (max-width: 640px) {
  .pagination-bar { justify-content: center; flex-wrap: wrap; }
}
</style>
