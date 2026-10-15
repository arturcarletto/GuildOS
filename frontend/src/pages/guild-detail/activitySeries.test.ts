import { describe, expect, it } from 'vitest';

import type { ActivityBucket } from '../../api/types';
import { buildMessageSeries } from './activitySeries';

function bucket(startedAt: string, messagesCreated: number): ActivityBucket {
  return {
    startedAt, messagesCreated, humanMessages: messagesCreated, botMessages: 0,
    distinctMessagesEdited: 0, messagesDeleted: 0, membersJoined: 0, membersLeft: 0,
    activeMembers: 0, activeChannels: 0,
  };
}

describe('recorded message series', () => {
  it('orders buckets, fills gaps, includes from and excludes to without mutating input', () => {
    const data = {
      from: '2026-12-31T23:00:00Z', to: '2027-01-01T02:00:00Z',
      buckets: [
        bucket('2027-01-01T00:00:00Z', 3),
        bucket('2027-01-01T02:00:00Z', 100),
        bucket('2026-12-31T22:00:00Z', 200),
        bucket('2026-12-31T23:00:00Z', 2),
      ],
    };
    const before = structuredClone(data);
    data.buckets.forEach(Object.freeze);
    Object.freeze(data.buckets);
    Object.freeze(data);
    const result = buildMessageSeries(data)!;

    expect(result.granularity).toBe('hour');
    expect(result.points.map((point) => [point.from, point.messagesCreated])).toEqual([
      ['2026-12-31T23:00:00.000Z', 2],
      ['2027-01-01T00:00:00.000Z', 3],
      ['2027-01-01T01:00:00.000Z', 0],
    ]);
    expect(result.total).toBe(5);
    expect(result.points.every((point) => !point.partial)).toBe(true);
    expect(data).toEqual(before);
  });

  it.each([
    [1, 'hour', 1],
    [24, 'hour', 24],
    [25, 'day', 2],
    [168, 'day', 8],
    [31 * 24, 'day', 32],
  ] as const)('renders %i hours as %s with %i points', (hours, granularity, count) => {
    const from = '2026-07-01T10:00:00Z';
    const to = new Date(Date.parse(from) + hours * 3_600_000).toISOString();
    const result = buildMessageSeries({
      from, to, buckets: [bucket(from, 2), bucket(new Date(Date.parse(to) - 3_600_000).toISOString(), 3)],
    })!;
    expect(result.granularity).toBe(granularity);
    expect(result.points).toHaveLength(count);
    expect(result.total).toBe(5);
    expect(result.points[0].from).toBe('2026-07-01T10:00:00.000Z');
    expect(result.points.at(-1)?.to).toBe(to);
  });

  it('sums UTC days across year boundaries and marks only clipped edge days', () => {
    const result = buildMessageSeries({
      from: '2026-12-31T10:00:00Z', to: '2027-01-02T10:00:00Z',
      buckets: [
        bucket('2027-01-02T10:00:00Z', 99),
        bucket('2027-01-02T09:00:00Z', 7),
        bucket('2027-01-01T23:00:00Z', 5),
        bucket('2026-12-31T23:00:00Z', 3),
        bucket('2026-12-31T10:00:00Z', 2),
      ],
    })!;
    expect(result.points.map((point) => [point.messagesCreated, point.partial])).toEqual([
      [5, true], [5, false], [7, true],
    ]);
    expect(result.total).toBe(17);
    expect(result.points[0].from).toBe('2026-12-31T10:00:00.000Z');
    expect(result.points.at(-1)?.to).toBe('2027-01-02T10:00:00.000Z');
  });

  it('keeps an empty UTC day across the leap-day/month boundary', () => {
    const result = buildMessageSeries({
      from: '2024-02-28T00:00:00Z', to: '2024-03-02T00:00:00Z',
      buckets: [bucket('2024-03-01T00:00:00Z', 4), bucket('2024-02-28T23:00:00Z', 2)],
    })!;
    expect(result.points.map((point) => [point.from.slice(0, 10), point.messagesCreated, point.partial])).toEqual([
      ['2024-02-28', 2, false], ['2024-02-29', 0, false], ['2024-03-01', 4, false],
    ]);
    expect(result.total).toBe(6);
  });

  it('treats offset instants as UTC rather than grouping by their string date', () => {
    const result = buildMessageSeries({
      from: '2026-07-01T00:00:00Z', to: '2026-07-03T00:00:00Z',
      buckets: [bucket('2026-07-01T23:00:00-03:00', 4)],
    })!;
    expect(result.points.map((point) => point.messagesCreated)).toEqual([0, 4]);
  });

  it('produces finite zero-count points for empty input and all-zero buckets', () => {
    for (const buckets of [[], [bucket('2026-07-01T00:00:00Z', 0)]]) {
      const result = buildMessageSeries({
        from: '2026-07-01T00:00:00Z', to: '2026-07-02T00:00:00Z', buckets,
      })!;
      expect(result.points).toHaveLength(24);
      expect(result.total).toBe(0);
      expect(result.points.every((point) => point.messagesCreated === 0)).toBe(true);
    }
  });

  it.each([
    ['invalid', '2026-07-02T00:00:00Z'],
    ['2026-07-01T00:00:00Z', 'invalid'],
    ['2026-07-01T00:00:00Z', '2026-07-01T00:00:00Z'],
    ['2026-07-02T00:00:00Z', '2026-07-01T00:00:00Z'],
    ['2026-07-01T00:30:00Z', '2026-07-02T00:00:00Z'],
    ['2026-07-01T00:00:00Z', '2026-08-01T01:00:00Z'],
  ])('declines an invalid response range (%s to %s)', (from, to) => {
    expect(buildMessageSeries({ from, to, buckets: [] })).toBeNull();
  });

  it.each([
    bucket('invalid', 1),
    bucket('2026-07-01T00:30:00Z', 1),
    bucket('2026-07-01T00:00:00Z', -1),
    bucket('2026-07-01T00:00:00Z', Number.NaN),
  ])('declines malformed chart data instead of inventing zero activity', (invalidBucket) => {
    expect(buildMessageSeries({
      from: '2026-07-01T00:00:00Z', to: '2026-07-02T00:00:00Z', buckets: [invalidBucket],
    })).toBeNull();
  });
});
