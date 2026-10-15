import type { ActivityAnalytics } from '../../api/types';
import { isUtcHourAligned, MAX_RANGE_DAYS } from '../../api/time';

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

export interface MessagePoint {
  from: string;
  to: string;
  messagesCreated: number;
  partial: boolean;
}

export interface MessageSeries {
  granularity: 'hour' | 'day';
  points: MessagePoint[];
  total: number;
}

/** Derives recorded counts within the response range without changing the API buckets. */
export function buildMessageSeries(
  data: Pick<ActivityAnalytics, 'from' | 'to' | 'buckets'>,
): MessageSeries | null {
  const from = Date.parse(data.from);
  const to = Date.parse(data.to);
  if (!isUtcHourAligned(data.from) || !isUtcHourAligned(data.to)
    || to <= from || to - from > MAX_RANGE_DAYS * DAY_MS) {
    return null;
  }

  const granularity = to - from <= DAY_MS ? 'hour' : 'day';
  const step = granularity === 'hour' ? HOUR_MS : DAY_MS;
  const byStart = new Map<number, MessagePoint>();
  for (let start = Math.floor(from / step) * step; start < to; start += step) {
    const coveredFrom = Math.max(from, start);
    const coveredTo = Math.min(to, start + step);
    byStart.set(start, {
      from: new Date(coveredFrom).toISOString(),
      to: new Date(coveredTo).toISOString(),
      messagesCreated: 0,
      partial: coveredFrom !== start || coveredTo !== start + step,
    });
  }

  for (const bucket of data.buckets) {
    const instant = Date.parse(bucket.startedAt);
    if (!isUtcHourAligned(bucket.startedAt)) {
      return null;
    }
    if (instant < from || instant >= to) {
      continue;
    }
    if (!Number.isSafeInteger(bucket.messagesCreated) || bucket.messagesCreated < 0) {
      return null;
    }
    const point = byStart.get(Math.floor(instant / step) * step)!;
    point.messagesCreated += bucket.messagesCreated;
    if (!Number.isSafeInteger(point.messagesCreated)) {
      return null;
    }
  }

  const points = [...byStart.values()];
  const total = points.reduce((sum, point) => sum + point.messagesCreated, 0);
  return Number.isSafeInteger(total) ? { granularity, points, total } : null;
}
