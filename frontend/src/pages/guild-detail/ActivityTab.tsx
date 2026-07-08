import { useCallback, useMemo, useState, type ReactNode } from 'react';

import { api } from '../../api/client';
import type { ActivityAnalytics } from '../../api/types';
import {
  MAX_RANGE_DAYS,
  isUtcHourAligned,
  last24Hours,
  last7Days,
  localInputToUtcHour,
  rangeDays,
} from '../../api/time';
import { formatCount, formatUtcHour } from '../../components/format';
import { Banner, EmptyState, ErrorState, LoadingState } from '../../components/states';
import { describeError, useAsync } from '../../hooks/useAsync';

type Preset = '24h' | '7d' | 'custom';

interface Range {
  from: string;
  to: string;
}

const SUMMARY_FIELDS: { key: keyof ActivityAnalytics['summary']; label: string }[] = [
  { key: 'messagesCreated', label: 'Messages created' },
  { key: 'humanMessages', label: 'Human messages' },
  { key: 'botMessages', label: 'Bot messages' },
  { key: 'distinctMessagesEdited', label: 'Messages edited' },
  { key: 'messagesDeleted', label: 'Messages deleted' },
  { key: 'membersJoined', label: 'Members joined' },
  { key: 'membersLeft', label: 'Members left' },
  { key: 'peakHourlyActiveMembers', label: 'Peak active members' },
  { key: 'peakHourlyActiveChannels', label: 'Peak active channels' },
];

export default function ActivityTab({ guildId }: { guildId: string }) {
  const [preset, setPreset] = useState<Preset>('24h');
  const [range, setRange] = useState<Range>(() => last24Hours());
  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo] = useState('');
  const [customError, setCustomError] = useState<string | null>(null);

  const load = useCallback(
    () => api.getActivityAnalytics(guildId, range.from, range.to),
    [guildId, range.from, range.to],
  );
  const analytics = useAsync<ActivityAnalytics>(load, [guildId, range.from, range.to]);

  const selectPreset = (next: Preset) => {
    setPreset(next);
    setCustomError(null);
    if (next === '24h') {
      setRange(last24Hours());
    } else if (next === '7d') {
      setRange(last7Days());
    }
  };

  const applyCustom = () => {
    const from = localInputToUtcHour(customFrom);
    const to = localInputToUtcHour(customTo);
    if (!from || !to) {
      setCustomError('Enter both a start and end date/time.');
      return;
    }
    if (!isUtcHourAligned(from) || !isUtcHourAligned(to)) {
      setCustomError('Range must align to UTC hour boundaries.');
      return;
    }
    const days = rangeDays(from, to);
    if (!(days > 0)) {
      setCustomError('The end must be after the start.');
      return;
    }
    if (days > MAX_RANGE_DAYS) {
      setCustomError(`The range cannot exceed ${MAX_RANGE_DAYS} days.`);
      return;
    }
    setCustomError(null);
    setRange({ from, to });
  };

  return (
    <div className="stack" style={{ gap: 20 }}>
      <div className="card card--pad">
        <h2 className="section__title" style={{ marginBottom: 4 }}>
          Activity analytics
        </h2>
        <p className="section__subtitle" style={{ marginBottom: 16 }}>
          Complete UTC-hour buckets. This is at-least-once processing with idempotent projection —
          not exact-once accounting.
        </p>

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <PresetButton active={preset === '24h'} onClick={() => selectPreset('24h')}>
            Last 24 hours
          </PresetButton>
          <PresetButton active={preset === '7d'} onClick={() => selectPreset('7d')}>
            Last 7 days
          </PresetButton>
          <PresetButton active={preset === 'custom'} onClick={() => selectPreset('custom')}>
            Custom range
          </PresetButton>
        </div>

        {preset === 'custom' ? (
          <div
            style={{
              display: 'flex',
              gap: 12,
              flexWrap: 'wrap',
              alignItems: 'flex-end',
              marginTop: 16,
            }}
          >
            <div className="field" style={{ flex: '1 1 200px' }}>
              <label className="field__label" htmlFor="from">
                From
              </label>
              <input
                id="from"
                type="datetime-local"
                className="input"
                value={customFrom}
                onChange={(event) => setCustomFrom(event.target.value)}
              />
            </div>
            <div className="field" style={{ flex: '1 1 200px' }}>
              <label className="field__label" htmlFor="to">
                To
              </label>
              <input
                id="to"
                type="datetime-local"
                className="input"
                value={customTo}
                onChange={(event) => setCustomTo(event.target.value)}
              />
            </div>
            <button type="button" className="btn btn--primary" onClick={applyCustom}>
              Load range
            </button>
          </div>
        ) : null}

        {customError ? (
          <div style={{ marginTop: 12 }}>
            <Banner tone="error">{customError}</Banner>
          </div>
        ) : null}

        <p className="dim" style={{ fontSize: '0.8rem', marginTop: 14 }}>
          Showing {formatUtcHour(range.from)} → {formatUtcHour(range.to)} (from inclusive, to
          exclusive).
        </p>
      </div>

      {analytics.loading ? (
        <div className="card">
          <LoadingState label="Loading analytics…" />
        </div>
      ) : analytics.error ? (
        <div className="card">
          <ErrorState message={describeError(analytics.error)} onRetry={analytics.reload} />
        </div>
      ) : analytics.data ? (
        <AnalyticsResult data={analytics.data} />
      ) : null}
    </div>
  );
}

function PresetButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      className={`btn btn--sm ${active ? 'btn--primary' : 'btn--ghost'}`}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

function AnalyticsResult({ data }: { data: ActivityAnalytics }) {
  const hasData = data.buckets.length > 0;
  const nonEmptyBuckets = useMemo(
    () =>
      data.buckets.filter(
        (bucket) =>
          bucket.messagesCreated +
            bucket.messagesDeleted +
            bucket.distinctMessagesEdited +
            bucket.membersJoined +
            bucket.membersLeft +
            bucket.activeMembers +
            bucket.activeChannels >
          0,
      ),
    [data.buckets],
  );

  return (
    <div className="stack" style={{ gap: 20 }}>
      <div className="grid-cards">
        {SUMMARY_FIELDS.map((field) => (
          <div key={field.key} className="stat-card">
            <span className="stat-card__label">{field.label}</span>
            <span className="stat-card__value">{formatCount(data.summary[field.key])}</span>
          </div>
        ))}
      </div>

      <div>
        <h3 className="section__title" style={{ marginBottom: 4 }}>
          Hourly buckets
        </h3>
        <p className="section__subtitle">
          {hasData
            ? `${formatCount(data.buckets.length)} complete hours · timezone ${data.bucketTimezone}`
            : 'No hourly buckets in this range.'}
        </p>

        {!hasData ? (
          <div className="card">
            <EmptyState icon="📊" title="No activity recorded">
              There is no activity data for this range yet. Try a wider window, or check back once
              the bot has been active in this guild.
            </EmptyState>
          </div>
        ) : nonEmptyBuckets.length === 0 ? (
          <div className="card">
            <EmptyState icon="😴" title="All hours are empty">
              Every hour in this range recorded zero activity.
            </EmptyState>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th scope="col">Hour (UTC)</th>
                  <th scope="col">Created</th>
                  <th scope="col">Human</th>
                  <th scope="col">Bot</th>
                  <th scope="col">Edited</th>
                  <th scope="col">Deleted</th>
                  <th scope="col">Joins</th>
                  <th scope="col">Leaves</th>
                  <th scope="col">Active members</th>
                  <th scope="col">Active channels</th>
                </tr>
              </thead>
              <tbody>
                {nonEmptyBuckets.map((bucket) => (
                  <tr key={bucket.startedAt}>
                    <td>{formatUtcHour(bucket.startedAt)}</td>
                    <td>{formatCount(bucket.messagesCreated)}</td>
                    <td>{formatCount(bucket.humanMessages)}</td>
                    <td>{formatCount(bucket.botMessages)}</td>
                    <td>{formatCount(bucket.distinctMessagesEdited)}</td>
                    <td>{formatCount(bucket.messagesDeleted)}</td>
                    <td>{formatCount(bucket.membersJoined)}</td>
                    <td>{formatCount(bucket.membersLeft)}</td>
                    <td>{formatCount(bucket.activeMembers)}</td>
                    <td>{formatCount(bucket.activeChannels)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
