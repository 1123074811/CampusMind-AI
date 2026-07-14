import type { AdminSession, ApiResponse, LoginResponse } from '../adminTypes';

const SESSION_KEY = 'campusmind-admin-session';
let refreshInFlight: Promise<AdminSession> | null = null;

export async function login(username: string, password: string) {
  const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ username, password })
  });

  const payload = await response.json() as ApiResponse<LoginResponse>;
  if (!response.ok || !payload.success) {
    throw new Error(payload.message || '登录失败');
  }

  const session: AdminSession = {
    ...payload.data,
    demo: false
  };
  saveSession(session);
  return session;
}

export function loadSession() {
  const raw = localStorage.getItem(SESSION_KEY);
  if (!raw) {
    return null;
  }

  try {
    const session = JSON.parse(raw) as AdminSession;
    if (session.demo || !session.refreshToken || new Date(session.refreshExpiresAt).getTime() <= Date.now()) {
      clearSession();
      return null;
    }
    return session;
  } catch (error) {
    clearSession();
    return null;
  }
}

export function saveSession(session: AdminSession) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY);
}

export function authHeaders(session: AdminSession | null): Record<string, string> {
  if (!session) {
    return {};
  }
  return {
    Authorization: `${session.tokenType} ${session.accessToken}`
  };
}

export async function refreshSession(session: AdminSession) {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = performRefresh(session).finally(() => { refreshInFlight = null; });
  return refreshInFlight;
}

async function performRefresh(session: AdminSession) {
  const response = await fetch('/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: session.refreshToken })
  });
  const payload = await response.json() as ApiResponse<LoginResponse>;
  if (!response.ok || !payload.success) {
    clearSession();
    throw new Error('登录已失效，请重新登录');
  }
  Object.assign(session, payload.data, { demo: false });
  saveSession(session);
  return session;
}

export async function authorizedFetch(input: RequestInfo | URL, init: RequestInit = {}, session: AdminSession | null) {
  const send = () => {
    const headers = new Headers(init.headers);
    for (const [name, value] of Object.entries(authHeaders(session))) headers.set(name, value);
    return fetch(input, { ...init, headers });
  };
  let response = await send();
  if (response.status === 401 && session?.refreshToken) {
    await refreshSession(session);
    response = await send();
  }
  return response;
}

export async function logoutRemote(session: AdminSession | null) {
  if (!session) return;
  try {
    await authorizedFetch('/api/v1/auth/logout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: session.refreshToken })
    }, session);
  } finally {
    clearSession();
  }
}
