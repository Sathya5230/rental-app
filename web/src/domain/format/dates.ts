import type { DateRange, IsoDate } from '../dates';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const parts = (d: IsoDate) => d.split('-').map(Number) as [number, number, number];

export function formatShort(d: IsoDate): string {
  const [, m, day] = parts(d);
  return `${day} ${MONTHS[m - 1]}`;
}

export function formatFull(d: IsoDate): string {
  const [y, m, day] = parts(d);
  return `${day} ${MONTHS[m - 1]} ${y}`;
}

export function formatMonth(d: IsoDate): string {
  const [y, m] = parts(d);
  return `${MONTHS[m - 1]} ${y}`;
}

export const formatRange = (r: DateRange) => (r.start === r.end ? formatShort(r.start) : `${formatShort(r.start)} – ${formatShort(r.end)}`);
export const bookingCode = (id: number) => 'RN-' + String(id).padStart(5, '0');
export const formatDays = (n: number) => (n === 1 ? '1 day' : `${n} days`);

export function formatRelative(thenMillis: number, nowMillis: number): string {
  const mins = Math.floor((nowMillis - thenMillis) / 60_000);
  if (mins < 1) return 'Just now';
  if (mins < 60) return `${mins}m ago`;
  if (mins < 1_440) return `${Math.floor(mins / 60)}h ago`;
  return `${Math.floor(mins / 1_440)}d ago`;
}
