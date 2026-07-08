<script setup lang="ts">
import { computed, ref } from 'vue';
import StatusPill from '../components/StatusPill.vue';

const aiInput = ref('7月8日 19:00，软件学院将在图书馆报告厅举办人工智能主题讲座，面向软件学院本科生。');

const aiPreview = computed(() => {
  const text = aiInput.value;
  const lecture = text.includes('讲座') || text.toLowerCase().includes('ai');
  const homework = text.includes('作业') || text.includes('截止');
  return {
    title: text.split(/[，。\n]/)[0] || '待抽取事件',
    type: homework ? 'HOMEWORK' : lecture ? 'LECTURE' : 'NOTICE',
    confidence: text.includes('地点') || text.includes('图书馆') ? 91 : 66,
    review: text.length < 20 ? '需要补充原文' : '可进入审核队列'
  };
});
</script>

<template>
  <section class="agent-workspace">
    <section class="agent-lab">
      <div class="panel-head">
        <div>
          <p class="eyebrow">Cognition Agent</p>
          <h3>文本抽取预览</h3>
        </div>
        <StatusPill status="RULE" />
      </div>
      <textarea v-model="aiInput" aria-label="待解析文本"></textarea>
      <div class="extract-grid">
        <div>
          <span>标题</span>
          <strong>{{ aiPreview.title }}</strong>
        </div>
        <div>
          <span>类型</span>
          <strong>{{ aiPreview.type }}</strong>
        </div>
        <div>
          <span>置信度</span>
          <strong>{{ aiPreview.confidence }}%</strong>
        </div>
        <div>
          <span>审核建议</span>
          <strong>{{ aiPreview.review }}</strong>
        </div>
      </div>
    </section>

    <section class="agent-chain" aria-label="智能体链路">
      <article>
        <span>01</span>
        <strong>感知 Agent</strong>
        <small>网页、图片、雨课堂输入统一归一化为原始文档。</small>
      </article>
      <article>
        <span>02</span>
        <strong>认知 Agent</strong>
        <small>抽取事件字段并给出置信度与审核建议。</small>
      </article>
      <article>
        <span>03</span>
        <strong>决策 Agent</strong>
        <small>结合用户问题生成检索范围、排序策略和追问计划。</small>
      </article>
      <article>
        <span>04</span>
        <strong>向量服务</strong>
        <small>审核后生成向量文本，进入语义检索索引。</small>
      </article>
    </section>
  </section>
</template>
