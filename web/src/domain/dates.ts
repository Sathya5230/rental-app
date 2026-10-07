/** A calendar date, "YYYY-MM-DD". Strings compare in date order. */
export type IsoDate = string;

/** Inclusive date range. */
export interface DateRange {
  start: IsoDate;
  end: IsoDate;
}

const DAY_MS = 86_400_000;

function toUtcMs(d: IsoDate): number {
  const [y, m, day] = d.split('-').map(Number);
  return Date.UTC(y, m - 1, day);
}

function fromUtcMs(ms: number): IsoDate {
  return new Date(ms).toISOString().slice(0, 10);
}

export function isoDate(year: number, month: number, day: number): IsoDate {
  return fromUtcMs(Date.UTC(year, month - 1, day));
}

export function addDays(d: IsoDate, n: number): IsoDate {
  return fromUtcMs(toUtcMs(d) + n * DAY_MS);
}

export function daysBetween(from: IsoDate, to: IsoDate): number {
  return Math.round((toUtcMs(to) - toUtcMs(from)) / DAY_MS);
}

/** Today in the user's own time zone. */
export function localToday(now: Date = new Date()): IsoDate {
  return isoDate(now.getFullYear(), now.getMonth() + 1, now.getDate());
}

/** Epoch millis of local midnight at the start of [d]. */
export function startOfLocalDayMs(d: IsoDate): number {
  const [y, m, day] = d.split('-').map(Number);
  return new Date(y, m - 1, day).getTime();
}

export const isValidRange = (r: DateRange) => r.end >= r.start;
export const rangeDays = (r: DateRange) => (isValidRange(r) ? daysBetween(r.start, r.end) + 1 : 0);
export const rangeContains = (r: DateRange, d: IsoDate) => d >= r.start && d <= r.end;
export const overlaps = (a: DateRange, b: DateRange) => a.start <= b.end && b.start <= a.end;
export const rangeDates = (r: DateRange): IsoDate[] => Array.from({ length: rangeDays(r) }, (_, i) => addDays(r.start, i));
