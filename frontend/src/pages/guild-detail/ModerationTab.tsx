import { useCallback, useState, type FormEvent } from 'react';

import { ApiError, api } from '../../api/client';
import type {
  MemberSearchResultMember,
  ModerationCase,
  ModerationCasesResponse,
} from '../../api/types';
import { formatDateTime } from '../../components/format';
import { Banner, EmptyState, ErrorState, LoadingState } from '../../components/states';
import { describeError, useAsync } from '../../hooks/useAsync';

type Feedback = { tone: 'success' | 'error'; message: string } | null;
type SearchState = 'idle' | 'loading' | 'loaded' | 'error';

// A full Discord user id is 17-20 digits. It is the only value that may bypass
// the text-search minimum length; a shorter numeric query is not a valid
// snowflake and is rejected rather than run as a numeric prefix search.
const FULL_SNOWFLAKE = /^[0-9]{17,20}$/;
const DIGITS = /^[0-9]+$/;
const MAX_DURATION_MINUTES = 40_320;
const MAX_REASON_LENGTH = 240;
const MIN_SEARCH_LENGTH = 2;
const MAX_SEARCH_LENGTH = 64;
const SEARCH_LIMIT = 10;
const CASE_LIMIT = 50;

export default function ModerationTab({ guildId }: { guildId: string }) {
  const [targetUserId, setTargetUserId] = useState('');
  const [durationMinutes, setDurationMinutes] = useState('10');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<Feedback>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [searchState, setSearchState] = useState<SearchState>('idle');
  const [searchError, setSearchError] = useState('');
  const [results, setResults] = useState<MemberSearchResultMember[]>([]);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const loadCases = useCallback(
    () => api.getModerationCases(guildId, { limit: CASE_LIMIT }),
    [guildId],
  );
  const caseHistory = useAsync<ModerationCasesResponse>(loadCases, [guildId]);

  const handleSearch = async (event: FormEvent) => {
    event.preventDefault();
    const trimmed = searchQuery.trim();
    const validation = validateSearch(trimmed);
    if (validation) {
      setSearchState('error');
      setSearchError(validation);
      setResults([]);
      return;
    }

    setSearchState('loading');
    setSearchError('');
    try {
      const response = await api.searchGuildMembers(guildId, trimmed, SEARCH_LIMIT);
      setResults(response.results);
      setSearchState('loaded');
    } catch (error) {
      setResults([]);
      setSearchState('error');
      setSearchError(describeModerationError(error));
    }
  };

  const handleSelect = (member: MemberSearchResultMember) => {
    setTargetUserId(member.userId);
    setSelectedUserId(member.userId);
  };

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
      caseHistory.reload();
    } catch (error) {
      setFeedback({ tone: 'error', message: describeModerationError(error) });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="stack" style={{ gap: 16 }}>
      <div className="page-head" style={{ marginBottom: 8 }}>
        <h2 className="section__title">Moderation</h2>
        <p className="section__subtitle">
          Apply a Discord member timeout through the bot and record the successful action in the
          privacy-safe moderation case history and audit log.
        </p>
      </div>

      <div className="card card--pad" style={{ marginBottom: 16 }}>
        <div className="row-between" style={{ marginBottom: 14 }}>
          <h3 className="section__title">Find a member</h3>
          <span className="badge badge--available">Live lookup</span>
        </div>

        <form onSubmit={handleSearch} className="stack" style={{ gap: 12 }}>
          <div className="field">
            <label className="field__label" htmlFor="moderation-search">
              Search by name or user ID
            </label>
            <div className="row" style={{ gap: 8 }}>
              <input
                id="moderation-search"
                className="input"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="Username, nickname, or 123456789012345678"
                autoComplete="off"
                spellCheck={false}
                maxLength={MAX_SEARCH_LENGTH}
              />
              <button
                type="submit"
                className="btn btn--ghost"
                disabled={searchState === 'loading'}
              >
                {searchState === 'loading' ? 'Searching...' : 'Search'}
              </button>
            </div>
            <span className="field__hint">
              Live Discord lookup. Guild OS does not store member names or search terms.
            </span>
          </div>
        </form>

        <div style={{ marginTop: 12 }}>
          {searchState === 'loading' ? (
            <p className="muted" role="status">
              Searching members...
            </p>
          ) : null}
          {searchState === 'error' ? <Banner tone="error">{searchError}</Banner> : null}
          {searchState === 'loaded' && results.length === 0 ? (
            <p className="muted">No members matched that search.</p>
          ) : null}
          {results.length > 0 ? (
            <ul className="stack" style={{ gap: 8, listStyle: 'none', padding: 0, margin: 0 }}>
              {results.map((member) => (
                <li key={member.userId}>
                  <button
                    type="button"
                    className="card card--pad row-between"
                    style={{
                      width: '100%',
                      textAlign: 'left',
                      cursor: 'pointer',
                      borderColor:
                        selectedUserId === member.userId ? 'var(--accent, #5865f2)' : undefined,
                    }}
                    aria-pressed={selectedUserId === member.userId}
                    onClick={() => handleSelect(member)}
                  >
                    <span className="stack" style={{ gap: 2 }}>
                      <span>
                        <strong>{member.displayName ?? member.username ?? member.userId}</strong>{' '}
                        <span className="badge">{member.bot ? 'Bot' : 'Human'}</span>
                      </span>
                      <span className="muted">
                        {member.username ? `@${member.username} · ` : ''}
                        <span className="mono">{member.userId}</span>
                      </span>
                    </span>
                    <span className="btn btn--ghost btn--sm" aria-hidden="true">
                      {selectedUserId === member.userId ? 'Selected' : 'Select'}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
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
              onChange={(event) => {
                setTargetUserId(event.target.value);
                setSelectedUserId(null);
              }}
              placeholder="123456789012345678"
              autoComplete="off"
              spellCheck={false}
              inputMode="numeric"
              required
            />
            <span className="field__hint">
              Fill this from a search result above, or enter a Discord user ID manually.
            </span>
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
              Case history and audit log summaries stay generic and never include the reason.
            </span>
            <button type="submit" className="btn btn--primary" disabled={submitting}>
              {submitting ? 'Applying...' : 'Apply timeout'}
            </button>
          </div>
        </form>
      </div>

      <ModerationCaseHistory
        cases={caseHistory.data?.cases ?? []}
        loading={caseHistory.loading}
        error={caseHistory.error}
        onRetry={caseHistory.reload}
      />
    </div>
  );
}

function ModerationCaseHistory({
  cases,
  loading,
  error,
  onRetry,
}: {
  cases: ModerationCase[];
  loading: boolean;
  error: Error | null;
  onRetry: () => void;
}) {
  return (
    <div className="card card--pad">
      <div className="row-between" style={{ marginBottom: 14 }}>
        <h3 className="section__title">Recent moderation cases</h3>
        <span className="badge badge--role">Read only</span>
      </div>

      {loading ? <LoadingState label="Loading moderation cases..." /> : null}
      {!loading && error ? <ErrorState message={describeError(error)} onRetry={onRetry} /> : null}
      {!loading && !error && cases.length === 0 ? (
        <EmptyState title="No moderation cases yet." />
      ) : null}
      {!loading && !error && cases.length > 0 ? <ModerationCaseTable cases={cases} /> : null}
    </div>
  );
}

function ModerationCaseTable({ cases }: { cases: ModerationCase[] }) {
  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            <th scope="col">Time</th>
            <th scope="col">Action</th>
            <th scope="col">Target user ID</th>
            <th scope="col">Duration</th>
            <th scope="col">Status</th>
            <th scope="col">Summary</th>
          </tr>
        </thead>
        <tbody>
          {cases.map((moderationCase) => (
            <tr key={moderationCase.publicCaseId}>
              <td>{formatDateTime(moderationCase.occurredAt)}</td>
              <td>
                <span className="badge">{labelFromConstant(moderationCase.actionType)}</span>
              </td>
              <td className="mono">{moderationCase.targetUserId}</td>
              <td>{durationLabel(moderationCase.durationMinutes)}</td>
              <td>{labelFromConstant(moderationCase.status)}</td>
              <td>{moderationCase.summary}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function validateSearch(query: string): string | null {
  if (query === '') {
    return 'Enter a name or user ID to search.';
  }
  if (query.length > MAX_SEARCH_LENGTH) {
    return `Search query must be ${MAX_SEARCH_LENGTH} characters or fewer.`;
  }
  if (!FULL_SNOWFLAKE.test(query) && DIGITS.test(query)) {
    return 'Numeric search must be a full Discord user id (17-20 digits).';
  }
  if (!FULL_SNOWFLAKE.test(query) && query.length < MIN_SEARCH_LENGTH) {
    return `Search query must be at least ${MIN_SEARCH_LENGTH} characters.`;
  }
  return null;
}

function validate(targetUserId: string, durationMinutes: string, reason: string): string | null {
  if (!FULL_SNOWFLAKE.test(targetUserId.trim())) {
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

function durationLabel(durationMinutes: number | null): string {
  return durationMinutes === null ? '-' : `${durationMinutes} min`;
}

function labelFromConstant(value: string): string {
  return value
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(' ');
}
