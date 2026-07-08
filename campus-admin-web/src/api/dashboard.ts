import type { ApiResponse, DashboardResponse, ReviewEvent } from '../adminTypes';
import type { AdminSession } from '../adminTypes';
import { authHeaders } from './auth';

export async function fetchDashboard(session: AdminSession | null) {
  const response = await fetch('/api/admin/dashboard', {
    headers: authHeaders(session)
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = await response.json() as ApiResponse<DashboardResponse>;
  if (!payload.success) {
    throw new Error(payload.message);
  }

  return payload.data;
}

export async function reviewEvent(session: AdminSession | null, id: number, status: 'REVIEWED' | 'REJECTED', comment: string) {
  const response = await fetch(`/api/admin/events/${id}/review`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': '9901',
      ...authHeaders(session)
    },
    body: JSON.stringify({ status, comment })
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = await response.json() as ApiResponse<ReviewEvent>;
  if (!payload.success) {
    throw new Error(payload.message);
  }

  return payload.data;
}
