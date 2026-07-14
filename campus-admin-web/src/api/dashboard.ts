import type { ApiResponse, DashboardResponse, ReviewEvent } from '../adminTypes';
import type { AdminSession } from '../adminTypes';
import { authorizedFetch } from './auth';

export async function fetchDashboard(session: AdminSession | null) {
  const response = await authorizedFetch('/api/admin/dashboard', {}, session);
  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('登录已失效，请重新登录');
    }
    throw new Error(`后台接口异常（HTTP ${response.status}）`);
  }

  const payload = await response.json() as ApiResponse<DashboardResponse>;
  if (!payload.success) {
    throw new Error(payload.message);
  }

  return payload.data;
}

export async function reviewEvent(session: AdminSession | null, id: number, status: 'REVIEWED' | 'REJECTED' | 'CORRECTED' | 'OFFLINE', comment: string) {
  const response = await authorizedFetch(`/api/admin/events/${id}/review`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ status, comment })
  }, session);

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = await response.json() as ApiResponse<ReviewEvent>;
  if (!payload.success) {
    throw new Error(payload.message);
  }

  return payload.data;
}

export async function updateEvent(session: AdminSession | null, id: number, data: { title?: string; summary?: string; eventType?: string }) {
  const response = await authorizedFetch(`/api/admin/events/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data)
  }, session);

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = await response.json() as ApiResponse<ReviewEvent>;
  if (!payload.success) {
    throw new Error(payload.message);
  }

  return payload.data;
}

export async function deleteEvent(session: AdminSession | null, id: number) {
  const response = await authorizedFetch(`/api/admin/events/${id}`, {
    method: 'DELETE',
  }, session);

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = await response.json() as ApiResponse<void>;
  if (!payload.success) {
    throw new Error(payload.message);
  }
}

export async function batchDeleteEvents(session: AdminSession | null, ids: number[]) {
  const response = await authorizedFetch('/api/admin/events/batch-delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ ids })
  }, session);

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = await response.json() as ApiResponse<void>;
  if (!payload.success) {
    throw new Error(payload.message);
  }
}
