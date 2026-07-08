/** Small presentation helpers shared across pages. No sensitive data is ever formatted here. */

/** Uppercase initials from a name or id, for avatar/icon fallbacks. */
export function initials(source: string | null | undefined, fallback = '?'): string {
  const value = (source ?? '').trim();
  if (!value) {
    return fallback;
  }
  const parts = value.split(/\s+/).filter(Boolean);
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/** Formats an ISO instant as a readable local date-time; returns a dash for missing/invalid input. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) {
    return '—';
  }
  return parsed.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Formats a UTC-hour bucket start as `MMM dd HH:00 UTC`. */
export function formatUtcHour(iso: string): string {
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) {
    return iso;
  }
  const date = parsed.toLocaleDateString('en-US', {
    month: 'short',
    day: '2-digit',
    timeZone: 'UTC',
  });
  const hour = parsed.toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'UTC',
  });
  return `${date} ${hour} UTC`;
}

/** Compact integer formatting (e.g. 1,240). */
export function formatCount(value: number): string {
  return new Intl.NumberFormat().format(value);
}

/** Human-readable label for an onboarding status. */
export function onboardingStatusLabel(status: string): string {
  switch (status) {
    case 'AVAILABLE':
      return 'Available';
    case 'ONBOARDED':
      return 'Onboarded';
    case 'REVOKED':
      return 'Revoked';
    default:
      return status;
  }
}

/** Maps an onboarding status to its badge modifier class. */
export function onboardingBadgeClass(status: string): string {
  switch (status) {
    case 'ONBOARDED':
      return 'badge--onboarded';
    case 'REVOKED':
      return 'badge--revoked';
    default:
      return 'badge--available';
  }
}
