import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { api } from '../../api/client';
import { Banner } from '../../components/states';
import { describeError } from '../../hooks/useAsync';

/**
 * Danger zone: revoke the current operator's own access to this guild. Requires an explicit
 * confirmation interaction before the CSRF-protected DELETE is sent.
 */
export default function AccessTab({
  guildId,
  guildName,
}: {
  guildId: string;
  guildName: string | null;
}) {
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(false);
  const [revoking, setRevoking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const label = guildName ?? 'this guild';

  const handleRevoke = async () => {
    setRevoking(true);
    setError(null);
    try {
      await api.revokeAccess(guildId);
      navigate('/dashboard/guilds', { replace: true });
    } catch (caught) {
      setError(describeError(caught));
      setRevoking(false);
    }
  };

  return (
    <div className="stack" style={{ gap: 16 }}>
      <div className="card card--pad">
        <h2 className="section__title" style={{ marginBottom: 6 }}>
          Access
        </h2>
        <p className="muted">
          Revoking your access removes only <strong>your</strong> Guild OS authorization for {label}.
          It never disconnects the bot, deletes guild history, or affects other operators. You can
          onboard again later to restore access.
        </p>
      </div>

      <div className="danger-zone">
        <h3 className="section__title danger-zone__title">Danger zone</h3>
        <p className="muted" style={{ marginBottom: 14 }}>
          Revoke your access to {label}. This action takes effect immediately.
        </p>

        {error ? (
          <div style={{ marginBottom: 14 }}>
            <Banner tone="error">{error}</Banner>
          </div>
        ) : null}

        {!confirming ? (
          <button type="button" className="btn btn--danger" onClick={() => setConfirming(true)}>
            Revoke my access
          </button>
        ) : (
          <div className="stack" style={{ gap: 12 }}>
            <Banner tone="error">
              Are you sure? You will lose access to {label} until you onboard it again.
            </Banner>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn btn--danger"
                onClick={handleRevoke}
                disabled={revoking}
              >
                {revoking ? 'Revoking…' : 'Yes, revoke my access'}
              </button>
              <button
                type="button"
                className="btn btn--ghost"
                onClick={() => setConfirming(false)}
                disabled={revoking}
              >
                Cancel
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
