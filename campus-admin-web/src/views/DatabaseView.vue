<script setup lang="ts">
import { ref, watch } from 'vue';
import { fetchChangeLogs } from '../api/admin';
import type { AdminSession } from '../adminTypes';

const props = defineProps<{
  tables: Array<{ name: string; label: string; columns: string[] }>;
  selected: string;
  rows: Array<Record<string, unknown>>;
  error: string;
  session?: AdminSession | null;
}>();

const emit = defineEmits<{ select: [table: string]; retryAi: [id: number] }>();

const activeView = ref<'tables' | 'changelog'>('tables');
const changeLogs = ref<Array<{
  id: number; itemId: number; itemTitle: string; sourceName: string;
  oldContentHash: string; newContentHash: string; changedFields: string; changedAt: string;
}>>([]);
const changelogLoading = ref(false);
const changelogError = ref('');

watch(activeView, async (view) => {
  if (view === 'changelog' && changeLogs.value.length === 0) {
    await loadChangeLogs();
  }
});

async function loadChangeLogs() {
  changelogLoading.value = true;
  changelogError.value = '';
  try {
    changeLogs.value = await fetchChangeLogs(props.session ?? null);
  } catch (e: any) {
    changelogError.value = e.message || '加载失败';
  } finally {
    changelogLoading.value = false;
  }
}

function formatFields(raw: string) {
  try {
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr.join(', ') : raw;
  } catch {
    return raw;
  }
}
</script>

<template>
  <section class="review-panel">
    <header class="panel-head">
      <div><p class="eyebrow">ADMIN ONLY</p><h3>数据管理</h3></div>
      <div class="view-tabs">
        <button class="ghost-button" :class="{ active: activeView === 'tables' }" @click="activeView = 'tables'">受控数据表</button>
        <button class="ghost-button" :class="{ active: activeView === 'changelog' }" @click="activeView = 'changelog'">变更日志</button>
      </div>
    </header>

    <!-- 数据表视图 -->
    <template v-if="activeView === 'tables'">
      <div class="filters">
        <button v-for="table in tables" :key="table.name" class="ghost-button" :class="{ active: selected === table.name }" @click="emit('select', table.name)">{{ table.label }}</button>
      </div>
      <div class="table-scroll"><table v-if="rows.length"><thead><tr><th v-for="key in Object.keys(rows[0])" :key="key">{{ key }}</th><th v-if="selected === 'ai_processing_record'">操作</th></tr></thead><tbody><tr v-for="(row, index) in rows" :key="index"><td v-for="key in Object.keys(rows[0])" :key="key">{{ row[key] ?? '-' }}</td><td v-if="selected === 'ai_processing_record'"><button v-if="row.status === 'FAILED'" type="button" class="ghost-button tiny" @click="emit('retryAi', Number(row.id))">重试</button><span v-else>-</span></td></tr></tbody></table><p v-else>{{ error || '暂无数据' }}</p></div>
    </template>

    <!-- 变更日志视图 -->
    <template v-else>
      <div class="filters">
        <button class="ghost-button tiny" @click="loadChangeLogs">刷新</button>
      </div>
      <div class="table-scroll">
        <p v-if="changelogLoading" class="loading-hint">加载中...</p>
        <p v-else-if="changelogError" class="error-hint">{{ changelogError }}</p>
        <table v-else-if="changeLogs.length">
          <thead><tr>
            <th>ID</th><th>信息标题</th><th>来源</th><th>变更字段</th><th>旧Hash</th><th>新Hash</th><th>变更时间</th>
          </tr></thead>
          <tbody>
            <tr v-for="log in changeLogs" :key="log.id">
              <td>{{ log.id }}</td>
              <td>{{ log.itemTitle || '-' }}</td>
              <td>{{ log.sourceName || '-' }}</td>
              <td><span class="field-tag">{{ formatFields(log.changedFields) }}</span></td>
              <td class="hash-cell">{{ log.oldContentHash?.substring(0, 12) || '-' }}...</td>
              <td class="hash-cell">{{ log.newContentHash?.substring(0, 12) || '-' }}...</td>
              <td>{{ log.changedAt || '-' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else>暂无变更记录</p>
      </div>
    </template>
  </section>
</template>

<style scoped>.filters{padding:16px;display:flex;gap:8px;flex-wrap:wrap}.table-scroll{overflow:auto;padding:0 16px 16px}table{border-collapse:collapse;width:100%;font-size:12px}th,td{padding:10px;border-bottom:1px solid var(--line);text-align:left;white-space:nowrap;max-width:260px;overflow:hidden;text-overflow:ellipsis}.active{background:var(--ink);color:white}.view-tabs{display:flex;gap:8px}.hash-cell{font-family:monospace;font-size:11px;color:var(--ink-muted)}.field-tag{background:var(--soft-amber,#f5ecd7);color:#8a6d2b;padding:2px 8px;border-radius:6px;font-size:11px;font-weight:700}.loading-hint,.error-hint{padding:24px;text-align:center;color:var(--ink-muted)}</style>
