import type { ReactNode } from 'react';

/** Centered loading indicator with an accessible live label. */
export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span className="muted">{label}</span>
    </div>
  );
}

/** Empty state for when a request succeeded but returned nothing to show. */
export function EmptyState({
  icon = '📭',
  title,
  children,
}: {
  icon?: string;
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="state">
      <span className="state__icon" aria-hidden="true">
        {icon}
      </span>
      <span className="state__title">{title}</span>
      {children ? <p className="muted">{children}</p> : null}
    </div>
  );
}

/** Error state with an optional retry action. */
export function ErrorState({
  title = 'Something went wrong',
  message,
  onRetry,
}: {
  title?: string;
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div className="state">
      <span className="state__icon" aria-hidden="true">
        ⚠️
      </span>
      <span className="state__title">{title}</span>
      <p className="muted">{message}</p>
      {onRetry ? (
        <button type="button" className="btn btn--sm" onClick={onRetry}>
          Try again
        </button>
      ) : null}
    </div>
  );
}

type BannerTone = 'info' | 'success' | 'error';

const BANNER_ICON: Record<BannerTone, string> = {
  info: 'ℹ️',
  success: '✅',
  error: '⚠️',
};

/** Inline feedback banner used for success/conflict/error messages on a page. */
export function Banner({ tone, children }: { tone: BannerTone; children: ReactNode }) {
  return (
    <div className={`banner banner--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <span aria-hidden="true">{BANNER_ICON[tone]}</span>
      <span>{children}</span>
    </div>
  );
}
