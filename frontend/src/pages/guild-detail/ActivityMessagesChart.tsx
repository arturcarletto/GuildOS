import { useId, useMemo } from 'react';

import type { ActivityAnalytics } from '../../api/types';
import { formatCount, formatUtcHour } from '../../components/format';
import { buildMessageSeries } from './activitySeries';

function intervalLabel(from: string, to: string): string {
  const utc = (iso: string) => iso.replace('T', ' ').replace('.000Z', ' UTC');
  return `${utc(from)} to ${utc(to)} (end exclusive)`;
}

export default function ActivityMessagesChart({ data }: { data: ActivityAnalytics }) {
  const id = useId();
  const series = useMemo(() => buildMessageSeries(data), [data]);
  if (!series) {
    return (
      <div className="card card--pad">
        <h3 className="section__title">Messages created</h3>
        <p role="status">The message chart is unavailable for this response.</p>
      </div>
    );
  }

  const { points, granularity, total } = series;
  const maximum = Math.max(1, ...points.map((point) => point.messagesCreated));
  const left = Math.max(60, formatCount(maximum).length * 8 + 12);
  const slot = 38;
  const width = left + points.length * slot + 20;
  const top = 24;
  const plotHeight = 164;
  const baseline = top + plotHeight;
  const title = `Messages created by ${granularity} (UTC)`;
  const hasPartialDays = points.some((point) => point.partial);
  const description = `${points.length} bars. ${formatCount(total)} messages recorded. `
    + `${intervalLabel(data.from, data.to)}. Exact values are available in View chart values.`;

  return (
    <figure className="card card--pad activity-chart">
      <figcaption>
        <h3 className="section__title">{title}</h3>
        <p className="section__subtitle">
          {formatUtcHour(data.from)} to {formatUtcHour(data.to)}.{' '}
          {granularity === 'day' ? 'Daily totals within the selected range.' : 'One bar per UTC hour.'}
        </p>
      </figcaption>
      {total === 0 ? <p>No messages created were recorded in this range.</p> : null}
      <p className="muted activity-chart__hint">Scroll horizontally for all bars. Expand the values below for exact counts.</p>
      <div className="activity-chart__plot" role="region" aria-label="Message chart, horizontally scrollable" tabIndex={0}>
        <svg
          role="img"
          aria-labelledby={`${id}-title`}
          aria-describedby={`${id}-description`}
          viewBox={`0 0 ${width} 258`}
          style={{ minWidth: width }}
        >
          <title id={`${id}-title`}>{title}</title>
          <desc id={`${id}-description`}>{description}</desc>
          <text x={left} y={14}>Messages</text>
          {[0, maximum].map((value) => {
            const y = baseline - (value / maximum) * plotHeight;
            return (
              <g key={value}>
                <line x1={left} x2={width - 20} y1={y} y2={y} className="activity-chart__axis" />
                <text x={left - 8} y={y + 4} textAnchor="end">{formatCount(value)}</text>
              </g>
            );
          })}
          <line x1={left} x2={left} y1={top} y2={baseline} className="activity-chart__axis" />
          {points.map((point, index) => {
            const x = left + index * slot;
            const height = (point.messagesCreated / maximum) * plotHeight;
            const label = granularity === 'day'
              ? point.from.slice(5, 10)
              : point.from.slice(11, 16);
            return (
              <g key={point.from}>
                <rect
                  className="activity-chart__bar"
                  x={x + 7}
                  y={baseline - height}
                  width={slot - 14}
                  height={height}
                >
                  <title>{intervalLabel(point.from, point.to)}: {formatCount(point.messagesCreated)} messages</title>
                </rect>
                <text x={x + slot / 2} y={baseline + 22} textAnchor="middle">
                  {label}{point.partial ? '*' : ''}
                </text>
              </g>
            );
          })}
          <text x={(left + width - 20) / 2} y={250} textAnchor="middle">
            {granularity === 'day' ? 'Date (UTC)' : 'Hour (UTC)'}
          </text>
        </svg>
      </div>
      {hasPartialDays ? (
        <p className="muted">* Partial UTC day: only hours inside the selected range are counted.</p>
      ) : null}
      <details className="activity-chart__values">
        <summary>View chart values</summary>
        <div className="table-wrap">
          <table className="data" aria-label="Message chart values">
            <thead>
              <tr>
                <th scope="col">Interval (UTC, end exclusive)</th>
                <th scope="col">Coverage</th>
                <th scope="col">Messages created</th>
              </tr>
            </thead>
            <tbody>
              {points.map((point) => (
                <tr key={point.from}>
                  <th scope="row">{intervalLabel(point.from, point.to)}</th>
                  <td>{point.partial ? 'Partial UTC day' : `Complete ${granularity}`}</td>
                  <td>{formatCount(point.messagesCreated)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
      <p className="muted activity-chart__hint">
        Missing hours represent zero events recorded, not proof of healthy ingestion.
      </p>
    </figure>
  );
}
