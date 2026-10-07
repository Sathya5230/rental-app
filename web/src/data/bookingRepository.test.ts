import { beforeEach, expect, it } from 'vitest';
import { addDays, type DateRange } from '@/domain/dates';
import { failure, unwrap } from '@/domain/errors';
import { ADMIN_USER_ID, DEMO_USER_ID, bookingRange, type Audience } from '@/domain/models';
import { freeUnitsFor } from '@/domain/rules/availability';
import { BookingRepository } from './bookingRepository';
import { CatalogRepository } from './catalogRepository';
import type { RentNestDb } from './db';
import { InventoryRepository } from './inventoryRepository';
import { NotificationRepository } from './notificationRepository';
import { seededDb, time, TODAY } from './testing';

const PHONE = '9845077777';
let db: RentNestDb;
let bookings: BookingRepository;
let inventory: InventoryRepository;
let catalog: CatalogRepository;
let notifications: NotificationRepository;

beforeEach(async () => {
  db = await seededDb();
  bookings = new BookingRepository(db, time);
  inventory = new InventoryRepository(db, time);
  catalog = new CatalogRepository(db);
  notifications = new NotificationRepository(db);
});

const range = (a: number, b: number): DateRange => ({ start: addDays(TODAY, a), end: addDays(TODAY, b) });
const inbox = (userId: number, audience: Audience) => notifications.notifications(userId, audience);

async function approved(itemId: number, a: number, b: number) {
  const req = unwrap(await bookings.requestBooking(itemId, DEMO_USER_ID, range(a, b), PHONE));
  const [unit] = freeUnitsFor(bookingRange(req), await inventory.unitsForItem(itemId), await bookings.bookingsForItem(itemId));
  unwrap(await bookings.accept(req.id, unit.id));
  return { req, unit };
}

it('a request creates a booking and notifies the admin', async () => {
  const b = unwrap(await bookings.requestBooking(11, DEMO_USER_ID, range(10, 12), PHONE));
  expect(b).toMatchObject({ status: 'REQUESTED', subtotal: 3 * 40_000, contactPhone: '+91 98450 77777', unitId: null });
  expect((await inbox(ADMIN_USER_ID, 'ADMIN')).some(n => n.bookingId === b.id && n.title === 'New rental request')).toBe(true);
});

it('rejects an invalid phone, a past start and the admin renting from their own store', async () => {
  expect(await bookings.requestBooking(11, DEMO_USER_ID, range(20, 21), '12345')).toEqual(failure({ kind: 'InvalidPhone' }));
  expect(await bookings.requestBooking(11, DEMO_USER_ID, range(-1, 2), PHONE)).toEqual(failure({ kind: 'InvalidDateRange' }));
  expect(await bookings.requestBooking(11, ADMIN_USER_ID, range(20, 21), PHONE)).toEqual(failure({ kind: 'OwnListing' }));
});

it('allows a booking that starts today', async () => {
  expect((await bookings.requestBooking(11, DEMO_USER_ID, range(0, 1), PHONE)).ok).toBe(true);
});

it('rejects a range spanning a fully booked day', async () => {
  // Item 4 has a single unit, ACTIVE from -2..2
  expect(await bookings.requestBooking(4, 8, range(1, 5), PHONE)).toEqual(failure({ kind: 'DatesUnavailable' }));
  expect((await bookings.requestBooking(4, 8, range(3, 5), PHONE)).ok).toBe(true);
});

it('runs the full lifecycle with a damaged return', async () => {
  const { req, unit } = await approved(11, 1, 3);
  expect(unwrap(await bookings.checkOut(req.id, { checklist: ['ID verified'], notes: '' })).status).toBe('ACTIVE');
  const returned = unwrap(await bookings.processReturn(req.id, { checklist: [], conditionAfter: 'DAMAGED', notes: 'Torn flap', damageFee: 50_000 }));
  expect(returned).toMatchObject({ status: 'RETURNED', damageFee: 50_000 });
  expect((await inventory.unitsForItem(11)).find(u => u.id === unit.id)).toMatchObject({ status: 'MAINTENANCE', condition: 'DAMAGED' });
  expect(await bookings.handovers(req.id)).toHaveLength(2);
  expect((await bookings.submitReview(req.id, 4, 'Nice')).ok).toBe(true);
  expect(await bookings.submitReview(req.id, 4, 'Again')).toEqual(failure({ kind: 'NotReviewable' }));
});

it('charges transport and cleaning fees at pickup and return', async () => {
  const { req } = await approved(11, 1, 3);
  expect(unwrap(await bookings.checkOut(req.id, { checklist: ['ID verified'], notes: '', transportFee: 15_000 })).pickupTransportFee).toBe(15_000);
  const returned = unwrap(await bookings.processReturn(req.id, { checklist: [], conditionAfter: 'GOOD', notes: '', damageFee: 0, transportFee: 12_000, cleaningFee: 5_000 }));
  expect(returned).toMatchObject({ dropTransportFee: 12_000, cleaningFee: 5_000 });
  const closed = (await inbox(DEMO_USER_ID, 'CUSTOMER')).find(n => n.title === 'Rental closed')!;
  expect(closed.body).toContain('transport charge');
  expect(closed.body).toContain('cleaning charge');
});

it('rejects busy units and invalid transitions', async () => {
  const req = unwrap(await bookings.requestBooking(11, DEMO_USER_ID, range(1, 1), PHONE));
  expect((await bookings.checkOut(req.id, { checklist: [], notes: '' })).ok).toBe(false);
  const [unit13] = await inventory.unitsForItem(13); // reserved by the seeded ACCEPTED booking
  const other = unwrap(await bookings.requestBooking(13, 8, range(1, 1), PHONE));
  expect(await bookings.accept(other.id, unit13.id)).toEqual(failure({ kind: 'NoUnitFree' }));
});

it('cancels only before pickup and tells the admin', async () => {
  const req = unwrap(await bookings.requestBooking(11, DEMO_USER_ID, range(5, 6), PHONE));
  expect(unwrap(await bookings.cancel(req.id)).status).toBe('CANCELLED');
  expect((await inbox(ADMIN_USER_ID, 'ADMIN')).some(n => n.title === 'Booking cancelled' && n.bookingId === req.id)).toBe(true);
  expect(await bookings.cancel(req.id)).toEqual(failure({ kind: 'InvalidTransition', from: 'CANCELLED', action: 'CANCEL' }));
});

it('charges the daily rate per late day', async () => {
  const overdue = (await bookings.allBookings()).find(b => b.itemId === 33 && b.status === 'ACTIVE')!;
  const rate = (await catalog.item(33))!.dailyRate;
  const closed = unwrap(await bookings.processReturn(overdue.id, { checklist: [], conditionAfter: 'GOOD', notes: '', damageFee: 0 }));
  expect(closed).toMatchObject({ status: 'RETURNED', lateFee: 2 * rate });
  expect((await inbox(overdue.customerId, 'CUSTOMER')).some(n => n.title === 'Rental closed' && n.body.includes('late fee'))).toBe(true);
});

it('an on-time return has no late fee', async () => {
  const onTime = (await bookings.allBookings()).find(b => b.itemId === 25 && b.status === 'ACTIVE')!;
  expect(unwrap(await bookings.processReturn(onTime.id, { checklist: [], conditionAfter: 'GOOD', notes: '', damageFee: 0 })).lateFee).toBe(0);
});

it('records overdue reminders only for overdue rentals', async () => {
  const all = await bookings.allBookings();
  const overdue = all.find(b => b.itemId === 33 && b.status === 'ACTIVE')!;
  expect(unwrap(await bookings.recordOverdueReminder(overdue.id)).overdueSmsAt).toBe(time.nowMillis());
  expect((await inbox(overdue.customerId, 'CUSTOMER')).some(n => n.title === 'Return overdue')).toBe(true);
  const notLate = all.find(b => b.itemId === 25 && b.status === 'ACTIVE')!;
  expect(await bookings.recordOverdueReminder(notLate.id)).toEqual(failure({ kind: 'NotOverdue' }));
});
