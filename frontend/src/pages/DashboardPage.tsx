import { useCallback } from 'react';
import { Link } from 'react-router-dom';

import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { formatCount } from '../components/format';
import { ErrorState, LoadingState } from '../components/states';
import { describeError, useAsync } from '../hooks/useAsync';

export default function DashboardPage() {
  const { operator } = useAuth();
  const loadGuilds = useCallback(() => api.listAuthorizedGuilds(), []);
  const guilds = useAsync(loadGuilds);

  const operatorName = operator?.displayName || operator?.username || 'operator';

  return (
    <div>
      <div className="page-head">
        <h1 className="page-head__title">Dashboard</h1>
        <p className="page-head__subtitle">
          Welcome back, {operatorName}. Here is a snapshot of what you manage in Guild OS.
        </p>
      </div>

      <div className="grid-cards">
        <div className="stat-card">
          <span className="stat-card__label">Signed in as</span>
          <span className="stat-card__value" style={{ fontSize: '1.15rem' }}>
            {operatorName}
          </span>
          {operator?.username ? (
            <span className="stat-card__hint">@{operator.username}</span>
          ) : null}
        </div>

        <div className="stat-card">
          <span className="stat-card__label">Active guild authorizations</span>
          <span className="stat-card__value">
            {guilds.loading ? '—' : formatCount(guilds.data?.length ?? 0)}
          </span>
          <span className="stat-card__hint">Guilds you can currently manage</span>
        </div>

        <Link
          to="/dashboard/guilds"
          className="stat-card"
          style={{ textDecoration: 'none', justifyContent: 'space-between' }}
        >
          <span className="stat-card__label">Manage guilds</span>
          <span className="stat-card__value" style={{ fontSize: '1.15rem' }}>
            Onboard &amp; configure →
          </span>
          <span className="stat-card__hint">View active and eligible guilds</span>
        </Link>
      </div>

      <section className="section">
        <h2 className="section__title">Your active guilds</h2>
        <p className="section__subtitle">
          Guilds where you hold an active Guild OS authorization.
        </p>

        {guilds.loading ? (
          <div className="card">
            <LoadingState label="Loading your guilds…" />
          </div>
        ) : guilds.error ? (
          <div className="card">
            <ErrorState message={describeError(guilds.error)} onRetry={guilds.reload} />
          </div>
        ) : (guilds.data?.length ?? 0) === 0 ? (
          <div className="card card--pad">
            <p className="muted">
              You have no active guild authorizations yet.{' '}
              <Link to="/dashboard/guilds" style={{ color: 'var(--brand)' }}>
                Onboard an eligible guild
              </Link>{' '}
              to get started.
            </p>
          </div>
        ) : (
          <div className="guild-list">
            {guilds.data?.map((guild) => (
              <Link
                key={guild.guildId}
                to={`/dashboard/guilds/${guild.guildId}`}
                className="guild-card"
              >
                <div className="guild-card__head">
                  <span className="guild-icon" aria-hidden="true">
                    {(guild.name ?? '#').slice(0, 1).toUpperCase()}
                  </span>
                  <div>
                    <div className="guild-card__name">{guild.name ?? 'Unnamed guild'}</div>
                    <div className="guild-card__id mono">{guild.guildId}</div>
                  </div>
                </div>
                {guild.role ? (
                  <div className="guild-card__foot">
                    <span className="badge badge--role">{guild.role}</span>
                    <span className="dim">Open →</span>
                  </div>
                ) : null}
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
