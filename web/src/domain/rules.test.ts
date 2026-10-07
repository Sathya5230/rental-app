import { describe, expect, it } from 'vitest';
import { addDays, type DateRange } from './dates';
import { failure, unwrap } from './errors';
import { BOOKING_ACTIONS, BOOKING_STATUSES, type Booking, type BookingStatus, type Item, type ItemUnit, type UnitCondition, type UnitStatus } from './models';
import { freeCountOn, freeUnitsFor, isBookable, unavailableDates } from './rules/availability';
import { canReview, nextStatus } from './rules/bookingStateMachine';
import { inventoryTotals, itemStock } from './rules/inventoryMetrics';
import { draftToItem, emptyDraft, validateItem } from './rules/itemValidator';
import { daysLate, isOverdue, lateFee, settle } from './rules/lateFees';
import { overdueSms } from './rules/overdueMessage';
import { quote } from './rules/pricing';
import { stockAlertsForItem } from './rules/stockAlerts';

const D0 = '2026-10-07';
const d = (n: number) => addDays(D0, n);
const r = (a: number, b: number): DateRange => ({ start: d(a), end: d(b) });
const unit = (id: number, status: UnitStatus = 'AVAILABLE', condition: UnitCondition = 'GOOD', itemId = 1): ItemUnit =>
  ({ id, itemId, tag: `U${id}`, condition, status });
const booking = (id: number, unitId: number | null, from: number, to: number, status: BookingStatus, extra: Partial<Booking> = {}): Booking => ({
  id, itemId: 1, unitId, customerId: 9, startDate: d(from), endDate: d(to), status, subtotal: 0, deposit: 0, damageFee: 0,
  createdAt: 0, reviewed: false, contactPhone: '', lateFee: 0, overdueSmsAt: null, pickupTransportFee: 0, dropTransportFee: 0, cleaningFee: 0,
  ...extra,
});
const item = (extra: Partial<Item> = {}): Item => ({
  id: 1, providerId: 1, categoryId: 1, title: 'Tent', description: '', photos: [], dailyRate: 100, weeklyRate: 500, deposit: 0, specs: [],
  lowStockThreshold: 1, isActive: true, unitValue: 0, ownership: 'OWNED', vendorId: null, vendorCostPerDay: 0, vendorReturnBy: null,
  ...extra,
});

describe('pricing', () => {
  const q = (days: number, weekly = 300_000) => unwrap(quote(50_000, weekly, 200_000, r(0, days - 1)));
  it('daily', () => {
    expect(q(1).subtotal).toBe(50_000);
    expect(q(1).totalDueNow).toBe(250_000);
    expect(q(3).subtotal).toBe(150_000);
  });
  it('weekly', () => {
    expect(q(7).subtotal).toBe(300_000);
    expect(q(7).weeks).toBe(1);
    expect(q(13).subtotal).toBe(600_000);
  });
  it('rounds a partial week up when cheaper', () => {
    expect(q(6, 250_000).subtotal).toBe(250_000);
    expect(q(6, 250_000).bestPriceApplied).toBe(true);
    expect(q(10, 250_000).subtotal).toBe(400_000);
  });
  it('zero weekly rate falls back to seven times daily', () => {
    expect(q(7, 0).subtotal).toBe(350_000);
  });
  it('invalid range', () => {
    expect(quote(50_000, 300_000, 0, r(3, 1))).toEqual(failure({ kind: 'InvalidDateRange' }));
  });
});

describe('availability', () => {
  const units = [unit(1), unit(2), unit(3, 'MAINTENANCE')];
  it('counts only reserving bookings', () => {
    expect(freeCountOn(d(1), units, [])).toBe(2);
    const bs = [booking(1, 1, 0, 2, 'ACCEPTED'), booking(2, null, 0, 2, 'REQUESTED'), booking(3, 2, 0, 2, 'CANCELLED')];
    expect(freeCountOn(d(1), units, bs)).toBe(1);
    expect(freeCountOn(d(3), units, bs)).toBe(2);
  });
  it('a fully booked day blocks the range', () => {
    const bs = [booking(1, 1, 2, 2, 'ACCEPTED'), booking(2, 2, 1, 3, 'ACTIVE')];
    expect(isBookable(r(0, 4), units, bs)).toBe(false);
    expect(isBookable(r(4, 6), units, bs)).toBe(true);
    expect(unavailableDates(r(0, 6), units, bs)).toEqual(new Set([d(2)]));
  });
  it('free units exclude busy and maintenance', () => {
    expect(freeUnitsFor(r(1, 1), units, [booking(1, 1, 0, 2, 'ACCEPTED')]).map(u => u.id)).toEqual([2]);
  });
  it('a booking on a serviced unit still consumes a slot', () => {
    expect(freeCountOn(d(1), units, [booking(1, 3, 0, 2, 'ACCEPTED')])).toBe(1);
  });
  it('no usable units means nothing is bookable', () => {
    expect(isBookable(r(0, 0), [unit(1, 'MAINTENANCE')], [])).toBe(false);
    expect(isBookable(r(0, 0), [], [])).toBe(false);
  });
});

describe('booking state machine', () => {
  it('valid transitions', () => {
    expect(nextStatus('REQUESTED', 'ACCEPT')).toEqual({ ok: true, value: 'ACCEPTED' });
    expect(nextStatus('ACCEPTED', 'CHECK_OUT')).toEqual({ ok: true, value: 'ACTIVE' });
    expect(nextStatus('ACTIVE', 'RETURN')).toEqual({ ok: true, value: 'RETURNED' });
  });
  it('every other transition fails', () => {
    const valid = new Set(['REQUESTED:ACCEPT', 'REQUESTED:DECLINE', 'REQUESTED:CANCEL', 'ACCEPTED:CANCEL', 'ACCEPTED:CHECK_OUT', 'ACTIVE:RETURN']);
    for (const s of BOOKING_STATUSES) for (const a of BOOKING_ACTIONS) {
      if (valid.has(`${s}:${a}`)) continue;
      expect(nextStatus(s, a), `${s}/${a}`).toEqual(failure({ kind: 'InvalidTransition', from: s, action: a }));
    }
  });
  it('review only once after return', () => {
    const b = booking(1, 1, 0, 1, 'RETURNED');
    expect(canReview(b)).toBe(true);
    expect(canReview({ ...b, reviewed: true })).toBe(false);
    expect(canReview({ ...b, status: 'ACTIVE' })).toBe(false);
  });
});

describe('stock alerts', () => {
  it('low stock within the 7-day window', () => {
    const units = [unit(1), unit(2), unit(3)];
    expect(stockAlertsForItem(item(), units, [], D0)).toEqual([]);
    const bs = [booking(1, 1, 2, 2, 'ACCEPTED'), booking(2, 2, 2, 3, 'ACCEPTED')];
    expect(stockAlertsForItem(item(), units, bs, D0)).toEqual([{ kind: 'LowStock', itemId: 1, itemTitle: 'Tent', minFree: 1, date: d(2) }]);
    const later = [booking(1, 1, 8, 8, 'ACCEPTED'), booking(2, 2, 8, 9, 'ACCEPTED')];
    expect(stockAlertsForItem(item(), units, later, D0)).toEqual([]);
  });
  it('maintenance, and nothing for inactive items', () => {
    const units = [unit(1), unit(2), unit(3, 'MAINTENANCE', 'DAMAGED'), unit(4, 'RETIRED', 'DAMAGED')];
    expect(stockAlertsForItem(item(), units, [], D0)).toEqual([{ kind: 'Maintenance', itemId: 1, itemTitle: 'Tent', unitTags: ['U3'] }]);
    expect(stockAlertsForItem(item({ isActive: false }), units, [], D0)).toEqual([]);
  });
});

describe('item validator', () => {
  const ok = emptyDraft(1, { title: 'Drill', categoryId: 1, photos: ['data:image/jpeg;base64,x'], dailyRate: 100, weeklyRate: 700 });
  it('requires fields', () => {
    expect(new Set(validateItem(emptyDraft(1, { deposit: -1 })))).toEqual(new Set(['TITLE', 'CATEGORY', 'PHOTOS', 'DAILY_RATE', 'WEEKLY_RATE', 'DEPOSIT']));
  });
  it('weekly cannot exceed seven days', () => {
    expect(validateItem(ok)).toEqual([]);
    expect(validateItem({ ...ok, weeklyRate: 701 })).toEqual(['WEEKLY_RATE']);
  });
  it('borrowed needs vendor and cost', () => {
    const borrowed = { ...ok, ownership: 'BORROWED' as const, vendorCostPerDay: null };
    expect(new Set(validateItem(borrowed))).toEqual(new Set(['VENDOR', 'VENDOR_COST']));
    expect(validateItem({ ...ok, unitValue: -1 })).toEqual(['UNIT_VALUE']);
    const saved = unwrap(draftToItem({ ...borrowed, vendorId: 3, vendorCostPerDay: 40, vendorReturnBy: D0 }));
    expect(saved).toMatchObject({ ownership: 'BORROWED', vendorId: 3, vendorCostPerDay: 40, vendorReturnBy: D0 });
  });
  it('owned items drop vendor fields and blank specs', () => {
    const saved = unwrap(draftToItem({ ...ok, vendorId: 3, vendorCostPerDay: 40, vendorReturnBy: D0, specs: [[' Power ', ' 800W '], ['', 'x']] }));
    expect(saved).toMatchObject({ vendorId: null, vendorCostPerDay: 0, vendorReturnBy: null, specs: [['Power', '800W']] });
  });
});

describe('late fees', () => {
  it('due today is not late', () => {
    expect(daysLate(D0, D0)).toBe(0);
    expect(daysLate(d(3), D0)).toBe(0);
    expect(daysLate(d(-2), D0)).toBe(2);
  });
  it('charges the daily rate per late day', () => {
    expect(lateFee(D0, D0, 50_000)).toBe(0);
    expect(lateFee(d(-3), D0, 50_000)).toBe(150_000);
  });
  it('only active rentals past their end are overdue', () => {
    expect(isOverdue(booking(1, 1, -5, -1, 'ACTIVE'), D0)).toBe(true);
    expect(isOverdue(booking(1, 1, -5, 0, 'ACTIVE'), D0)).toBe(false);
    expect(isOverdue(booking(1, 1, -5, -1, 'RETURNED'), D0)).toBe(false);
  });
  it('settlement refunds or collects', () => {
    expect(settle({ advance: 1_000, damageFee: 200, lateFee: 300 }).balance).toBe(500);
    expect(settle({ advance: 1_000, damageFee: 600, lateFee: 800 }).balance).toBe(-400);
    const s = settle({ advance: 2_000, damageFee: 0, lateFee: 0, cleaningFee: 300, dropTransportFee: 500 });
    expect(s.charges).toBe(800);
    expect(s.balance).toBe(1_200);
  });
});

describe('inventory metrics', () => {
  const stocked = (id: number, daily: number, value: number, borrowed = false, vendorCost = 0) =>
    item({ id, title: `Item ${id}`, dailyRate: daily, weeklyRate: daily * 6, unitValue: value, ownership: borrowed ? 'BORROWED' : 'OWNED', vendorId: borrowed ? 1 : null, vendorCostPerDay: vendorCost });
  const active = (unitId: number, itemId: number) => booking(unitId, unitId, -1, 1, 'ACTIVE', { itemId });
  it('per-item stock and money', () => {
    const units = [unit(1), unit(2), unit(3, 'MAINTENANCE'), unit(4, 'RETIRED')];
    const s = itemStock(stocked(1, 1_000, 50_000), units, [active(1, 1)]);
    expect(s).toMatchObject({ units: 3, out: 1, inStore: 2, inService: 1, worth: 150_000, dailyPotential: 2_000, dailyEarning: 1_000, dailyVendorCost: 0 });
  });
  it('totals split owned and borrowed', () => {
    const owned = itemStock(stocked(1, 1_000, 50_000), [unit(1), unit(2)], [active(1, 1)]);
    const borrowed = itemStock(stocked(2, 500, 20_000, true, 200), [unit(3, 'AVAILABLE', 'GOOD', 2)], [active(3, 2)]);
    expect(inventoryTotals([owned, borrowed])).toMatchObject({
      products: 2, units: 3, unitsOut: 2, unitsInStore: 1, ownedWorth: 100_000, borrowedWorth: 20_000, totalWorth: 120_000,
      dailyEarning: 1_500, dailyVendorCost: 200, netDaily: 1_300, dailyPotential: 2_500, borrowedProducts: 1,
    });
  });
});

it('overdue SMS text', () => {
  const b = booking(42, 1, -6, -2, 'ACTIVE', { deposit: 500_000 });
  expect(overdueSms(b, 'Thule Roof Box 400L', 2, 100_000)).toBe(
    'RentNest: Your rental of Thule Roof Box 400L (RN-00042) was due back on 5 Oct 2026 and is 2 days overdue. ' +
      'A late fee of ₹1,000 so far will be deducted from your advance of ₹5,000. Please return it today.',
  );
});
