import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { api } from '../api/client';
import type { AuthorizedGuild } from '../api/types';
import { initials } from '../components/format';
import { ErrorState, LoadingState } from '../components/states';
import { describeError, useAsync } from '../hooks/useAsync';
import AccessTab from './guild-detail/AccessTab';
import ActivityTab from './guild-detail/ActivityTab';
import OverviewTab from './guild-detail/OverviewTab';
import SettingsTab from './guild-detail/SettingsTab';

type TabKey = 'overview' | 'settings' | 'activity' | 'access';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'overview', label: 'Overview' },
  { key: 'settings', label: 'Settings' },
  { key: 'activity', label: 'Activity' },
  { key: 'access', label: 'Access' },
];

export default function GuildDetailPage() {
  const { discordGuildId = '' } = useParams();
  const [activeTab, setActiveTab] = useState<TabKey>('overview');

  // The authorized-guilds list is the safe source for this guild's name/role. If the guild is not
  // in the list (e.g. access was revoked), tabs still work off the id and surface their own errors.
  const loadGuild = useCallback(async (): Promise<AuthorizedGuild | null> => {
    const guilds = await api.listAuthorizedGuilds();
    return guilds.find((guild) => guild.guildId === discordGuildId) ?? null;
  }, [discordGuildId]);

  const guild = useAsync<AuthorizedGuild | null>(loadGuild, [discordGuildId]);

  const displayName = guild.data?.name ?? 'Guild';

  return (
    <div>
      <div className="page-head">
        <Link to="/dashboard/guilds" className="dim" style={{ fontSize: '0.85rem' }}>
          ← Back to guilds
        </Link>
        <div className="row-between" style={{ marginTop: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <span className="guild-icon" aria-hidden="true">
              {initials(guild.data?.name, '#')}
            </span>
            <div>
              <h1 className="page-head__title">{guild.loading ? 'Loading…' : displayName}</h1>
              <div className="guild-card__id mono">{discordGuildId}</div>
            </div>
          </div>
          {guild.data?.role ? <span className="badge badge--role">{guild.data.role}</span> : null}
        </div>
      </div>

      {guild.error ? (
        <div className="card">
          <ErrorState message={describeError(guild.error)} onRetry={guild.reload} />
        </div>
      ) : null}

      <div className="tabs" role="tablist" aria-label="Guild sections">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.key}
            className={`tab ${activeTab === tab.key ? 'is-active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'overview' ? (
        guild.loading ? (
          <LoadingState label="Loading guild…" />
        ) : (
          <OverviewTab guildId={discordGuildId} guild={guild.data} />
        )
      ) : null}
      {activeTab === 'settings' ? <SettingsTab guildId={discordGuildId} /> : null}
      {activeTab === 'activity' ? <ActivityTab guildId={discordGuildId} /> : null}
      {activeTab === 'access' ? (
        <AccessTab guildId={discordGuildId} guildName={guild.data?.name ?? null} />
      ) : null}
    </div>
  );
}
