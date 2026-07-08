/**
 * Helpers for building the UTC-hour-aligned instants the analytics endpoint requires.
 *
 * The backend rejects any `from`/`to` that is not exactly aligned to a UTC hour boundary
 * (minutes, seconds, and milliseconds must all be zero). It also requires `from` inclusive and
 * `to` exclusive, and caps the range at 31 days. These helpers generate only valid instants.
 */

const HOUR_MS = 60 * 60 * 1000;
export const MAX_RANGE_DAYS = 31;

/** Floors a Date down to the start of its UTC hour and returns an ISO instant (e.g. `...T10:00:00Z`). */
export function floorToUtcHour(date: Date): string {
  const floored = Math.floor(date.getTime() / HOUR_MS) * HOUR_MS;
  return new Date(floored).toISOString().replace(/\.\d{3}Z$/, 'Z');
}

/** True when an ISO instant is exactly aligned to a UTC hour boundary. */
export function isUtcHourAligned(iso: string): boolean {
  const parsed = Date.parse(iso);
  if (Number.isNaN(parsed)) {
    return false;
  }
  return parsed % HOUR_MS === 0;
}

interface Range {
  from: string;
  to: string;
}

/**
 * The last N complete UTC hours ending at the current hour boundary.
 * `to` is the start of the current hour (exclusive); `from` is N hours earlier (inclusive).
 */
export function lastCompleteHours(hours: number, now: Date = new Date()): Range {
  const toMs = Math.floor(now.getTime() / HOUR_MS) * HOUR_MS;
  const fromMs = toMs - hours * HOUR_MS;
  return {
    from: new Date(fromMs).toISOString().replace(/\.\d{3}Z$/, 'Z'),
    to: new Date(toMs).toISOString().replace(/\.\d{3}Z$/, 'Z'),
  };
}

/** The last 24 complete UTC hours. */
export function last24Hours(now: Date = new Date()): Range {
  return lastCompleteHours(24, now);
}

/** The last 7 days as complete UTC hours (168 hours). */
export function last7Days(now: Date = new Date()): Range {
  return lastCompleteHours(24 * 7, now);
}

/**
 * Converts a `datetime-local` input value (e.g. `2026-07-03T10:30`) into a UTC-hour-aligned instant.
 * The value is interpreted as local time, then floored to the enclosing UTC hour.
 */
export function localInputToUtcHour(value: string): string | null {
  if (!value) {
    return null;
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  return floorToUtcHour(parsed);
}

/** Number of whole days between two ISO instants. */
export function rangeDays(fromIso: string, toIso: string): number {
  const from = Date.parse(fromIso);
  const to = Date.parse(toIso);
  if (Number.isNaN(from) || Number.isNaN(to)) {
    return Number.NaN;
  }
  return (to - from) / (24 * HOUR_MS);
}
