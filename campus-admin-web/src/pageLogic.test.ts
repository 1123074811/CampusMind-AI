import assert from 'node:assert/strict';
import test from 'node:test';
import { buildReviewMetrics, dashboardConnectionMessage } from './pageLogic.ts';
import type { ReviewEvent } from './adminTypes.ts';

function event(status: ReviewEvent['status']): ReviewEvent {
  return { id: 1, title: '', source: '', sourceUrl: '', type: 'NOTICE', status,
    location: '', startTime: '', endTime: '', organizer: '', scope: '', summary: '', risk: '', tags: [],
    aiStatus: 'SUCCESS', aiNeedReview: false, aiCard: {} };
}

test('review metrics do not count corrected items as published', () => {
  const metrics = buildReviewMetrics([event('AI_PUBLISHED'), event('CORRECTED'), event('REVIEWED'), event('OFFLINE')]);
  assert.deepEqual(metrics.map((item) => item.value), [1, 1, 1, 1]);
});

test('partial dashboard failures remain visible', () => {
  assert.equal(dashboardConnectionMessage([]), '已连接后端数据库');
  assert.equal(dashboardConnectionMessage(['用户列表', '审计日志']), '已连接后端，以下数据加载失败：用户列表、审计日志');
});
