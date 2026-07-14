<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import StatusPill from '../components/StatusPill.vue';
import type { CrawlItem, CrawlTask, DataSource, DataSourceVersion, SourceStatus } from '../adminTypes';
import type { DataSourcePayload } from '../api/admin';

const props = defineProps<{
  dataSources: DataSource[];
  crawlingSourceId: number | null;
  canManage: boolean;
  versions: DataSourceVersion[];
  tasks?: CrawlTask[];
  crawlItems?: CrawlItem[];
}>();

const emit = defineEmits<{
  crawl: [id: number];
  create: [payload: DataSourcePayload];
  update: [id: number, payload: DataSourcePayload];
  toggle: [source: DataSource];
  history: [id: number];
  rollback: [id: number, versionNo: number];
}>();

const statusFilter = ref<'ALL' | SourceStatus>('ALL');
const selectedSourceId = ref(props.dataSources[0]?.id ?? 0);
const editing = ref(false);
const editingId = ref<number | null>(null);
const form = reactive<DataSourcePayload>({
  name: '', sourceType: 'PUBLIC_WEB', baseUrl: '', robotsUrl: '', crawlIntervalSeconds: 3600,
  parserType: 'WEBMAGIC', selectorConfig: '', enabled: true
});

const filteredSources = computed(() => {
  if (statusFilter.value === 'ALL') {
    return props.dataSources;
  }
  return props.dataSources.filter((source) => source.status === statusFilter.value);
});

const selectedSource = computed(() => {
  return props.dataSources.find((source) => source.id === selectedSourceId.value) ?? props.dataSources[0];
});

const sourceTimeline = computed(() => {
  if (!selectedSource.value) return [];
  const source = selectedSource.value;
  const entries: Array<{ time: string; label: string; type: 'version' | 'task' | 'error' }> = [];
  for (const v of props.versions) {
    entries.push({ time: v.createdAt, label: `v${v.versionNo} · ${v.action}`, type: 'version' });
  }
  const matchedTasks = (props.tasks ?? []).filter((t) => t.name.startsWith(source.name)).slice(0, 5);
  for (const t of matchedTasks) {
    entries.push({ time: t.time, label: `采集: ${t.status} · ${t.note.slice(0, 60)}`, type: t.status === 'FAILED' ? 'error' : 'task' });
  }
  const failedItems = (props.crawlItems ?? [])
    .filter((item) => item.sourceId === source.id && item.parseStatus === 'PARSE_FAILED')
    .slice(0, 3);
  for (const item of failedItems) {
    entries.push({ time: item.fetchedAt, label: `解析失败: ${item.parseError ?? item.title}`, type: 'error' });
  }
  entries.sort((a, b) => (b.time > a.time ? 1 : -1));
  return entries.slice(0, 12);
});

watch(selectedSource, (source) => {
  if (source && props.canManage) emit('history', source.id);
}, { immediate: true });

function sourceChannelLabel(channel: string) {
  return {
    PUBLIC_WEB: '公开网页',
    RAIN_CLASSROOM: '雨课堂',
    USER_TEXT: '用户文本',
    USER_IMAGE: '用户截图',
    USER_FILE: '用户文件'
  }[channel] ?? channel;
}

function startEdit(source?: DataSource) {
  editing.value = true;
  editingId.value = source?.id ?? null;
  Object.assign(form, source ? {
    name: source.name,
    sourceType: source.channel,
    baseUrl: source.sourceUrl,
    robotsUrl: source.robotsUrl ?? '',
    crawlIntervalSeconds: source.crawlIntervalSeconds || 3600,
    parserType: source.parserType || 'WEBMAGIC',
    selectorConfig: source.selectorConfig ?? '',
    enabled: source.enabled
  } : {
    name: '', sourceType: 'PUBLIC_WEB', baseUrl: '', robotsUrl: '', crawlIntervalSeconds: 3600,
    parserType: 'WEBMAGIC', selectorConfig: '', enabled: true
  });
}

function saveSource() {
  if (!form.name.trim() || !form.baseUrl.trim()) return;
  const payload = { ...form, name: form.name.trim(), baseUrl: form.baseUrl.trim() };
  if (editingId.value == null) emit('create', payload);
  else emit('update', editingId.value, payload);
  editing.value = false;
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
        <button v-if="props.canManage" type="button" class="solid-button" @click="startEdit()">新增数据源</button>
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

    <aside v-if="editing" class="inspector-panel" aria-label="编辑数据源">
      <p class="eyebrow">Source Editor</p>
      <h3>{{ editingId == null ? '新增数据源' : '编辑数据源' }}</h3>
      <div class="filters vertical">
        <input v-model="form.name" type="text" placeholder="数据源名称" />
        <select v-model="form.sourceType">
          <option value="PUBLIC_WEB">公开网页</option>
          <option value="OFFICIAL_API">官方 API</option>
        </select>
        <input v-model="form.baseUrl" type="url" placeholder="https://example.edu.cn/notices" />
        <input v-model="form.robotsUrl" type="url" placeholder="robots.txt 地址（可选）" />
        <input v-model.number="form.crawlIntervalSeconds" type="number" min="30" max="86400" placeholder="采集间隔（秒）" />
        <input v-model="form.parserType" type="text" placeholder="解析器，例如 WEBMAGIC" />
        <textarea v-model="form.selectorConfig" rows="7" placeholder="选择器 JSON（可选）"></textarea>
      </div>
      <div class="decision-actions">
        <button type="button" class="solid-button" @click="saveSource">保存</button>
        <button type="button" class="ghost-button" @click="editing = false">取消</button>
      </div>
    </aside>

    <aside v-else-if="selectedSource" class="inspector-panel" aria-label="数据源详情">
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
        <div>
          <dt>采集间隔</dt>
          <dd>{{ selectedSource.crawlIntervalSeconds }} 秒</dd>
        </div>
        <div>
          <dt>解析器</dt>
          <dd>{{ selectedSource.parserType }}</dd>
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
        <button v-if="props.canManage" type="button" class="ghost-button" @click="startEdit(selectedSource)">编辑</button>
        <button v-if="props.canManage" type="button" class="ghost-button" @click="emit('toggle', selectedSource)">
          {{ selectedSource.enabled ? '暂停' : '恢复' }}
        </button>
      </div>
      <section class="source-history" aria-label="数据源健康时间线">
        <p class="eyebrow">Health Timeline</p>
        <ol class="health-timeline">
          <li v-for="(entry, idx) in sourceTimeline" :key="idx" :class="'timeline-' + entry.type">
            <time>{{ entry.time?.slice(0, 16) ?? '-' }}</time>
            <span>{{ entry.label }}</span>
          </li>
        </ol>
        <p v-if="sourceTimeline.length === 0" class="empty-note">暂无时间线记录</p>
      </section>
      <section v-if="props.canManage" class="source-history" aria-label="数据源版本历史">
        <p class="eyebrow">Version History</p>
        <article v-for="version in props.versions" :key="version.id" class="history-entry">
          <div><strong>v{{ version.versionNo }} · {{ version.action }}</strong><small>{{ version.createdAt }}</small></div>
          <pre>{{ JSON.stringify(version.snapshot, null, 2) }}</pre>
          <button v-if="version.versionNo !== props.versions[0]?.versionNo" type="button" class="ghost-button"
                  @click="emit('rollback', selectedSource.id, version.versionNo)">回滚到此版本</button>
        </article>
        <p v-if="props.versions.length === 0" class="empty-note">暂无版本记录</p>
      </section>
    </aside>
  </section>
</template>

<style scoped>
.health-timeline {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}
.health-timeline li {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 10px;
  align-items: start;
  padding: 8px 10px;
  border-radius: 12px;
  background: #f6f7f4;
  font-size: 13px;
  line-height: 1.4;
}
.health-timeline time {
  font-family: "Cascadia Mono", Consolas, monospace;
  font-size: 11px;
  font-weight: 800;
  color: var(--ink-muted, #66736f);
}
.health-timeline .timeline-version {
  border-left: 3px solid var(--green, #16845f);
}
.health-timeline .timeline-task {
  border-left: 3px solid var(--accent, #f26b45);
}
.health-timeline .timeline-error {
  border-left: 3px solid var(--danger, #c8472f);
  background: #fde5df;
}
.empty-note {
  color: var(--ink-muted, #66736f);
  font-size: 13px;
  font-weight: 700;
}
</style>
