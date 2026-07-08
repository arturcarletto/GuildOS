import { useCallback, useEffect, useState, type FormEvent } from 'react';

import { ApiError, api } from '../../api/client';
import type {
  MemberMessageConfig,
  MemberMessageKind,
  MemberMessagePreview,
  UpdateMemberMessageRequest,
} from '../../api/types';
import { Banner, ErrorState, LoadingState } from '../../components/states';
import { describeError, useAsync } from '../../hooks/useAsync';

/** Two automation cards — welcome and goodbye — that manage member-message configuration. */
export default function AutomationTab({ guildId }: { guildId: string }) {
  return (
    <div>
      <div className="page-head" style={{ marginBottom: 8 }}>
        <h2 className="section__title">Member message automation</h2>
        <p className="section__subtitle">
          Configure the welcome and goodbye messages GuildOS sends in this server. The same rules and
          delivery apply as the <span className="mono">/welcome</span> and{' '}
          <span className="mono">/goodbye</span> slash commands.
        </p>
      </div>

      <div className="stack" style={{ gap: 20 }}>
        <MemberMessageCard guildId={guildId} kind="welcome" heading="Welcome message" />
        <MemberMessageCard guildId={guildId} kind="goodbye" heading="Goodbye message" />
      </div>
    </div>
  );
}

interface FormState {
  channelId: string;
  title: string;
  message: string;
  color: string;
  imageUrl: string;
  footer: string;
  includeBots: boolean;
  mentionMember: boolean;
  buttonLabel: string;
  buttonUrl: string;
}

const EMPTY_FORM: FormState = {
  channelId: '',
  title: '',
  message: '',
  color: '',
  imageUrl: '',
  footer: '',
  includeBots: false,
  mentionMember: true,
  buttonLabel: '',
  buttonUrl: '',
};

function fromConfig(config: MemberMessageConfig): FormState {
  if (!config.configured) {
    return { ...EMPTY_FORM };
  }
  return {
    channelId: config.channelId,
    title: config.title,
    message: config.message,
    color: config.color,
    imageUrl: config.imageUrl,
    footer: config.footer,
    includeBots: config.includeBots,
    mentionMember: config.mentionMember ?? true,
    buttonLabel: config.buttonLabel,
    buttonUrl: config.buttonUrl,
  };
}

function toRequest(kind: MemberMessageKind, form: FormState): UpdateMemberMessageRequest {
  const base: UpdateMemberMessageRequest = {
    channelId: form.channelId.trim(),
    message: form.message,
    title: blankToUndefined(form.title),
    color: blankToUndefined(form.color),
    imageUrl: blankToUndefined(form.imageUrl),
    footer: blankToUndefined(form.footer),
    includeBots: form.includeBots,
  };
  if (kind === 'welcome') {
    base.mentionMember = form.mentionMember;
    base.buttonLabel = blankToUndefined(form.buttonLabel);
    base.buttonUrl = blankToUndefined(form.buttonUrl);
  }
  return base;
}

function blankToUndefined(value: string): string | undefined {
  return value.trim() === '' ? undefined : value;
}

/** Prefers the backend's safe validation message; otherwise falls back to the generic mapping. */
function describeMemberMessageError(error: unknown): string {
  if (error instanceof ApiError) {
    const body = error.body as { error?: string; message?: string } | null;
    if (error.isConflict && body?.error === 'not_configured') {
      return 'Save this message before enabling or disabling it.';
    }
    if (error.status === 400 && body?.message) {
      return body.message;
    }
  }
  return describeError(error);
}

type Feedback = { tone: 'success' | 'info' | 'error'; message: string } | null;

function MemberMessageCard({
  guildId,
  kind,
  heading,
}: {
  guildId: string;
  kind: MemberMessageKind;
  heading: string;
}) {
  const isWelcome = kind === 'welcome';
  const load = useCallback(() => api.getMemberMessageConfig(guildId, kind), [guildId, kind]);
  const config = useAsync<MemberMessageConfig>(load, [guildId, kind]);

  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [feedback, setFeedback] = useState<Feedback>(null);
  const [preview, setPreview] = useState<MemberMessagePreview | null>(null);
  const [busy, setBusy] = useState<null | 'save' | 'toggle' | 'preview'>(null);

  useEffect(() => {
    if (config.data) {
      setForm(fromConfig(config.data));
      setPreview(null);
    }
  }, [config.data]);

  const update = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((current) => ({ ...current, [key]: value }));

  const handleSave = async (event: FormEvent) => {
    event.preventDefault();
    setBusy('save');
    setFeedback(null);
    try {
      const saved = await api.updateMemberMessageConfig(guildId, kind, toRequest(kind, form));
      config.setData(saved);
      setForm(fromConfig(saved));
      setFeedback({ tone: 'success', message: 'Saved.' });
    } catch (error) {
      setFeedback({ tone: 'error', message: describeMemberMessageError(error) });
    } finally {
      setBusy(null);
    }
  };

  const handleToggle = async () => {
    setBusy('toggle');
    setFeedback(null);
    try {
      const toggled = await api.toggleMemberMessageConfig(guildId, kind);
      config.setData(toggled);
      setForm(fromConfig(toggled));
      setFeedback({
        tone: 'success',
        message: toggled.enabled ? 'Enabled.' : 'Disabled.',
      });
    } catch (error) {
      setFeedback({ tone: 'error', message: describeMemberMessageError(error) });
    } finally {
      setBusy(null);
    }
  };

  const handlePreview = async () => {
    setBusy('preview');
    setFeedback(null);
    try {
      const rendered = await api.previewMemberMessageConfig(guildId, kind, toRequest(kind, form));
      setPreview(rendered);
    } catch (error) {
      setPreview(null);
      setFeedback({ tone: 'error', message: describeMemberMessageError(error) });
    } finally {
      setBusy(null);
    }
  };

  if (config.loading && !config.data) {
    return (
      <div className="card">
        <LoadingState label={`Loading ${kind} message…`} />
      </div>
    );
  }

  if (config.error && !config.data) {
    return (
      <div className="card">
        <ErrorState message={describeError(config.error)} onRetry={config.reload} />
      </div>
    );
  }

  const current = config.data;
  const configured = current?.configured ?? false;
  const enabled = current?.enabled ?? false;
  const anyBusy = busy !== null;

  return (
    <div className="card card--pad">
      <div className="row-between" style={{ marginBottom: 12 }}>
        <h3 className="section__title">{heading}</h3>
        {configured ? (
          <span className={`badge ${enabled ? 'badge--onboarded' : 'badge--revoked'}`}>
            {enabled ? 'Enabled' : 'Disabled'}
          </span>
        ) : (
          <span className="badge badge--available">Not configured</span>
        )}
      </div>

      {!configured ? (
        <div style={{ marginBottom: 14 }}>
          <Banner tone="info">
            This {kind} message isn’t set up yet. Fill in a channel and message, then Save to create
            it.
          </Banner>
        </div>
      ) : null}

      {feedback ? (
        <div style={{ marginBottom: 14 }}>
          <Banner tone={feedback.tone}>{feedback.message}</Banner>
        </div>
      ) : null}

      <form onSubmit={handleSave} className="stack" style={{ gap: 14 }}>
        <div className="field">
          <label className="field__label" htmlFor={`${kind}-channel`}>
            Channel ID
          </label>
          <input
            id={`${kind}-channel`}
            className="input mono"
            value={form.channelId}
            onChange={(event) => update('channelId', event.target.value)}
            placeholder="123456789012345678"
            autoComplete="off"
            spellCheck={false}
            required
          />
          <span className="field__hint">
            The Discord channel id where the message is posted. In Discord, enable Developer Mode and
            use “Copy Channel ID”.
          </span>
        </div>

        <div className="field">
          <label className="field__label" htmlFor={`${kind}-title`}>
            Title
          </label>
          <input
            id={`${kind}-title`}
            className="input"
            value={form.title}
            onChange={(event) => update('title', event.target.value)}
            placeholder={isWelcome ? 'Welcome to {server}!' : 'A member has left'}
            autoComplete="off"
          />
        </div>

        <div className="field">
          <label className="field__label" htmlFor={`${kind}-message`}>
            Message
          </label>
          <textarea
            id={`${kind}-message`}
            className="input"
            rows={3}
            value={form.message}
            onChange={(event) => update('message', event.target.value)}
            placeholder={
              isWelcome
                ? 'Hey {member}, welcome to {server}! You are member #{memberCount}.'
                : '{member} has left {server}.'
            }
            required
          />
          <span className="field__hint">
            Placeholders: {'{member}'}, {'{username}'}, {'{server}'}, {'{memberCount}'}
            {isWelcome ? ', {mention}' : ''}. Mass mentions like @everyone are not allowed.
          </span>
        </div>

        <div className="row-between" style={{ gap: 14, alignItems: 'flex-start' }}>
          <div className="field" style={{ flex: '1 1 160px' }}>
            <label className="field__label" htmlFor={`${kind}-color`}>
              Accent color
            </label>
            <input
              id={`${kind}-color`}
              className="input mono"
              value={form.color}
              onChange={(event) => update('color', event.target.value)}
              placeholder="#57F287"
              autoComplete="off"
              spellCheck={false}
            />
          </div>
          <div className="field" style={{ flex: '2 1 260px' }}>
            <label className="field__label" htmlFor={`${kind}-footer`}>
              Footer
            </label>
            <input
              id={`${kind}-footer`}
              className="input"
              value={form.footer}
              onChange={(event) => update('footer', event.target.value)}
              placeholder="Welcome • {server}"
              autoComplete="off"
            />
          </div>
        </div>

        <div className="field">
          <label className="field__label" htmlFor={`${kind}-image`}>
            Image URL
          </label>
          <input
            id={`${kind}-image`}
            className="input"
            value={form.imageUrl}
            onChange={(event) => update('imageUrl', event.target.value)}
            placeholder="https://example.com/banner.png"
            autoComplete="off"
            spellCheck={false}
          />
          <span className="field__hint">Must be an HTTPS URL.</span>
        </div>

        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={form.includeBots}
            onChange={(event) => update('includeBots', event.target.checked)}
          />
          Also announce bot accounts
        </label>

        {isWelcome ? (
          <>
            <label className="checkbox-row">
              <input
                type="checkbox"
                checked={form.mentionMember}
                onChange={(event) => update('mentionMember', event.target.checked)}
              />
              Mention (ping) the new member
            </label>

            <div className="row-between" style={{ gap: 14, alignItems: 'flex-start' }}>
              <div className="field" style={{ flex: '1 1 200px' }}>
                <label className="field__label" htmlFor={`${kind}-button-label`}>
                  Button label
                </label>
                <input
                  id={`${kind}-button-label`}
                  className="input"
                  value={form.buttonLabel}
                  onChange={(event) => update('buttonLabel', event.target.value)}
                  placeholder="Read the rules"
                  autoComplete="off"
                />
              </div>
              <div className="field" style={{ flex: '2 1 260px' }}>
                <label className="field__label" htmlFor={`${kind}-button-url`}>
                  Button URL
                </label>
                <input
                  id={`${kind}-button-url`}
                  className="input"
                  value={form.buttonUrl}
                  onChange={(event) => update('buttonUrl', event.target.value)}
                  placeholder="https://example.com/rules"
                  autoComplete="off"
                  spellCheck={false}
                />
              </div>
            </div>
            <span className="field__hint">A button needs both a label and an HTTPS URL.</span>
          </>
        ) : null}

        <div className="row-between" style={{ gap: 10, marginTop: 4 }}>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={handlePreview}
              disabled={anyBusy}
            >
              {busy === 'preview' ? 'Rendering…' : 'Preview'}
            </button>
            {configured ? (
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={handleToggle}
                disabled={anyBusy}
              >
                {busy === 'toggle' ? 'Updating…' : enabled ? 'Disable' : 'Enable'}
              </button>
            ) : null}
          </div>
          <button type="submit" className="btn btn--primary" disabled={anyBusy}>
            {busy === 'save' ? 'Saving…' : configured ? 'Save changes' : 'Create message'}
          </button>
        </div>
        <span className="field__hint">
          Preview uses sample values and never sends a message to Discord.
        </span>
      </form>

      {preview ? <PreviewCard kind={kind} preview={preview} /> : null}
    </div>
  );
}

function PreviewCard({
  kind,
  preview,
}: {
  kind: MemberMessageKind;
  preview: MemberMessagePreview;
}) {
  const accent = /^#[0-9a-fA-F]{6}$/.test(preview.color) ? preview.color : '#5865f2';
  return (
    <div style={{ marginTop: 18 }}>
      <div className="row-between" style={{ marginBottom: 8 }}>
        <span className="stat-card__label">Sample preview</span>
        <span className="dim" style={{ fontSize: '0.76rem' }}>
          No message is sent to Discord
        </span>
      </div>
      <div
        className="card card--pad"
        style={{ borderLeft: `4px solid ${accent}` }}
        role="group"
        aria-label={`${kind} message preview`}
      >
        {preview.mentionMember ? (
          <p className="dim" style={{ marginBottom: 6 }}>
            🔔 Would mention the new member
          </p>
        ) : null}
        {preview.title ? (
          <p style={{ fontWeight: 700, marginBottom: 6 }}>{preview.title}</p>
        ) : null}
        <p style={{ whiteSpace: 'pre-wrap' }}>{preview.description}</p>
        {preview.imageUrl ? (
          <img
            src={preview.imageUrl}
            alt=""
            style={{ maxWidth: '100%', borderRadius: 8, marginTop: 10 }}
          />
        ) : null}
        {preview.footer ? (
          <p className="dim" style={{ marginTop: 10, fontSize: '0.8rem' }}>
            {preview.footer}
          </p>
        ) : null}
        {preview.buttonLabel && preview.buttonUrl ? (
          <div style={{ marginTop: 12 }}>
            <span className="btn btn--sm" aria-disabled="true">
              {preview.buttonLabel}
            </span>
          </div>
        ) : null}
      </div>
    </div>
  );
}
