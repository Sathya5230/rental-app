import { isValidRange, overlaps, rangeContains, rangeDates, type DateRange, type IsoDate } from '../dates';
import { bookingRange, type Booking, type BookingStatus, type ItemUnit } from '../models';

export const RESERVING: BookingStatus[] = ['ACCEPTED', 'ACTIVE'];
const reserves = (b: Booking) => RESERVING.includes(b.status);

export const usableUnits = (units: ItemUnit[]) => units.filter(u => u.status === 'AVAILABLE');

/**
 * Free units on [date]. A reserving booking whose unit is no longer usable (or unassigned)
 * still consumes a slot, so a promised customer is never double-booked.
 */
export function freeCountOn(date: IsoDate, units: ItemUnit[], bookings: Booking[]): number {
  const usableIds = new Set(usableUnits(units).map(u => u.id));
  const reserving = bookings.filter(b => reserves(b) && rangeContains(bookingRange(b), date));
  const reservedUsable = new Set(reserving.flatMap(b => (b.unitId != null && usableIds.has(b.unitId) ? [b.unitId] : [])));
  const orphaned = reserving.filter(b => b.unitId == null || !usableIds.has(b.unitId)).length;
  return Math.max(0, usableIds.size - reservedUsable.size - orphaned);
}

export const isBookable = (range: DateRange, units: ItemUnit[], bookings: Booking[]) =>
  isValidRange(range) && rangeDates(range).every(d => freeCountOn(d, units, bookings) >= 1);

export const unavailableDates = (window: DateRange, units: ItemUnit[], bookings: Booking[]) =>
  new Set(rangeDates(window).filter(d => freeCountOn(d, units, bookings) === 0));

export function freeUnitsFor(range: DateRange, units: ItemUnit[], bookings: Booking[]): ItemUnit[] {
  const busy = new Set(bookings.filter(b => reserves(b) && overlaps(bookingRange(b), range)).map(b => b.unitId));
  return usableUnits(units).filter(u => !busy.has(u.id));
}
