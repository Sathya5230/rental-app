import { daysBetween, type IsoDate } from '../dates';
import type { Booking } from '../models';

/** What happens to the customer's advance when a rental closes. */
export interface Settlement {
  advance: number;
  damageFee: number;
  lateFee: number;
  cleaningFee: number;
  /** Transport charge for collecting the item back from the customer. */
  dropTransportFee: number;
  charges: number;
  /** Positive: refund to the customer. Negative: the customer still owes this much. */
  balance: number;
}

export function settle(s: { advance: number; damageFee: number; lateFee: number; cleaningFee?: number; dropTransportFee?: number }): Settlement {
  const cleaningFee = s.cleaningFee ?? 0;
  const dropTransportFee = s.dropTransportFee ?? 0;
  const charges = s.damageFee + s.lateFee + cleaningFee + dropTransportFee;
  return { ...s, cleaningFee, dropTransportFee, charges, balance: s.advance - charges };
}

/** Days past the inclusive end date. A rental due today is not late yet. */
export const daysLate = (endDate: IsoDate, today: IsoDate) => Math.max(0, daysBetween(endDate, today));

/** Each late day is charged at the item's daily rate. */
export const lateFee = (endDate: IsoDate, today: IsoDate, dailyRate: number) => daysLate(endDate, today) * dailyRate;

export const isOverdue = (b: Booking, today: IsoDate) => b.status === 'ACTIVE' && b.endDate < today;
