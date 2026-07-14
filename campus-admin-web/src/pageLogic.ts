import type { PageMetric, ReviewEvent } from './adminTypes';

export function buildReviewMetrics(events: ReviewEvent[]): PageMetric[] {
  const published = events.filter((event) => event.status === 'AI_PUBLISHED').length;
  const corrected = events.filter((event) => event.status === 'CORRECTED').length;
  const reviewed = events.filter((event) => event.status === 'REVIEWED').length;
  const offline = events.filter((event) => event.status === 'OFFLINE' || event.status === 'REJECTED').length;
  return [
    { label: '待审核', value: published, hint: 'AI 已提取，待人工确认', accent: published > 0 ? 'amber' : 'default' },
    { label: '已修正', value: corrected, hint: '人工修正过' },
    { label: '已发布', value: reviewed, hint: '人工确认通过' },
    { label: '已下线', value: offline, hint: `共 ${events.length} 条事件` }
  ];
}

export function dashboardConnectionMessage(partialFailures: string[]) {
  return partialFailures.length === 0
    ? '已连接后端数据库'
    : `已连接后端，以下数据加载失败：${partialFailures.join('、')}`;
}
