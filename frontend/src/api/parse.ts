/**
 * Small, dependency-free helpers for parsing `unknown` JSON into typed values defensively.
 *
 * The backend contracts are stable, but the UI treats responses as untrusted input: it reads only
 * the fields it renders, coerces types safely, and tolerates missing or extra fields so a minor
 * backend change does not throw in the render path.
 */

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function asString(value: unknown): string | null {
  return typeof value === 'string' ? value : null;
}

export function asRequiredString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

/** Coerces a numeric field to a finite number, defaulting to 0 for anything unexpected. */
export function asNumber(value: unknown, fallback = 0): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  return fallback;
}

/** Maps an unknown array-like into a typed list, dropping entries the mapper rejects. */
export function asArray<T>(value: unknown, map: (item: unknown) => T | null): T[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const result: T[] = [];
  for (const item of value) {
    const mapped = map(item);
    if (mapped !== null) {
      result.push(mapped);
    }
  }
  return result;
}
