import type { CrawlTask, DataSource, NavItem, ReviewEvent } from './adminTypes';

export const navItems: NavItem[] = [
  { key: 'review', label: '事件审核', count: 18 },
  { key: 'sources', label: '数据源', count: 7 },
  { key: 'tasks', label: '采集任务', count: 11 },
  { key: 'agents', label: '智能体', count: 4 }
];

export const initialReviewEvents: ReviewEvent[] = [
  {
    id: 1001,
    title: '人工智能主题讲座通知',
    source: '软件学院官网',
    type: 'LECTURE',
    status: 'AI_PUBLISHED',
    confidence: 0.91,
    location: '图书馆报告厅',
    startTime: '07-08 19:00',
    scope: '软件学院本科生',
    summary: '软件学院将于 7 月 8 日举办 AI 主题讲座，面向软件学院本科生开放。',
    risk: '字段完整，建议快速确认',
    tags: ['AI', '讲座', '软件学院']
  },
  {
    id: 1002,
    title: '期末考试考场调整说明',
    source: '教务通知',
    type: 'EXAM',
    status: 'CORRECTED',
    confidence: 0.74,
    location: '一号教学楼',
    startTime: '07-11 09:30',
    scope: '2023 级',
    summary: '部分课程考试地点变更，学生需以最终考场清单为准。',
    risk: '地点存在冲突，需人工复核',
    tags: ['考试', '教务']
  },
  {
    id: 1003,
    title: '雨课堂作业提交提醒',
    source: '用户导入',
    type: 'HOMEWORK',
    status: 'AI_PUBLISHED',
    confidence: 0.68,
    location: '线上',
    startTime: '07-09 23:59',
    scope: 'SE101',
    summary: 'SE101 课程作业截止到 7 月 9 日 23:59，来源为用户粘贴 JSON。',
    risk: '来源为用户导入，建议抽查原文',
    tags: ['雨课堂', '作业']
  },
  {
    id: 1004,
    title: '创新创业竞赛报名开放',
    source: '学工系统',
    type: 'ACTIVITY',
    status: 'REVIEWED',
    confidence: 0.88,
    location: '学生事务中心',
    startTime: '07-15 18:00',
    scope: '全校学生',
    summary: '创新创业竞赛进入报名阶段，材料提交窗口开放至 7 月 15 日。',
    risk: '已审核，可观察报名链接有效性',
    tags: ['竞赛', '报名']
  }
];

export const initialDataSources: DataSource[] = [
  { id: 1, name: '软件学院通知', channel: 'PUBLIC_WEB', status: 'RUNNING', lastSync: '2 分钟前', successRate: 98, pending: 3 },
  { id: 2, name: '教务处公告', channel: 'PUBLIC_WEB', status: 'HEALTHY', lastSync: '11 分钟前', successRate: 94, pending: 5 },
  { id: 3, name: '雨课堂导入', channel: 'USER_JSON', status: 'NEEDS_AUTH', lastSync: '24 分钟前', successRate: 81, pending: 8 },
  { id: 4, name: '用户截图 OCR', channel: 'USER_IMAGE', status: 'PAUSED', lastSync: '1 小时前', successRate: 76, pending: 2 }
];

export const initialCrawlTasks: CrawlTask[] = [
  { id: 501, name: '软件学院增量抓取', status: 'SUCCESS', owner: '感知 Agent', time: '18:20', note: '新增 3 条，重复 1 条' },
  { id: 502, name: '雨课堂 JSON 解析', status: 'RUNNING', owner: '认知 Agent', time: '18:18', note: '正在抽取课程作业字段' },
  { id: 503, name: '向量文本同步', status: 'PENDING', owner: '向量服务', time: '18:16', note: '等待事件审核结果' },
  { id: 504, name: '教务通知抓取', status: 'FAILED', owner: '感知 Agent', time: '18:11', note: '源站 403，需要检查白名单' }
];
