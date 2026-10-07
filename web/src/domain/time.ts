import { localToday, startOfLocalDayMs, type IsoDate } from './dates';

export interface TimeProvider {
  today(): IsoDate;
  nowMillis(): number;
  startOfTodayMillis(): number;
}

export const systemTime: TimeProvider = {
  today: () => localToday(),
  nowMillis: () => Date.now(),
  startOfTodayMillis: () => startOfLocalDayMs(localToday()),
};

/** Midday on [day], so "now" and "today" agree. For tests. */
export function fixedTime(day: IsoDate): TimeProvider {
  const start = startOfLocalDayMs(day);
  return { today: () => day, nowMillis: () => start + 12 * 3_600_000, startOfTodayMillis: () => start };
}
