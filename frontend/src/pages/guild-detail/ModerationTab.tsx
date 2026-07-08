import { useState, type FormEvent } from 'react';

import { ApiError, api } from '../../api/client';
import { Banner } from '../../components/states';
import { describeError } from '../../hooks/useAsync';

type Feedback = { tone: 'success' | 'error'; message: string } | null;

const SNOWFLAKE = /^[0-9]{1,20}$/;
const MAX_DURATION_MINUTES = 40_320;
const MAX_REASON_LENGTH = 240;

export default function ModerationTab({ guildId }: { guildId: string }) {
  const [targetUserId, setTargetUserId] = useState('');
  const [durationMinutes, setDurationMinutes] = useState('10');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<Feedback>(null);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const validation = validate(targetUserId, durationMinutes, reason);
    if (validation) {
      setFeedback({ tone: 'error', message: validation });
      return;
    }

    setSubmitting(true);
    setFeedback(null);
    try {
      const trimmedReason = reason.trim();
      const response = await api.createMemberTimeout(guildId, {
        targetUserId: targetUserId.trim(),
        durationMinutes: Number(durationMinutes),
        reason: trimmedReason === '' ? undefined : trimmedReason,
      });
      setFeedback({
        tone: 'success',
        message: `Member timeout created for ${response.targetUserId}.`,
      });
      setReason('');
    } catch (error) {
      setFeedback({ tone: 'error', message: describeModerationError(error) });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <div className="page-head" style={{ marginBottom: 8 }}>
        <h2 className="section__title">Moderation</h2>
        <p className="section__subtitle">
          Apply a Discord member timeout through the bot and record the successful action in the
          audit log.
        </p>
      </div>

      <div className="card card--pad">
        <div className="row-between" style={{ marginBottom: 14 }}>
          <h3 className="section__title">Member timeout</h3>
          <span className="badge badge--available">Discord action</span>
        </div>

        {feedback ? (
          <div style={{ marginBottom: 14 }}>
            <Banner tone={feedback.tone}>{feedback.message}</Banner>
          </div>
        ) : null}

        <form onSubmit={handleSubmit} className="stack" style={{ gap: 14 }}>
          <div className="field">
            <label className="field__label" htmlFor="moderation-target-user">
              Target Discord user ID
            </label>
            <input
              id="moderation-target-user"
              className="input mono"
              value={targetUserId}
              onChange={(event) => setTargetUserId(event.target.value)}
              placeholder="123456789012345678"
              autoComplete="off"
              spellCheck={false}
              inputMode="numeric"
              required
            />
          </div>

          <div className="field">
            <label className="field__label" htmlFor="moderation-duration">
              Duration minutes
            </label>
            <input
              id="moderation-duration"
              className="input"
              type="number"
              min={1}
              max={MAX_DURATION_MINUTES}
              step={1}
              value={durationMinutes}
              onChange={(event) => setDurationMinutes(event.target.value)}
              required
            />
            <span className="field__hint">Allowed range: 1 minute to 28 days.</span>
          </div>

          <div className="field">
            <label className="field__label" htmlFor="moderation-reason">
              Reason
            </label>
            <textarea
              id="moderation-reason"
              className="input"
              rows={3}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={MAX_REASON_LENGTH}
              placeholder="Repeated spam after warning."
            />
            <span className="field__hint">Optional. Stored only in Discord moderation context.</span>
          </div>

          <div className="row-between">
            <span className="field__hint">
              Audit log summaries stay generic and never include the reason.
            </span>
            <button type="submit" className="btn btn--primary" disabled={submitting}>
              {submitting ? 'Applying...' : 'Apply timeout'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function validate(targetUserId: string, durationMinutes: string, reason: string): string | null {
  if (!SNOWFLAKE.test(targetUserId.trim())) {
    return 'Target user id must be a Discord snowflake.';
  }

  const duration = Number(durationMinutes);
  if (!Number.isInteger(duration) || duration < 1 || duration > MAX_DURATION_MINUTES) {
    return 'Duration must be between 1 and 40320 minutes.';
  }

  if (reason.length > 0 && reason.trim() === '') {
    return 'Reason cannot be blank when provided.';
  }
  if (reason.trim().length > MAX_REASON_LENGTH) {
    return 'Reason must be 240 characters or fewer.';
  }
  return null;
}

function describeModerationError(error: unknown): string {
  if (error instanceof ApiError) {
    const body = error.body as { error?: string; message?: string } | null;
    if (body?.message) {
      return body.message;
    }
  }
  return describeError(error);
}
