import type {
  AdminAuditLogListResponse,
  AdminManagedUser,
  AdminSession,
  AiConfig,
  AdminUserListResponse,
  ApiResponse
} from '../adminTypes';
import { authorizedFetch } from './auth';

async function readPayload<T>(response: Response) {
  const payload = await response.json() as ApiResponse<T>;
  if (!response.ok || !payload.success) {
    throw new Error(payload.message || `HTTP ${response.status}`);
  }
  return payload.data;
}

export async function fetchAdminUsers(session: AdminSession | null) {
  const response = await authorizedFetch('/api/v1/users/admin?size=100', {}, session);
  return readPayload<AdminUserListResponse>(response);
}

export async function createAdminUser(
  session: AdminSession | null,
  payload: { username: string; phone: string; role: string; password: string }
) {
  const response = await authorizedFetch('/api/v1/users/admin', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload)
  }, session);
  return readPayload<AdminManagedUser>(response);
}

export async function updateAdminUserStatus(session: AdminSession | null, id: number, status: number) {
  const response = await authorizedFetch(`/api/v1/users/admin/${id}/status`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ status })
  }, session);
  return readPayload<AdminManagedUser>(response);
}

export async function resetAdminUserPassword(session: AdminSession | null, id: number, password = '123456') {
  const response = await authorizedFetch(`/api/v1/users/admin/${id}/password`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ password })
  }, session);
  return readPayload<AdminManagedUser>(response);
}

export async function fetchAdminLogs(session: AdminSession | null, action = 'ALL') {
  const query = action === 'ALL' ? '' : `?action=${encodeURIComponent(action)}`;
  const response = await authorizedFetch(`/api/admin/logs${query}`, {}, session);
  return readPayload<AdminAuditLogListResponse>(response);
}

export async function fetchAiConfig(session: AdminSession | null) {
  const response = await authorizedFetch('/api/admin/ai-config', {}, session);
  return readPayload<AiConfig>(response);
}

export async function fetchAdminTables(session: AdminSession | null) {
  const response = await authorizedFetch('/api/admin/tables', {}, session);
  return readPayload<Array<{ name: string; label: string; columns: string[] }>>(response);
}

export async function fetchAdminTableRows(session: AdminSession | null, table: string) {
  const response = await authorizedFetch(`/api/admin/tables/${encodeURIComponent(table)}?size=50`, {}, session);
  return readPayload<Array<Record<string, unknown>>>(response);
}

export async function retryAiProcessing(session: AdminSession | null, id: number) {
  const response = await authorizedFetch(`/api/admin/ai-processing/${id}/retry`, { method: 'POST' }, session);
  return readPayload<{ id: number; status: string }>(response);
}

export async function updateAiConfig(session: AdminSession | null, payload: {
  mode: 'rule' | 'llm'; baseUrl: string; model: string; apiKey?: string;
}) {
  const response = await authorizedFetch('/api/admin/ai-config', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }, session);
  return readPayload<AiConfig>(response);
}

export async function fetchChangeLogs(session: AdminSession | null, size = 50) {
  const response = await authorizedFetch(`/api/admin/change-logs?size=${size}`, {}, session);
  return readPayload<Array<{
    id: number; itemId: number; itemTitle: string; sourceName: string;
    oldContentHash: string; newContentHash: string; changedFields: string; changedAt: string;
  }>>(response);
}

export type DataSourcePayload = {
  name: string; sourceType: string; baseUrl: string; robotsUrl?: string | null;
  crawlIntervalSeconds: number; parserType: string; selectorConfig?: string | null; enabled: boolean;
};

export async function createDataSource(session: AdminSession | null, payload: DataSourcePayload) {
  const response = await authorizedFetch('/api/admin/sources', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
  }, session);
  return readPayload<import('../adminTypes').DataSource>(response);
}

export async function updateDataSource(session: AdminSession | null, id: number, payload: DataSourcePayload) {
  const response = await authorizedFetch(`/api/admin/sources/${id}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
  }, session);
  return readPayload<import('../adminTypes').DataSource>(response);
}

export async function setDataSourceEnabled(session: AdminSession | null, id: number, enabled: boolean) {
  const response = await authorizedFetch(`/api/admin/sources/${id}/enabled`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled })
  }, session);
  return readPayload<import('../adminTypes').DataSource>(response);
}
