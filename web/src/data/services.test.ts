import { expect, it } from 'vitest';
import { addDays } from '@/domain/dates';
import { failure, unwrap, type Outcome } from '@/domain/errors';
import { bookingCode } from '@/domain/format/dates';
import { DEMO_USER_ID } from '@/domain/models';
import { isOverdue } from '@/domain/rules/lateFees';
import { fitWithin } from '@/lib/photos';
import { parseId } from '@/lib/routes';
import { exclusive, singleFlight } from '@/lib/singleFlight';
import { smsLink } from './overdueReminders';
import { bootstrap, createServices } from './services';
import { memoryStore } from './sessionRepository';
import { seededDb, testDb, time, TODAY } from './testing';

async function services() {
  return createServices({ db: await seededDb(), time, store: memoryStore(), unlockStore: memoryStore() });
}

it('drafts the overdue SMS and records it once a day', async () => {
  const s = await services();
  const late = (await s.bookings.allBookings()).filter(b => isOverdue(b, TODAY));
  expect(late).toHaveLength(1);
  const draft = unwrap(await s.reminders.draft(late[0].id));
  expect(draft.phone).toBe('+91 98450 12009');
  expect(draft.message).toContain('2 days overdue');
  expect(draft.message).toContain(bookingCode(late[0].id));
  expect(smsLink(draft)).toBe(`sms:+919845012009?&body=${encodeURIComponent(draft.message)}`);
  expect(s.reminders.needsReminder(late[0])).toBe(true);
  const reminded = unwrap(await s.reminders.markSent(late[0].id));
  expect(s.reminders.needsReminder(reminded)).toBe(false);
});

it('refuses to draft a reminder for a rental that is not overdue', async () => {
  const s = await services();
  const onTime = (await s.bookings.allBookings()).find(b => b.itemId === 25 && b.status === 'ACTIVE')!;
  expect(await s.reminders.draft(onTime.id)).toEqual(failure({ kind: 'NotOverdue' }));
});

it('submitting twice creates one request', async () => {
  const s = await services();
  const range = { start: addDays(TODAY, 20), end: addDays(TODAY, 22) };
  const submit = singleFlight(() => s.bookings.requestBooking(11, DEMO_USER_ID, range, '+91 98450 12001'));
  await Promise.all([submit(), submit()]);
  await submit();
  const mine = (await s.bookings.bookingsForCustomer(DEMO_USER_ID)).filter(b => b.itemId === 11);
  expect(mine).toHaveLength(1);
});

it('a failed action can be retried', async () => {
  let calls = 0;
  const run = singleFlight(async (): Promise<Outcome<number>> => (++calls === 1 ? failure({ kind: 'NotFound' }) : { ok: true, value: calls }));
  expect((await run()).ok).toBe(false);
  expect(await run()).toEqual({ ok: true, value: 2 });
  expect(await run()).toEqual({ ok: true, value: 2 });
});

it('bootstrap reports blocked storage instead of hanging', async () => {
  const db = testDb();
  db.close();
  const r = await bootstrap(db, time);
  expect(r.ok).toBe(false);
  expect(await bootstrap(testDb(), time)).toEqual({ ok: true });
});

it('fits photos within the max edge', () => {
  expect(fitWithin(4000, 3000)).toEqual({ width: 1600, height: 1200 });
  expect(fitWithin(800, 600)).toEqual({ width: 800, height: 600 });
  expect(fitWithin(1000, 3000)).toEqual({ width: 533, height: 1600 });
});

it('an exclusive action ignores taps while running but runs again once done', async () => {
  let calls = 0;
  const run = exclusive(async (): Promise<Outcome<number>> => ({ ok: true, value: ++calls }));
  await Promise.all([run(), run()]);
  expect(calls).toBe(1);
  expect(await run()).toEqual({ ok: true, value: 2 });
});

it('parses route ids strictly', () => {
  expect(parseId('12')).toBe(12);
  expect(parseId('abc')).toBeNull();
  expect(parseId('12abc')).toBeNull();
  expect(parseId('0')).toBeNull();
  expect(parseId('-3')).toBeNull();
  expect(parseId('1.5')).toBeNull();
  expect(parseId(undefined)).toBeNull();
});
