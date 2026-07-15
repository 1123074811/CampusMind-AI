import type { PageMetric, ReviewEvent } from './adminTypes';

export function buildEventMetrics(events: ReviewEvent[]): PageMetric[] {
  const published = events.filter((event) => event.status !== 'OFFLINE' && event.status !== 'REJECTED').length;
  const corrected = events.filter((event) => event.status === 'CORRECTED').length;
  const aiIssues = events.filter((event) => event.aiStatus === 'REVIEW' || event.aiStatus === 'FAILED').length;
  const offline = events.filter((event) => event.status === 'OFFLINE' || event.status === 'REJECTED').length;
  return [
    { label: '正在展示', value: published, hint: '采集后直接发布', accent: 'green' },
    { label: '原文更新', value: corrected, hint: '仍在正常展示' },
    { label: 'AI 异常', value: aiIssues, hint: '不影响原文展示', accent: aiIssues > 0 ? 'amber' : 'default' },
    { label: '已下线', value: offline, hint: `共 ${events.length} 条事件` }
  ];
}

export function dashboardConnectionMessage(partialFailures: string[]) {
  return partialFailures.length === 0
    ? '已连接后端数据库'
    : `已连接后端，以下数据加载失败：${partialFailures.join('、')}`;
}
