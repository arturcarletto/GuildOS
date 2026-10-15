import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, api } from '../../api/client';
import type { ActivityAnalytics, ActivityBucket } from '../../api/types';
import ActivityTab from './ActivityTab';

vi.mock('../../api/client', async () => ({
  ...await vi.importActual<typeof import('../../api/client')>('../../api/client'),
  api: { getActivityAnalytics: vi.fn() },
}));

const mockedApi = vi.mocked(api);
const FROM = '2026-07-08T10:00:00Z';
const TO = '2026-07-09T10:00:00Z';

function bucket(startedAt: string, messagesCreated = 5): ActivityBucket {
  return {
    startedAt, messagesCreated, humanMessages: messagesCreated, botMessages: 0,
    distinctMessagesEdited: 0, messagesDeleted: 0, membersJoined: 0, membersLeft: 0,
    activeMembers: 0, activeChannels: 0,
  };
}

function response(guildId = 'g1', from = FROM, to = TO, buckets = [bucket(from)]): ActivityAnalytics {
  const total = (field: keyof Omit<ActivityBucket, 'startedAt'>) => buckets.reduce((sum, item) => sum + item[field], 0);
  return {
    guildId, from, to, bucketTimezone: 'UTC', buckets,
    summary: {
      messagesCreated: total('messagesCreated'), humanMessages: total('humanMessages'),
      botMessages: total('botMessages'), distinctMessagesEdited: total('distinctMessagesEdited'),
      messagesDeleted: total('messagesDeleted'), membersJoined: total('membersJoined'),
      membersLeft: total('membersLeft'), peakHourlyActiveMembers: 0, peakHourlyActiveChannels: 0,
    },
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-07-09T10:42:31.456Z'));
  mockedApi.getActivityAnalytics.mockReset();
  mockedApi.getActivityAnalytics.mockImplementation(async (guildId, from, to) => response(guildId, from, to));
});

afterEach(() => vi.useRealTimers());

describe('ActivityTab message chart', () => {
  it('uses complete UTC hours and puts an accessible chart between unchanged cards and table', async () => {
    const user = userEvent.setup();
    render(<ActivityTab guildId="g1" />);
    const image = await screen.findByRole('img', { name: 'Messages created by hour (UTC)' });
    expect(mockedApi.getActivityAnalytics).toHaveBeenCalledTimes(1);
    expect(mockedApi.getActivityAnalytics).toHaveBeenCalledWith('g1', FROM, TO);
    expect(image).toHaveAccessibleDescription(/24 bars.*5 messages recorded/);
    expect(screen.getByRole('button', { name: 'Last 24 hours' })).toHaveAttribute('aria-pressed', 'true');
    const card = screen.getByText('Messages created', { selector: '.stat-card__label' }).parentElement!;
    expect(within(card).getByText('5')).toBeInTheDocument();
    const table = screen.getByRole('table', { name: 'Hourly activity buckets' });
    expect(image.compareDocumentPosition(card) & Node.DOCUMENT_POSITION_PRECEDING).toBeTruthy();
    expect(image.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(within(table).getByRole('columnheader', { name: 'Human' })).toBeInTheDocument();
    expect(screen.getByText(/at-least-once processing with idempotent projection/)).toBeInTheDocument();
    expect(screen.getByText(/not proof of healthy ingestion/)).toBeInTheDocument();
    const plot = screen.getByRole('region', { name: /horizontally scrollable/ });
    expect(plot).toHaveAttribute('tabindex', '0');

    await user.click(screen.getByText('View chart values'));
    const values = screen.getByRole('table', { name: 'Message chart values' });
    expect(within(values).getAllByRole('row')).toHaveLength(25);
    expect(within(values).getByText(/2026-07-08 10:00:00 UTC to 2026-07-08 11:00:00 UTC/)).toBeInTheDocument();
    expect(within(values).getAllByText('0')).toHaveLength(23);
  });

  it('uses the response bounds, not the requested duration, to choose daily bars and partial days', async () => {
    const user = userEvent.setup();
    mockedApi.getActivityAnalytics.mockResolvedValue(response('g1', '2026-07-02T10:00:00Z', TO));
    render(<ActivityTab guildId="g1" />);
    const image = await screen.findByRole('img', { name: 'Messages created by day (UTC)' });
    expect(image).toHaveAccessibleDescription(/8 bars/);
    expect(screen.getByText(/only hours inside the selected range/)).toBeInTheDocument();
    await user.click(screen.getByText('View chart values'));
    expect(screen.getAllByText('Partial UTC day', { exact: true })).toHaveLength(2);
    expect(screen.getAllByText('Complete day', { exact: true })).toHaveLength(6);
  });

  it('renders all 32 UTC dates at the 31-day maximum with finite SVG geometry and exact values', async () => {
    const user = userEvent.setup();
    mockedApi.getActivityAnalytics.mockResolvedValue(response('g1', '2026-07-01T10:00:00Z', '2026-08-01T10:00:00Z'));
    render(<ActivityTab guildId="g1" />);
    const image = await screen.findByRole('img', { name: /Messages created by day/ });
    expect(image).toHaveAccessibleDescription(/32 bars/);
    expect(image.querySelectorAll('rect')).toHaveLength(32);
    expect(image.outerHTML).not.toMatch(/NaN|Infinity/);
    await user.click(screen.getByText('View chart values'));
    expect(within(screen.getByRole('table', { name: 'Message chart values' })).getAllByRole('row')).toHaveLength(33);
  });

  it('preserves loading, controlled error and retry without presenting a zero chart', async () => {
    const user = userEvent.setup();
    const pending = deferred<ActivityAnalytics>();
    mockedApi.getActivityAnalytics.mockReturnValueOnce(pending.promise);
    render(<ActivityTab guildId="g1" />);
    expect(screen.getByRole('status')).toHaveTextContent('Loading analytics');
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    await act(async () => pending.reject(new ApiError(500, {})));
    expect(screen.getByText(/server ran into a problem/)).toBeInTheDocument();
    expect(screen.queryByText(/No activity recorded/)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('img')).toBeInTheDocument();
    expect(mockedApi.getActivityAnalytics).toHaveBeenLastCalledWith('g1', FROM, TO);
  });

  it.each([401, 404])('preserves the access error for HTTP %i and never renders previous analytics', async (status) => {
    mockedApi.getActivityAnalytics.mockRejectedValueOnce(new ApiError(status, {}));
    render(<ActivityTab guildId="g1" />);
    expect(await screen.findByRole('button', { name: 'Try again' })).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByText(status === 401
      ? 'Your session has expired. Please sign in again.'
      : 'That resource is unavailable or you no longer have access to it.')).toBeInTheDocument();
  });

  it('keeps a completely empty API response distinct from a zero chart', async () => {
    mockedApi.getActivityAnalytics.mockResolvedValue(response('g1', FROM, TO, []));
    render(<ActivityTab guildId="g1" />);
    expect(await screen.findByText('No activity recorded')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('preserves the all-hours-empty state and draws a finite all-zero series', async () => {
    mockedApi.getActivityAnalytics.mockResolvedValue(response('g1', FROM, TO, [bucket(FROM, 0)]));
    render(<ActivityTab guildId="g1" />);
    const image = await screen.findByRole('img');
    expect(image.outerHTML).not.toMatch(/NaN|Infinity/);
    expect(screen.getByText('All hours are empty')).toBeInTheDocument();
    expect(screen.getByText('No messages created were recorded in this range.')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Hourly activity buckets' })).not.toBeInTheDocument();
  });

  it('shows other activity and the original table when no messages were created', async () => {
    mockedApi.getActivityAnalytics.mockResolvedValue(response('g1', FROM, TO, [{ ...bucket(FROM, 0), membersJoined: 2 }]));
    render(<ActivityTab guildId="g1" />);
    expect(await screen.findByRole('img')).toBeInTheDocument();
    expect(screen.getByText('No messages created were recorded in this range.')).toBeInTheDocument();
    expect(screen.queryByText('All hours are empty')).not.toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Hourly activity buckets' })).getByText('2')).toBeInTheDocument();
  });

  it('hides already-loaded results immediately when the guild changes', async () => {
    mockedApi.getActivityAnalytics.mockResolvedValueOnce(response('g1', FROM, TO, [bucket(FROM, 73)]));
    const pending = deferred<ActivityAnalytics>();
    mockedApi.getActivityAnalytics.mockReturnValueOnce(pending.promise);
    const { rerender } = render(<ActivityTab guildId="g1" />);
    await screen.findByRole('img');
    expect(screen.getAllByText('73').length).toBeGreaterThan(0);
    rerender(<ActivityTab guildId="g2" />);
    expect(screen.queryByText('73')).not.toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Loading analytics');
    await act(async () => pending.resolve(response('g2', FROM, TO, [bucket(FROM, 11)])));
    expect(await screen.findByRole('img')).toHaveAccessibleDescription(/11 messages recorded/);
  });

  it('ignores a late response from a previous guild', async () => {
    const older = deferred<ActivityAnalytics>();
    mockedApi.getActivityAnalytics.mockReturnValueOnce(older.promise);
    const { rerender } = render(<ActivityTab guildId="g1" />);
    rerender(<ActivityTab guildId="g2" />);
    expect(await screen.findByRole('img')).toHaveAccessibleDescription(/5 messages recorded/);
    await act(async () => older.resolve(response('g1', FROM, TO, [bucket(FROM, 73)])));
    expect(screen.getByRole('img')).toHaveAccessibleDescription(/5 messages recorded/);
    expect(screen.queryByText('73')).not.toBeInTheDocument();
    expect(mockedApi.getActivityAnalytics).toHaveBeenLastCalledWith('g2', FROM, TO);
  });

  it('ignores a late response after rapidly changing period back to 24 hours', async () => {
    const user = userEvent.setup();
    const older = deferred<ActivityAnalytics>();
    mockedApi.getActivityAnalytics
      .mockResolvedValueOnce(response())
      .mockReturnValueOnce(older.promise)
      .mockResolvedValueOnce(response('g1', FROM, TO, [bucket(FROM, 11)]));
    render(<ActivityTab guildId="g1" />);
    await screen.findByRole('img');
    const weekButton = screen.getByRole('button', { name: 'Last 7 days' });
    weekButton.focus();
    await user.keyboard('{Enter}');
    expect(screen.getByRole('status')).toHaveTextContent('Loading analytics');
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Last 24 hours' }));
    expect(await screen.findByRole('img')).toHaveAccessibleDescription(/11 messages recorded/);
    await act(async () => older.resolve(response('g1', '2026-07-02T10:00:00Z', TO, [bucket(FROM, 73)])));
    expect(screen.getByRole('img')).toHaveAccessibleDescription(/11 messages recorded/);
    expect(screen.queryByText('73')).not.toBeInTheDocument();
    expect(mockedApi.getActivityAnalytics.mock.calls[1]).toEqual(['g1', '2026-07-02T10:00:00Z', TO]);
  });

  it('keeps a failed previous-guild response from replacing the current chart', async () => {
    const older = deferred<ActivityAnalytics>();
    mockedApi.getActivityAnalytics.mockReturnValueOnce(older.promise);
    const { rerender } = render(<ActivityTab guildId="g1" />);
    rerender(<ActivityTab guildId="g2" />);
    await screen.findByRole('img');
    await act(async () => older.reject(new ApiError(404, {})));
    expect(screen.getByRole('img')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });

  it('applies a custom interval and rejects an interval over 31 days without fetching', async () => {
    const user = userEvent.setup();
    render(<ActivityTab guildId="g1" />);
    await screen.findByRole('img');
    await user.click(screen.getByRole('button', { name: 'Custom range' }));
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-07-01T10:00' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-07-02T10:00' } });
    await user.click(screen.getByRole('button', { name: 'Load range' }));
    await waitFor(() => expect(mockedApi.getActivityAnalytics).toHaveBeenCalledTimes(2));
    expect(mockedApi.getActivityAnalytics).toHaveBeenLastCalledWith('g1',
      new Date('2026-07-01T10:00').toISOString().replace('.000Z', 'Z'),
      new Date('2026-07-02T10:00').toISOString().replace('.000Z', 'Z'));
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-08-02T10:00' } });
    await user.click(screen.getByRole('button', { name: 'Load range' }));
    expect(screen.getByRole('alert')).toHaveTextContent('cannot exceed 31 days');
    expect(mockedApi.getActivityAnalytics).toHaveBeenCalledTimes(2);
  });

  it('does not mistake malformed response bounds for zero recorded activity', async () => {
    mockedApi.getActivityAnalytics.mockResolvedValue({ ...response(), from: 'invalid' });
    render(<ActivityTab guildId="g1" />);
    expect(await screen.findByText('The message chart is unavailable for this response.')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Hourly activity buckets' })).toBeInTheDocument();
  });
});
