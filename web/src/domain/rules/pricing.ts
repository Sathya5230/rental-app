import { isValidRange, rangeDays, type DateRange } from '../dates';
import { fail, success, type Outcome } from '../errors';
import type { Item } from '../models';

export interface PriceBreakdown {
  days: number;
  weeks: number;
  extraDays: number;
  weeklyCharge: number;
  dailyCharge: number;
  subtotal: number;
  deposit: number;
  totalDueNow: number;
  /** True when a partial week was rounded up to the (cheaper) weekly rate. */
  bestPriceApplied: boolean;
}

function breakdown(days: number, weeks: number, extraDays: number, weeklyCharge: number, dailyCharge: number, deposit: number): PriceBreakdown {
  const subtotal = weeklyCharge + dailyCharge;
  return { days, weeks, extraDays, weeklyCharge, dailyCharge, subtotal, deposit, totalDueNow: subtotal + deposit, bestPriceApplied: weeks * 7 + extraDays !== days };
}

export function quote(dailyRate: number, weeklyRate: number, deposit: number, range: DateRange): Outcome<PriceBreakdown> {
  if (!isValidRange(range)) return fail('InvalidDateRange');
  const weekly = weeklyRate > 0 ? weeklyRate : dailyRate * 7;
  const days = rangeDays(range);
  const weeks = Math.floor(days / 7);
  const extra = days % 7;
  const raw = weeks * weekly + extra * dailyRate;
  const roundedUp = (weeks + 1) * weekly;
  if (extra > 0 && roundedUp < raw) return success(breakdown(days, weeks + 1, 0, roundedUp, 0, deposit));
  return success(breakdown(days, weeks, extra, weeks * weekly, extra * dailyRate, deposit));
}

export const quoteItem = (item: Item, range: DateRange) => quote(item.dailyRate, item.weeklyRate, item.deposit, range);
