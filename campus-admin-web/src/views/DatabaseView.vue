<script setup lang="ts">
defineProps<{
  tables: Array<{ name: string; label: string; columns: string[] }>;
  selected: string;
  rows: Array<Record<string, unknown>>;
  error: string;
}>();
defineEmits<{ select: [table: string]; retryAi: [id: number] }>();
</script>

<template>
  <section class="review-panel">
    <header class="panel-head"><div><p class="eyebrow">ADMIN ONLY</p><h3>受控数据表</h3></div></header>
    <div class="filters">
      <button v-for="table in tables" :key="table.name" class="ghost-button" :class="{ active: selected === table.name }" @click="$emit('select', table.name)">{{ table.label }}</button>
    </div>
    <div class="table-scroll"><table v-if="rows.length"><thead><tr><th v-for="key in Object.keys(rows[0])" :key="key">{{ key }}</th><th v-if="selected === 'ai_processing_record'">操作</th></tr></thead><tbody><tr v-for="(row, index) in rows" :key="index"><td v-for="key in Object.keys(rows[0])" :key="key">{{ row[key] ?? '-' }}</td><td v-if="selected === 'ai_processing_record'"><button v-if="row.status === 'FAILED'" type="button" class="ghost-button tiny" @click="$emit('retryAi', Number(row.id))">重试</button><span v-else>-</span></td></tr></tbody></table><p v-else>{{ error || '暂无数据' }}</p></div>
  </section>
</template>

<style scoped>.filters{padding:16px;display:flex;gap:8px;flex-wrap:wrap}.table-scroll{overflow:auto;padding:0 16px 16px}table{border-collapse:collapse;width:100%;font-size:12px}th,td{padding:10px;border-bottom:1px solid var(--line);text-align:left;white-space:nowrap;max-width:260px;overflow:hidden;text-overflow:ellipsis}.active{background:var(--ink);color:white}</style>
