import type {
  AdminAuditLogListResponse,
  AdminManagedUser,
  AdminSession,
  AiConfig,
  AdminUserListResponse,
  ApiResponse
} from '../adminTypes';
import { authHeaders } from './auth';

async function readPayload<T>(response: Response) {
  const payload = await response.json() as ApiResponse<T>;
  if (!response.ok || !payload.success) {
    throw new Error(payload.message || `HTTP ${response.status}`);
  }
  return payload.data;
}

export async function fetchAdminUsers(session: AdminSession | null) {
  const response = await fetch('/api/v1/users/admin?size=100', {
    headers: authHeaders(session)
  });
  return readPayload<AdminUserListResponse>(response);
}

export async function createAdminUser(
  session: AdminSession | null,
  payload: { username: string; phone: string; role: string; password: string }
) {
  const response = await fetch('/api/v1/users/admin', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(session)
    },
    body: JSON.stringify(payload)
  });
  return readPayload<AdminManagedUser>(response);
}

export async function updateAdminUserStatus(session: AdminSession | null, id: number, status: number) {
  const response = await fetch(`/api/v1/users/admin/${id}/status`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(session)
    },
    body: JSON.stringify({ status })
  });
  return readPayload<AdminManagedUser>(response);
}

export async function resetAdminUserPassword(session: AdminSession | null, id: number, password = '123456') {
  const response = await fetch(`/api/v1/users/admin/${id}/password`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(session)
    },
    body: JSON.stringify({ password })
  });
  return readPayload<AdminManagedUser>(response);
}

export async function fetchAdminLogs(session: AdminSession | null, action = 'ALL') {
  const query = action === 'ALL' ? '' : `?action=${encodeURIComponent(action)}`;
  const response = await fetch(`/api/admin/logs${query}`, {
    headers: authHeaders(session)
  });
  return readPayload<AdminAuditLogListResponse>(response);
}

export async function fetchAiConfig(session: AdminSession | null) {
  const response = await fetch('/api/admin/ai-config', { headers: authHeaders(session) });
  return readPayload<AiConfig>(response);
}

export async function fetchAdminTables(session: AdminSession | null) {
  const response = await fetch('/api/admin/tables', { headers: authHeaders(session) });
  return readPayload<Array<{ name: string; label: string; columns: string[] }>>(response);
}

export async function fetchAdminTableRows(session: AdminSession | null, table: string) {
  const response = await fetch(`/api/admin/tables/${encodeURIComponent(table)}?size=50`, { headers: authHeaders(session) });
  return readPayload<Array<Record<string, unknown>>>(response);
}

export async function retryAiProcessing(session: AdminSession | null, id: number) {
  const response = await fetch(`/api/admin/ai-processing/${id}/retry`, {
    method: 'POST',
    headers: authHeaders(session)
  });
  return readPayload<{ id: number; status: string }>(response);
}

export async function updateAiConfig(session: AdminSession | null, payload: {
  mode: 'rule' | 'llm'; baseUrl: string; model: string; apiKey?: string;
}) {
  const response = await fetch('/api/admin/ai-config', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders(session) },
    body: JSON.stringify(payload)
  });
  return readPayload<AiConfig>(response);
}

export async function fetchChangeLogs(session: AdminSession | null, size = 50) {
  const response = await fetch(`/api/admin/change-logs?size=${size}`, {
    headers: authHeaders(session)
  });
  return readPayload<Array<{
    id: number; itemId: number; itemTitle: string; sourceName: string;
    oldContentHash: string; newContentHash: string; changedFields: string; changedAt: string;
  }>>(response);
}
