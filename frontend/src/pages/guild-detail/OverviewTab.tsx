import type { AuthorizedGuild } from '../../api/types';

/** Read-only summary of the guild built from safe fields the authorized-guilds API returns. */
export default function OverviewTab({
  guildId,
  guild,
}: {
  guildId: string;
  guild: AuthorizedGuild | null;
}) {
  return (
    <div className="card card--pad">
      <h2 className="section__title" style={{ marginBottom: 16 }}>
        Overview
      </h2>
      <dl className="def-list">
        <dt>Guild name</dt>
        <dd>{guild?.name ?? <span className="dim">Not available</span>}</dd>

        <dt>Discord guild ID</dt>
        <dd className="mono">{guildId}</dd>

        <dt>Your role</dt>
        <dd>
          {guild?.role ? (
            <span className="badge badge--role">{guild.role}</span>
          ) : (
            <span className="dim">Not available</span>
          )}
        </dd>

        <dt>Status</dt>
        <dd>
          {guild ? (
            <span className="badge badge--onboarded">Active authorization</span>
          ) : (
            <span className="badge badge--revoked">No active authorization</span>
          )}
        </dd>
      </dl>

      {!guild ? (
        <p className="muted" style={{ marginTop: 16 }}>
          You don’t currently hold an active authorization for this guild. Settings and analytics may
          be unavailable until you onboard it again.
        </p>
      ) : null}
    </div>
  );
}
