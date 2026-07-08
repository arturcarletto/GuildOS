import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAuth } from '../auth/AuthContext';
import { Banner, LoadingState } from '../components/states';

/** Entry point where sign-in begins. Signed-in operators are redirected to the dashboard. */
const SIGN_IN_URL = '/oauth2/authorization/discord';

export default function LandingPage() {
  const { status, error } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (status === 'authenticated') {
      navigate('/dashboard', { replace: true });
    }
  }, [status, navigate]);

  if (status === 'loading') {
    return <LoadingState label="Loading Guild OS…" />;
  }

  return (
    <div className="landing">
      <header className="app-header">
        <span className="brand">
          <span className="brand__mark" aria-hidden="true">
            G
          </span>
          Guild OS
        </span>
      </header>

      <div className="landing__inner">
        <section className="hero">
          <span className="hero__badge">Operator dashboard · early foundation</span>
          <h1 className="hero__title">
            Manage your <span className="accent">Discord communities</span> with confidence
          </h1>
          <p className="hero__lead">
            Guild OS gives community operators a secure home base to onboard guilds, manage
            per-guild settings, and review privacy-conscious activity analytics — all behind your
            own Discord sign-in.
          </p>

          <div className="hero__points">
            <span className="hero__point">Discord OAuth sign-in</span>
            <span className="hero__point">Guild onboarding</span>
            <span className="hero__point">Timezone &amp; locale settings</span>
            <span className="hero__point">Hourly activity analytics</span>
          </div>

          {error ? <Banner tone="error">{error}</Banner> : null}

          <a className="btn btn--discord" href={SIGN_IN_URL}>
            <DiscordMark />
            Sign in with Discord
          </a>

          <p className="hero__note">
            Sign-in is handled entirely by the Guild OS backend. Your Discord tokens never touch the
            browser — the dashboard only uses a secure session cookie and CSRF-protected requests.
          </p>
        </section>
      </div>

      <footer className="landing__foot">
        Guild OS · Real-time dashboards, moderation, AI, and billing are not implemented yet.
      </footer>
    </div>
  );
}

function DiscordMark() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M20.317 4.369A19.79 19.79 0 0 0 15.885 3c-.211.375-.454.88-.622 1.28a18.27 18.27 0 0 0-5.53 0A12.6 12.6 0 0 0 9.11 3 19.7 19.7 0 0 0 4.677 4.37C1.9 8.48 1.144 12.49 1.522 16.44a19.9 19.9 0 0 0 6.032 3.03c.487-.66.921-1.36 1.296-2.096-.708-.266-1.386-.594-2.026-.98.17-.124.335-.253.494-.386 3.902 1.79 8.13 1.79 11.986 0 .161.14.326.269.494.386-.64.386-1.32.716-2.028.982.375.735.808 1.434 1.295 2.094a19.86 19.86 0 0 0 6.035-3.03c.443-4.58-.757-8.554-3.183-12.07ZM8.02 14.01c-1.183 0-2.157-1.085-2.157-2.42 0-1.334.955-2.42 2.157-2.42 1.21 0 2.176 1.096 2.157 2.42 0 1.335-.955 2.42-2.157 2.42Zm7.975 0c-1.183 0-2.157-1.085-2.157-2.42 0-1.334.955-2.42 2.157-2.42 1.21 0 2.176 1.096 2.157 2.42 0 1.335-.946 2.42-2.157 2.42Z" />
    </svg>
  );
}
