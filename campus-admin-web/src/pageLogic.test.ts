import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildEventMetrics, dashboardConnectionMessage } from './pageLogic.ts';
import type { ReviewEvent } from './adminTypes.ts';

function event(status: ReviewEvent['status']): ReviewEvent {
  return { id: 1, title: '', source: '', sourceUrl: '', type: 'NOTICE', status,
    location: '', startTime: '', endTime: '', organizer: '', scope: '', summary: '', risk: '', tags: [],
    aiStatus: 'SUCCESS', aiNeedReview: false, aiCard: {}, submittedBy: null, submittedByUserId: null };
}

describe('pageLogic', () => {
  it('event metrics count all non-offline items as published', () => {
    const metrics = buildEventMetrics([event('AI_PUBLISHED'), event('CORRECTED'), event('REVIEWED'), event('OFFLINE')]);
    assert.deepEqual(metrics.map((item) => item.value), [3, 1, 0, 1]);
  });

  it('partial dashboard failures remain visible', () => {
    assert.equal(dashboardConnectionMessage([]), '已连接后端数据库');
    assert.equal(dashboardConnectionMessage(['用户列表', '审计日志']), '已连接后端，以下数据加载失败：用户列表、审计日志');
  });
});
