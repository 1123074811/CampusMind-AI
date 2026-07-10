export type NavKey = 'review' | 'agent' | 'sources' | 'tasks' | 'users' | 'logs';

export type ReviewStatus = 'AI_PUBLISHED' | 'CORRECTED' | 'REVIEWED' | 'REJECTED' | 'OFFLINE';
export type EventType = 'NOTICE' | 'COURSE' | 'EXAM' | 'HOMEWORK' | 'ACTIVITY' | 'LECTURE' | 'COMPETITION' | 'SERVICE' | 'OTHER';
export type SourceStatus = 'RUNNING' | 'HEALTHY' | 'NEEDS_AUTH' | 'PAUSED';
export type TaskStatus = 'SUCCESS' | 'RUNNING' | 'FAILED' | 'PENDING' | 'SKIPPED';

export interface NavItem {
  key: NavKey;
  label: string;
  count: number;
}

export interface ReviewEvent {
  id: number;
  title: string;
  source: string;
  sourceUrl: string;
  type: EventType;
  status: ReviewStatus;
  confidence: number;
  location: string;
  startTime: string;
  endTime: string;
  organizer: string;
  scope: string;
  summary: string;
  risk: string;
  tags: string[];
  aiStatus: 'PENDING' | 'SUCCESS' | 'REVIEW' | 'FAILED';
  aiNeedReview: boolean;
  aiCard: Record<string, unknown>;
}

export interface DataSource {
  id: number;
  name: string;
  sourceUrl: string;
  channel: string;
  status: SourceStatus;
  lastSync: string;
  successRate: number;
  pending: number;
}

export interface CrawlTask {
  id: number;
  name: string;
  status: TaskStatus;
  owner: string;
  time: string;
  note: string;
}

export interface CrawlSourceResult {
  taskId: number;
  sourceId: number;
  sourceName: string;
  status: TaskStatus;
  httpStatus: number | null;
  crawlUrl: string;
  discoveredCount: number;
  persistedCount: number;
  parserVersion: string | null;
  failReason: string | null;
  startedAt: string;
  finishedAt: string;
}

export interface BatchCrawlResult {
  sourceCount: number;
  successCount: number;
  failedCount: number;
  persistedCount: number;
  results: CrawlSourceResult[];
  startedAt: string;
  finishedAt: string;
}

export interface CrawlItem {
  id: number;
  sourceId: number;
  sourceName: string;
  sourceUrl: string;
  itemUrl: string;
  title: string;
  detailTitle: string | null;
  dateText: string | null;
  summary: string | null;
  detailContent: string | null;
  parseStatus: 'LIST_ONLY' | 'DETAIL_SUCCESS' | 'PARSE_FAILED' | 'DETAIL_FAILED';
  parseError: string | null;
  detailHttpStatus: number | null;
  fetchedAt: string;
  detailFetchedAt: string | null;
  favoriteCount: number;
}

export interface DashboardMetrics {
  reviewCount: number;
  urgentCount: number;
  avgConfidence: number;
  sourceSuccessRate: number;
  sourcesNeedAuth: number;
  vectorPending: number;
}

export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export interface DashboardResponse {
  metrics: DashboardMetrics;
  events: ReviewEvent[];
  dataSources: DataSource[];
  tasks: CrawlTask[];
}

export interface AdminUser {
  id: number;
  username: string;
  role: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: AdminUser;
}

export interface AdminSession {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: AdminUser;
  demo?: boolean;
}

export interface AdminManagedUser {
  id: number;
  username: string;
  phone: string | null;
  role: 'ADMIN' | 'OPERATOR' | 'STUDENT';
  status: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminUserListResponse {
  items: AdminManagedUser[];
  total: number;
}

export interface AdminAuditLog {
  id: number;
  eventId: number | null;
  operatorId: number | null;
  action: string;
  beforeSnapshot: string | null;
  afterSnapshot: string | null;
  comment: string | null;
  createdAt: string;
}

export interface AdminAuditLogListResponse {
  items: AdminAuditLog[];
  total: number;
}
