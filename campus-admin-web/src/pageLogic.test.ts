import { describe, it, expect } from 'vitest';
import { buildReviewMetrics, dashboardConnectionMessage } from './pageLogic.ts';
import type { ReviewEvent } from './adminTypes.ts';

function event(status: ReviewEvent['status']): ReviewEvent {
  return { id: 1, title: '', source: '', sourceUrl: '', type: 'NOTICE', status,
    location: '', startTime: '', endTime: '', organizer: '', scope: '', summary: '', risk: '', tags: [],
    aiStatus: 'SUCCESS', aiNeedReview: false, aiCard: {}, submittedBy: null, submittedByUserId: null };
}

describe('pageLogic', () => {
  it('review metrics do not count corrected items as published', () => {
    const metrics = buildReviewMetrics([event('AI_PUBLISHED'), event('CORRECTED'), event('REVIEWED'), event('OFFLINE')]);
    expect(metrics.map((item) => item.value)).toEqual([1, 1, 1, 1]);
  });

  it('partial dashboard failures remain visible', () => {
    expect(dashboardConnectionMessage([])).toBe('已连接后端数据库');
    expect(dashboardConnectionMessage(['用户列表', '审计日志'])).toBe('已连接后端，以下数据加载失败：用户列表、审计日志');
  });
});
