import type { AdminSession, ApiResponse, BatchCrawlResult, CrawlItem, CrawlSourceResult } from '../adminTypes';
import { authHeaders } from './auth';

export async function crawlSource(session: AdminSession | null, sourceId: number, days = 30, maxItems = 50) {
  const params = new URLSearchParams({
    days: String(days),
    maxItems: String(maxItems)
  });
  const response = await fetch(`/api/admin/crawler/sources/${sourceId}/crawl?${params.toString()}`, {
    method: 'POST',
    headers: authHeaders(session)
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = await response.json() as ApiResponse<CrawlSourceResult>;
  if (!payload.success) {
    throw new Error(payload.message);
  }
  return payload.data;
}

export async function crawlPublicSources(session: AdminSession | null, sourceIds: number[]) {
  const params = new URLSearchParams({
    days: '30',
    maxItems: '50'
  });
  const response = await fetch(`/api/admin/crawler/sources/crawl?${params.toString()}`, {
    method: 'POST',
    headers: authHeaders(session)
  });

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

  const results: CrawlSourceResult[] = [];
  for (const sourceId of sourceIds) {
    results.push(await crawlSource(session, sourceId));
  }
  return {
    sourceCount: sourceIds.length,
    successCount: results.filter((result) => result.status === 'SUCCESS').length,
    failedCount: results.filter((result) => result.status === 'FAILED').length,
    persistedCount: results.reduce((total, result) => total + result.persistedCount, 0),
    results,
    startedAt: new Date().toISOString(),
    finishedAt: new Date().toISOString()
  };
}

export async function fetchCrawlItems(session: AdminSession | null, size = 30) {
  const params = new URLSearchParams({ size: String(size) });
  const response = await fetch(`/api/admin/crawler/items?${params.toString()}`, {
    headers: authHeaders(session)
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = await response.json() as ApiResponse<CrawlItem[]>;
  if (!payload.success) {
    throw new Error(payload.message);
  }
  return payload.data;
}
