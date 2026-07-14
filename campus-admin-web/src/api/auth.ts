import type { AdminSession, ApiResponse } from '../adminTypes';

let currentSession: AdminSession | null = null;
let refreshInFlight: Promise<AdminSession> | null = null;

async function readSession(responseOrPromise: Response | Promise<Response>) {
  const response = await responseOrPromise;
  const payload = await response.json() as ApiResponse<AdminSession>;
  if (!response.ok || !payload.success) throw new Error(payload.message || '登录失败');
  currentSession = payload.data;
  return payload.data;
}

export async function login(username: string, password: string) {
  return readSession(await fetch('/api/v1/auth/web/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  }));
}

export function loadSession() { return currentSession; }

export async function restoreSession() {
  let response = await fetch('/api/v1/auth/web/session', { credentials: 'same-origin' });
  if (response.status === 401) {
    try {
      await refreshSession();
      response = await fetch('/api/v1/auth/web/session', { credentials: 'same-origin' });
    } catch { return null; }
  }
  if (!response.ok) return null;
  return readSession(response);
}

export function clearSession() { currentSession = null; }

export async function refreshSession() {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = readSession(fetch('/api/v1/auth/web/refresh', {
    method: 'POST', credentials: 'same-origin'
  }).then(async response => {
    if (!response.ok) {
      clearSession();
      throw new Error('登录已失效，请重新登录');
    }
    return response;
  })).finally(() => { refreshInFlight = null; });
  return refreshInFlight;
}

export async function authorizedFetch(input: RequestInfo | URL, init: RequestInit = {}, _session: AdminSession | null) {
  const send = () => fetch(input, { ...init, credentials: 'same-origin' });
  let response = await send();
  if (response.status === 401 && currentSession) {
    await refreshSession();
    response = await send();
  }
  return response;
}

export async function logoutRemote(_session: AdminSession | null) {
  try {
    await fetch('/api/v1/auth/web/logout', { method: 'POST', credentials: 'same-origin' });
  } finally { clearSession(); }
}
