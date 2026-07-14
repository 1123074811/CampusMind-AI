import type { AdminSession, ApiResponse, BatchCrawlResult, CrawlItem, CrawlSourceResult } from '../adminTypes';
import { authorizedFetch } from './auth';

export async function crawlSource(session: AdminSession | null, sourceId: number, days = 30) {
  const params = new URLSearchParams({ days: String(days) });
  const response = await authorizedFetch(`/api/admin/crawler/sources/${sourceId}/crawl?${params.toString()}`, { method: 'POST' }, session);

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = await response.json() as ApiResponse<CrawlSourceResult>;
  if (!payload.success) {
    throw new Error(payload.message);
  }
  return payload.data;
}

export async function crawlEnabledSources(session: AdminSession | null) {
  const params = new URLSearchParams({ days: '30' });
  const response = await authorizedFetch(`/api/admin/crawler/sources/crawl?${params.toString()}`, { method: 'POST' }, session);

  if (response.ok) {
    const payload = await response.json() as ApiResponse<BatchCrawlResult>;
    if (!payload.success) {
      throw new Error(payload.message);
    }
    return payload.data;
  }

  if (response.status !== 404) {
    throw new Error(`HTTP ${response.status}`);
  }

  throw new Error('批量采集接口不可用');
}

export async function fetchCrawlItems(session: AdminSession | null, size = 30) {
  const params = new URLSearchParams({ size: String(size) });
  const response = await authorizedFetch(`/api/admin/crawler/items?${params.toString()}`, {}, session);

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = await response.json() as ApiResponse<CrawlItem[]>;
  if (!payload.success) {
    throw new Error(payload.message);
  }
  return payload.data;
}
