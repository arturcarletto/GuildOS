import { useCallback, useEffect, useState, type FormEvent } from 'react';

import { ApiError, api } from '../../api/client';
import type { GuildSettings } from '../../api/types';
import { formatDateTime } from '../../components/format';
import { Banner, ErrorState, LoadingState } from '../../components/states';
import { describeError, useAsync } from '../../hooks/useAsync';

// Suggested defaults surfaced in the UI. They are never forced — the operator must save to apply.
const SUGGESTED_TIMEZONE = 'America/Sao_Paulo';
const SUGGESTED_LOCALE = 'pt-BR';

type SaveFeedback =
  | { tone: 'success'; message: string }
  | { tone: 'info'; message: string }
  | { tone: 'error'; message: string };

export default function SettingsTab({ guildId }: { guildId: string }) {
  const load = useCallback(() => api.getGuildSettings(guildId), [guildId]);
  const settings = useAsync<GuildSettings>(load, [guildId]);

  const [timezone, setTimezone] = useState('');
  const [locale, setLocale] = useState('');
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<SaveFeedback | null>(null);

  // Keep the form in sync with the loaded/reloaded settings resource.
  useEffect(() => {
    if (settings.data) {
      setTimezone(settings.data.timezone);
      setLocale(settings.data.locale);
    }
  }, [settings.data]);

  if (settings.loading && !settings.data) {
    return (
      <div className="card">
        <LoadingState label="Loading settings…" />
      </div>
    );
  }

  if (settings.error && !settings.data) {
    return (
      <div className="card">
        <ErrorState message={describeError(settings.error)} onRetry={settings.reload} />
      </div>
    );
  }

  const current = settings.data;
  if (!current) {
    return null;
  }

  const dirty = timezone !== current.timezone || locale !== current.locale;

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setFeedback(null);
    try {
      const updated = await api.updateGuildSettings(guildId, {
        timezone: timezone.trim(),
        locale: locale.trim(),
        expectedVersion: current.version,
      });
      settings.setData(updated);
      setFeedback({ tone: 'success', message: 'Settings saved.' });
    } catch (error) {
      if (error instanceof ApiError && error.isConflict) {
        // Someone else changed the settings. Reload the latest and let the operator retry.
        setFeedback({
          tone: 'info',
          message: 'Settings changed elsewhere. Reloading latest settings.',
        });
        settings.reload();
      } else if (error instanceof ApiError && error.status === 400) {
        setFeedback({
          tone: 'error',
          message: 'Timezone must be a valid IANA zone id and locale a valid BCP 47 tag.',
        });
      } else {
        setFeedback({ tone: 'error', message: describeError(error) });
      }
    } finally {
      setSaving(false);
    }
  };

  const applySuggested = () => {
    setTimezone(SUGGESTED_TIMEZONE);
    setLocale(SUGGESTED_LOCALE);
  };

  return (
    <div className="card card--pad">
      <div className="row-between" style={{ marginBottom: 16 }}>
        <h2 className="section__title">Settings</h2>
        <span className="dim" style={{ fontSize: '0.82rem' }}>
          Version {current.version} · Updated {formatDateTime(current.updatedAt)}
        </span>
      </div>

      {feedback ? <Banner tone={feedback.tone}>{feedback.message}</Banner> : null}

      <form onSubmit={handleSubmit} className="stack" style={{ gap: 18, marginTop: 4 }}>
        <div className="field">
          <label className="field__label" htmlFor="timezone">
            Timezone
          </label>
          <input
            id="timezone"
            className="input"
            value={timezone}
            onChange={(event) => setTimezone(event.target.value)}
            placeholder={SUGGESTED_TIMEZONE}
            autoComplete="off"
            spellCheck={false}
            required
          />
          <span className="field__hint">A valid IANA zone id, e.g. UTC or America/Sao_Paulo.</span>
        </div>

        <div className="field">
          <label className="field__label" htmlFor="locale">
            Locale
          </label>
          <input
            id="locale"
            className="input"
            value={locale}
            onChange={(event) => setLocale(event.target.value)}
            placeholder={SUGGESTED_LOCALE}
            autoComplete="off"
            spellCheck={false}
            required
          />
          <span className="field__hint">A well-formed BCP 47 language tag, e.g. en-US or pt-BR.</span>
        </div>

        <div className="row-between">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={applySuggested}
            disabled={saving}
          >
            Use suggested ({SUGGESTED_TIMEZONE} · {SUGGESTED_LOCALE})
          </button>
          <button type="submit" className="btn btn--primary" disabled={saving || !dirty}>
            {saving ? 'Saving…' : 'Save settings'}
          </button>
        </div>
      </form>
    </div>
  );
}
