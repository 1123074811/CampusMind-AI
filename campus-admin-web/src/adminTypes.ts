export type NavKey = 'review' | 'sources' | 'tasks' | 'agents';

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
  type: EventType;
  status: ReviewStatus;
  confidence: number;
  location: string;
  startTime: string;
  scope: string;
  summary: string;
  risk: string;
  tags: string[];
}

export interface DataSource {
  id: number;
  name: string;
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
