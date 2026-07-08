import { useCallback } from 'react';

import { api } from '../../api/client';
import type { GuildAuditEvent, GuildAuditLog } from '../../api/types';
import { formatDateTime } from '../../components/format';
import { EmptyState, ErrorState, LoadingState } from '../../components/states';
import { describeError, useAsync } from '../../hooks/useAsync';

const DEFAULT_LIMIT = 50;

export default function AuditLogTab({ guildId }: { guildId: string }) {
  const load = useCallback(
    () => api.getGuildAuditLog(guildId, { limit: DEFAULT_LIMIT }),
    [guildId],
  );
  const auditLog = useAsync<GuildAuditLog>(load, [guildId]);

  return (
    <div className="stack" style={{ gap: 20 }}>
      <div>
        <h2 className="section__title" style={{ marginBottom: 4 }}>
          Audit log
        </h2>
        <p className="section__subtitle">Recent guild management events.</p>
      </div>

      {auditLog.loading ? (
        <div className="card">
          <LoadingState label="Loading audit log..." />
        </div>
      ) : auditLog.error ? (
        <div className="card">
          <ErrorState message={describeError(auditLog.error)} onRetry={auditLog.reload} />
        </div>
      ) : auditLog.data ? (
        <AuditLogResult events={auditLog.data.events} />
      ) : null}
    </div>
  );
}

function AuditLogResult({ events }: { events: GuildAuditEvent[] }) {
  if (events.length === 0) {
    return (
      <div className="card">
        <EmptyState title="No audit events yet." />
      </div>
    );
  }

  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            <th scope="col">Time</th>
            <th scope="col">Event</th>
            <th scope="col">Actor</th>
            <th scope="col">Summary</th>
            <th scope="col">Target</th>
          </tr>
        </thead>
        <tbody>
          {events.map((event) => (
            <tr key={`${event.occurredAt}-${event.eventType}-${event.summary}`}>
              <td>{formatDateTime(event.occurredAt)}</td>
              <td>
                <span className="badge">{labelFromConstant(event.eventType)}</span>
              </td>
              <td>{labelFromConstant(event.actorType)}</td>
              <td>{event.summary}</td>
              <td>{event.targetLabel ?? labelFromConstant(event.targetType) ?? '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function labelFromConstant(value: string | null): string | null {
  if (!value) {
    return null;
  }
  return value
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(' ');
}
