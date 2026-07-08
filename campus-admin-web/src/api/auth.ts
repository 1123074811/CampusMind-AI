import type { AdminSession, ApiResponse, LoginResponse } from '../adminTypes';

const SESSION_KEY = 'campusmind-admin-session';

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
    if (session.demo || new Date(session.expiresAt).getTime() <= Date.now()) {
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
