import { describe, expect, it } from 'vitest';
import { addDays } from '@/domain/dates';
import type { Booking, BookingStatus } from '@/domain/models';
import { billText, groupRentals, initialRentalTab, nextSelection, rentalTabOf, selectionRange } from './booking';

const D0 = '2026-10-07';
const d = (n: number) => addDays(D0, n);

describe('date selection', () => {
  it('first tap starts, second tap ends, a tap before the start restarts', () => {
    expect(nextSelection(null, d(5))).toEqual({ start: d(5), end: null });
    expect(nextSelection({ start: d(5), end: null }, d(7))).toEqual({ start: d(5), end: d(7) });
    expect(nextSelection({ start: d(5), end: null }, d(3))).toEqual({ start: d(3), end: null });
    expect(nextSelection({ start: d(5), end: d(7) }, d(9))).toEqual({ start: d(9), end: null });
  });
  it('tapping the same day twice is a one-day rental', () => {
    expect(selectionRange(nextSelection({ start: d(5), end: null }, d(5)))).toEqual({ start: d(5), end: d(5) });
    expect(selectionRange({ start: d(5), end: null })).toEqual({ start: d(5), end: d(5) });
    expect(selectionRange(null)).toBeNull();
  });
});

const booking = (id: number, status: BookingStatus, from: number, to: number): Booking => ({
  id, itemId: 1, unitId: 1, customerId: 1, startDate: d(from), endDate: d(to), status, subtotal: 100_000, deposit: 300_000, damageFee: 0,
  createdAt: 0, reviewed: false, contactPhone: '+91 98450 12001', lateFee: 0, overdueSmsAt: null, pickupTransportFee: 0, dropTransportFee: 0, cleaningFee: 0,
});

describe('rentals tabs', () => {
  it('maps statuses to tabs', () => {
    expect(['REQUESTED', 'ACCEPTED', 'ACTIVE', 'RETURNED', 'DECLINED', 'CANCELLED'].map(s => rentalTabOf(s as BookingStatus)))
      .toEqual(['REQUESTED', 'UPCOMING', 'ACTIVE', 'PAST', 'PAST', 'PAST']);
  });
  it('sorts past rentals newest first and the rest soonest first', () => {
    const rows = [booking(1, 'RETURNED', -20, -18), booking(2, 'RETURNED', -5, -3), booking(3, 'ACCEPTED', 9, 10), booking(4, 'ACCEPTED', 2, 3)].map(b => ({ booking: b }));
    const grouped = groupRentals(rows);
    expect(grouped.PAST.map(r => r.booking.id)).toEqual([2, 1]);
    expect(grouped.UPCOMING.map(r => r.booking.id)).toEqual([4, 3]);
    expect(grouped.ACTIVE).toEqual([]);
  });
  it('opens on the most pressing non-empty tab', () => {
    const rows = [booking(1, 'REQUESTED', 1, 2), booking(2, 'ACCEPTED', 3, 4)].map(b => ({ booking: b }));
    expect(initialRentalTab(groupRentals(rows))).toBe('UPCOMING');
    expect(initialRentalTab(groupRentals([]))).toBe('PAST');
  });
});

describe('bill text', () => {
  const base = { shopName: 'RentNest Store', shopPhone: '+91 98450 10000', customerName: 'Arjun Mehta', itemTitle: 'Tent', dailyRate: 40_000 };
  it('pickup receipt totals rental, advance and transport', () => {
    const text = billText({ ...base, isReturn: false, booking: { ...booking(42, 'ACTIVE', 0, 2), subtotal: 120_000, pickupTransportFee: 15_000 } });
    expect(text).toContain('PICKUP RECEIPT');
    expect(text).toContain('Bill: RN-00042');
    expect(text).toContain('Tent  x1  3d @ ₹400 = ₹1,200');
    expect(text).toContain('Total collected now: ₹4,350');
  });
  it('closing bill refunds or collects the balance', () => {
    const refund = billText({ ...base, isReturn: true, booking: { ...booking(42, 'RETURNED', -3, -1), lateFee: 40_000, damageFee: 50_000 } });
    expect(refund).toContain('CLOSING BILL');
    expect(refund).toContain('Total new charges: ₹900');
    expect(refund).toContain('Refund to customer: ₹2,100');
    const owed = billText({ ...base, isReturn: true, booking: { ...booking(42, 'RETURNED', -3, -1), damageFee: 500_000 } });
    expect(owed).toContain('Balance to pay: ₹2,000');
  });
});
