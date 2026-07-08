import { useState } from 'react';
import { Navigate, NavLink, Outlet, useNavigate } from 'react-router-dom';

import { useAuth } from '../auth/AuthContext';
import { initials } from './format';
import { LoadingState } from './states';

const DISCORD_AVATAR_BASE = 'https://cdn.discordapp.com/avatars';

function OperatorChip() {
  const { operator } = useAuth();
  if (!operator) {
    return null;
  }
  const name = operator.displayName || operator.username || 'Operator';
  const avatarUrl =
    operator.avatarHash && operator.discordUserId
      ? `${DISCORD_AVATAR_BASE}/${operator.discordUserId}/${operator.avatarHash}.png?size=64`
      : null;

  return (
    <div className="operator-chip" title={name}>
      {avatarUrl ? (
        <img className="avatar" src={avatarUrl} alt="" width={30} height={30} />
      ) : (
        <span className="avatar" aria-hidden="true">
          {initials(name)}
        </span>
      )}
      <span className="operator-chip__meta">
        <span className="operator-chip__name">{name}</span>
        {operator.username ? (
          <span className="operator-chip__id">@{operator.username}</span>
        ) : null}
      </span>
    </div>
  );
}

function Sidebar() {
  return (
    <nav className="app-sidebar" aria-label="Primary">
      <NavLink to="/dashboard" end className="nav-link">
        <span className="nav-link__icon" aria-hidden="true">
          ▨
        </span>
        Dashboard
      </NavLink>
      <NavLink to="/dashboard/guilds" className="nav-link">
        <span className="nav-link__icon" aria-hidden="true">
          ⛨
        </span>
        Guilds
      </NavLink>
    </nav>
  );
}

/**
 * Authenticated dashboard layout. Guards every nested route: while the session is being probed it
 * shows a loading state, and if the operator is not signed in it redirects to the landing page.
 */
export default function AppShell() {
  const { status, signOut } = useAuth();
  const navigate = useNavigate();
  const [signingOut, setSigningOut] = useState(false);

  if (status === 'loading') {
    return <LoadingState label="Checking your session…" />;
  }

  // Redirect declaratively instead of calling navigate() during render, which would trigger a
  // state update on the Router mid-render. <Navigate> performs the redirect as a routing effect.
  if (status === 'unauthenticated') {
    return <Navigate to="/" replace />;
  }

  const handleSignOut = async () => {
    setSigningOut(true);
    await signOut();
    navigate('/', { replace: true });
  };

  return (
    <div className="app-shell">
      <header className="app-header">
        <NavLink to="/dashboard" className="brand">
          <span className="brand__mark" aria-hidden="true">
            G
          </span>
          Guild OS
        </NavLink>
        <div className="app-header__right">
          <OperatorChip />
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={handleSignOut}
            disabled={signingOut}
          >
            {signingOut ? 'Signing out…' : 'Log out'}
          </button>
        </div>
      </header>
      <div className="app-body">
        <Sidebar />
        <main className="app-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
