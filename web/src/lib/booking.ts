import { rangeDays, type DateRange, type IsoDate } from '@/domain/dates';
import { bookingCode, formatFull } from '@/domain/format/dates';
import { formatMoney } from '@/domain/format/money';
import { bookingRange, type Booking, type BookingStatus } from '@/domain/models';
import { settle } from '@/domain/rules/lateFees';

/** A date range being picked: the end is null until the second tap. */
export type Selection = { start: IsoDate; end: IsoDate | null } | null;

/** Mirrors the Android DateRangePicker: tap a start, then an end; a tap before the start, or after a full range, starts over. */
export function nextSelection(current: Selection, tapped: IsoDate): Selection {
  if (current == null || current.end != null || tapped < current.start) return { start: tapped, end: null };
  return { start: current.start, end: tapped };
}

/** A start on its own is a one-day rental. */
export const selectionRange = (s: Selection): DateRange | null => (s ? { start: s.start, end: s.end ?? s.start } : null);

export type RentalTab = 'REQUESTED' | 'UPCOMING' | 'ACTIVE' | 'PAST';
export const RENTAL_TABS: [RentalTab, string][] = [['REQUESTED', 'Requested'], ['UPCOMING', 'Upcoming'], ['ACTIVE', 'Active'], ['PAST', 'Past']];

export function rentalTabOf(status: BookingStatus): RentalTab {
  switch (status) {
    case 'REQUESTED': return 'REQUESTED';
    case 'ACCEPTED': return 'UPCOMING';
    case 'ACTIVE': return 'ACTIVE';
    default: return 'PAST';
  }
}

/** Past rentals newest first; everything else soonest first. */
export function groupRentals<T extends { booking: Booking }>(rows: T[]): Record<RentalTab, T[]> {
  const out: Record<RentalTab, T[]> = { REQUESTED: [], UPCOMING: [], ACTIVE: [], PAST: [] };
  for (const r of rows) out[rentalTabOf(r.booking.status)].push(r);
  out.PAST.sort((a, b) => b.booking.endDate.localeCompare(a.booking.endDate));
  for (const tab of ['REQUESTED', 'UPCOMING', 'ACTIVE'] as const) out[tab].sort((a, b) => a.booking.startDate.localeCompare(b.booking.startDate));
  return out;
}

export function initialRentalTab(byTab: Record<RentalTab, unknown[]>): RentalTab {
  return (['ACTIVE', 'UPCOMING', 'REQUESTED'] as const).find(t => byTab[t].length > 0) ?? 'PAST';
}

export interface BillInput {
  isReturn: boolean;
  booking: Booking;
  shopName: string;
  shopPhone: string;
  customerName: string;
  itemTitle: string;
  dailyRate: number;
}

/** The plain-text bill the admin shares with the customer. Port of BillScreen.shareBill. */
export function billText({ isReturn, booking: b, shopName, shopPhone, customerName, itemTitle, dailyRate }: BillInput): string {
  const lines = [shopName];
  if (shopPhone) lines.push(shopPhone);
  lines.push('', isReturn ? 'CLOSING BILL' : 'PICKUP RECEIPT');
  lines.push(`Bill: ${bookingCode(b.id)}  Date: ${formatFull(b.startDate)}`);
  lines.push(`To: ${customerName} (${b.contactPhone})`, '');
  lines.push(`${itemTitle}  x1  ${rangeDays(bookingRange(b))}d @ ${formatMoney(dailyRate)} = ${formatMoney(b.subtotal)}`, '');
  if (isReturn) {
    const s = settle({ advance: b.deposit, damageFee: b.damageFee, lateFee: b.lateFee, cleaningFee: b.cleaningFee, dropTransportFee: b.dropTransportFee });
    if (b.lateFee > 0) lines.push(`Late fee: ${formatMoney(b.lateFee)}`);
    if (b.damageFee > 0) lines.push(`Damage charge: ${formatMoney(b.damageFee)}`);
    if (b.dropTransportFee > 0) lines.push(`Transport charge (drop): ${formatMoney(b.dropTransportFee)}`);
    if (b.cleaningFee > 0) lines.push(`Cleaning / others: ${formatMoney(b.cleaningFee)}`);
    lines.push(`Total new charges: ${formatMoney(s.charges)}`, `Advance: ${formatMoney(b.deposit)}`);
    lines.push(s.balance >= 0 ? `Refund to customer: ${formatMoney(s.balance)}` : `Balance to pay: ${formatMoney(-s.balance)}`);
  } else {
    lines.push(`Advance (refundable): ${formatMoney(b.deposit)}`);
    if (b.pickupTransportFee > 0) lines.push(`Transport charge (pickup): ${formatMoney(b.pickupTransportFee)}`);
    lines.push(`Total collected now: ${formatMoney(b.subtotal + b.deposit + b.pickupTransportFee)}`);
  }
  return lines.join('\n');
}
