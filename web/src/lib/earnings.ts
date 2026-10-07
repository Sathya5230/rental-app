import { addDays, isoDate, type IsoDate } from '@/domain/dates';
import { formatShort } from '@/domain/format/dates';
import type { Booking } from '@/domain/models';

export type EarningsPeriod = 'WEEKLY' | 'MONTHLY';
export const EARNINGS_PERIODS: [EarningsPeriod, string, string][] = [['WEEKLY', 'Weekly', 'This week'], ['MONTHLY', 'Monthly', 'This month']];

export interface EarningsBar { label: string; amount: number; start: IsoDate; end: IsoDate }
export interface TopItem { title: string; amount: number; rentals: number }
export interface Payout { label: string; amount: number; pending: boolean }
export interface EarningsSummary { bars: EarningsBar[]; total: number; changePercent: number | null; topItems: TopItem[]; payouts: Payout[] }

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const amount = (b: Booking) => b.subtotal + b.damageFee;

function mondayOf(d: IsoDate): IsoDate {
  const dow = new Date(`${d}T00:00:00Z`).getUTCDay();
  return addDays(d, -((dow + 6) % 7));
}

function monthBounds(today: IsoDate, monthsBack: number): [IsoDate, IsoDate] {
  const [y, m] = today.split('-').map(Number);
  const first = isoDate(y, m - monthsBack, 1);
  const [fy, fm] = first.split('-').map(Number);
  return [first, isoDate(fy, fm + 1, 0)];
}

/** Port of EarningsCalculator.compute: closed rentals (rent plus damage) by week or month. */
export function earnings(period: EarningsPeriod, today: IsoDate, bookings: Booking[], titles: Map<number, string>): EarningsSummary {
  const returned = bookings.filter(b => b.status === 'RETURNED');
  const monday = mondayOf(today);
  const weeks = [7, 6, 5, 4, 3, 2, 1, 0].map((w): [IsoDate, IsoDate] => [addDays(monday, -7 * w), addDays(monday, -7 * w + 6)]);
  const buckets = period === 'WEEKLY' ? weeks : [5, 4, 3, 2, 1, 0].map(m => monthBounds(today, m));
  const sum = (s: IsoDate, e: IsoDate) => returned.filter(b => b.endDate >= s && b.endDate <= e).reduce((acc, b) => acc + amount(b), 0);
  const bars = buckets.map(([s, e]) => ({
    label: period === 'WEEKLY' ? formatShort(s) : MONTHS[Number(s.slice(5, 7)) - 1], amount: sum(s, e), start: s, end: e,
  }));
  const total = bars[bars.length - 1].amount;
  const prev = bars[bars.length - 2].amount;
  const windowStart = bars[0].start;
  const byItem = new Map<number, Booking[]>();
  for (const b of returned.filter(b => b.endDate >= windowStart)) byItem.set(b.itemId, [...(byItem.get(b.itemId) ?? []), b]);
  const topItems = [...byItem]
    .map(([id, list]) => ({ title: titles.get(id) ?? 'Item', amount: list.reduce((acc, b) => acc + amount(b), 0), rentals: list.length }))
    .sort((a, b) => b.amount - a.amount)
    .slice(0, 5);
  const payouts = weeks.slice(-4).reverse().flatMap(([s, e]) => {
    const a = sum(s, e);
    return a === 0 ? [] : [{ label: `Week of ${formatShort(s)}`, amount: a, pending: s === monday }];
  });
  return { bars, total, changePercent: prev === 0 ? null : Math.trunc(((total - prev) * 100) / prev), topItems, payouts };
}
