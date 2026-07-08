import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';

import { api } from '../api/client';
import {
  formatCount,
  initials,
  onboardingBadgeClass,
  onboardingStatusLabel,
} from '../components/format';
import { Banner, EmptyState, ErrorState, LoadingState } from '../components/states';
import type { AuthorizedGuild, EligibleGuild } from '../api/types';
import { describeError, useAsync } from '../hooks/useAsync';

interface GuildsData {
  active: AuthorizedGuild[];
  eligible: EligibleGuild[];
}

const DISCORD_ICON_BASE = 'https://cdn.discordapp.com/icons';

function guildIconUrl(guildId: string, iconHash: string | null): string | null {
  return iconHash ? `${DISCORD_ICON_BASE}/${guildId}/${iconHash}.png?size=64` : null;
}

export default function GuildsPage() {
  const load = useCallback(async (): Promise<GuildsData> => {
    const [active, eligible] = await Promise.all([
      api.listAuthorizedGuilds(),
      api.listEligibleGuilds(),
    ]);
    return { active, eligible };
  }, []);

  const guilds = useAsync<GuildsData>(load);
  const [onboardingId, setOnboardingId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<
    { tone: 'success' | 'error'; message: string } | null
  >(null);

  const handleOnboard = async (guild: EligibleGuild) => {
    setOnboardingId(guild.guildId);
    setFeedback(null);
    try {
      await api.onboardGuild(guild.guildId);
      setFeedback({
        tone: 'success',
        message: `${guild.name ?? 'Guild'} is now onboarded.`,
      });
      guilds.reload();
    } catch (error) {
      setFeedback({ tone: 'error', message: describeError(error) });
    } finally {
      setOnboardingId(null);
    }
  };

  return (
    <div>
      <div className="page-head">
        <h1 className="page-head__title">Guilds</h1>
        <p className="page-head__subtitle">
          Onboard guilds you administer on Discord, then manage the ones Guild OS already protects.
        </p>
      </div>

      {feedback ? <Banner tone={feedback.tone}>{feedback.message}</Banner> : null}

      {guilds.loading ? (
        <div className="card">
          <LoadingState label="Loading guilds…" />
        </div>
      ) : guilds.error ? (
        <div className="card">
          <ErrorState message={describeError(guilds.error)} onRetry={guilds.reload} />
        </div>
      ) : (
        <>
          <ActiveGuildsSection guilds={guilds.data?.active ?? []} />
          <EligibleGuildsSection
            guilds={guilds.data?.eligible ?? []}
            onboardingId={onboardingId}
            onOnboard={handleOnboard}
          />
        </>
      )}
    </div>
  );
}

function ActiveGuildsSection({ guilds }: { guilds: AuthorizedGuild[] }) {
  return (
    <section className="section">
      <div className="row-between">
        <h2 className="section__title">
          Active guilds{' '}
          <span className="dim" style={{ fontWeight: 400 }}>
            ({formatCount(guilds.length)})
          </span>
        </h2>
      </div>
      <p className="section__subtitle">Guilds you can currently manage.</p>

      {guilds.length === 0 ? (
        <div className="card">
          <EmptyState icon="🛡️" title="No active guilds yet">
            Onboard an eligible guild below to start managing it.
          </EmptyState>
        </div>
      ) : (
        <div className="guild-list">
          {guilds.map((guild) => (
            <Link
              key={guild.guildId}
              to={`/dashboard/guilds/${guild.guildId}`}
              className="guild-card"
            >
              <div className="guild-card__head">
                <span className="guild-icon" aria-hidden="true">
                  {initials(guild.name, '#')}
                </span>
                <div>
                  <div className="guild-card__name">{guild.name ?? 'Unnamed guild'}</div>
                  <div className="guild-card__id mono">{guild.guildId}</div>
                </div>
              </div>
              <div className="guild-card__foot">
                {guild.role ? <span className="badge badge--role">{guild.role}</span> : <span />}
                <span className="dim">Open →</span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}

function EligibleGuildsSection({
  guilds,
  onboardingId,
  onOnboard,
}: {
  guilds: EligibleGuild[];
  onboardingId: string | null;
  onOnboard: (guild: EligibleGuild) => void;
}) {
  return (
    <section className="section">
      <h2 className="section__title">
        Eligible to onboard{' '}
        <span className="dim" style={{ fontWeight: 400 }}>
          ({formatCount(guilds.length)})
        </span>
      </h2>
      <p className="section__subtitle">
        Guilds where you own the server or hold Administrator / Manage Server, and the Guild OS bot
        is connected.
      </p>

      {guilds.length === 0 ? (
        <div className="card">
          <EmptyState icon="🔍" title="No eligible guilds found">
            Install the Guild OS bot in a server you administer, then check back here.
          </EmptyState>
        </div>
      ) : (
        <div className="guild-list">
          {guilds.map((guild) => {
            const canOnboard =
              guild.onboardingStatus === 'AVAILABLE' || guild.onboardingStatus === 'REVOKED';
            const busy = onboardingId === guild.guildId;
            const iconUrl = guildIconUrl(guild.guildId, guild.iconHash);
            return (
              <div key={guild.guildId} className="guild-card">
                <div className="guild-card__head">
                  {iconUrl ? (
                    <img className="guild-icon" src={iconUrl} alt="" width={42} height={42} />
                  ) : (
                    <span className="guild-icon" aria-hidden="true">
                      {initials(guild.name, '#')}
                    </span>
                  )}
                  <div>
                    <div className="guild-card__name">{guild.name ?? 'Unnamed guild'}</div>
                    <div className="guild-card__id mono">{guild.guildId}</div>
                  </div>
                </div>
                <div className="guild-card__foot">
                  <span className={`badge ${onboardingBadgeClass(guild.onboardingStatus)}`}>
                    {onboardingStatusLabel(guild.onboardingStatus)}
                  </span>
                  {canOnboard ? (
                    <button
                      type="button"
                      className="btn btn--primary btn--sm"
                      onClick={() => onOnboard(guild)}
                      disabled={busy}
                    >
                      {busy ? 'Onboarding…' : 'Onboard'}
                    </button>
                  ) : (
                    <Link
                      to={`/dashboard/guilds/${guild.guildId}`}
                      className="btn btn--ghost btn--sm"
                    >
                      Manage
                    </Link>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
