import { describe, expect, it } from 'vitest';

import { floorToUtcHour, isUtcHourAligned, last24Hours, last7Days, rangeDays } from './time';

describe('UTC-hour alignment', () => {
  it('floors an arbitrary instant to its UTC hour', () => {
    const date = new Date('2026-07-03T10:37:41.512Z');
    expect(floorToUtcHour(date)).toBe('2026-07-03T10:00:00Z');
  });

  it('recognises aligned and unaligned instants', () => {
    expect(isUtcHourAligned('2026-07-03T10:00:00Z')).toBe(true);
    expect(isUtcHourAligned('2026-07-03T10:30:00Z')).toBe(false);
    expect(isUtcHourAligned('2026-07-03T10:00:01Z')).toBe(false);
    expect(isUtcHourAligned('not-a-date')).toBe(false);
  });

  it('produces aligned, hour-exact ranges for presets', () => {
    const now = new Date('2026-07-03T10:37:41.512Z');

    const day = last24Hours(now);
    expect(day.from).toBe('2026-07-02T10:00:00Z');
    expect(day.to).toBe('2026-07-03T10:00:00Z');
    expect(isUtcHourAligned(day.from)).toBe(true);
    expect(isUtcHourAligned(day.to)).toBe(true);
    expect(rangeDays(day.from, day.to)).toBe(1);

    const week = last7Days(now);
    expect(week.from).toBe('2026-06-26T10:00:00Z');
    expect(week.to).toBe('2026-07-03T10:00:00Z');
    expect(rangeDays(week.from, week.to)).toBe(7);
  });
});
