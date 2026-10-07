import { describe, expect, it } from 'vitest';
import { addDays, daysBetween, localToday, overlaps, rangeContains, rangeDays, type DateRange } from './dates';
import { errorMessage } from './errors';
import { bookingCode, formatRange, formatRelative } from './format/dates';
import { compactMoney, formatMoney, moneyToInput, parseRupees } from './format/money';
import { displayPhone, e164Phone } from './format/phone';

const D0 = '2026-10-07';
const d = (n: number) => addDays(D0, n);
const r = (a: number, b: number): DateRange => ({ start: d(a), end: d(b) });

describe('money', () => {
  it('formats with Indian grouping', () => {
    expect(formatMoney(0)).toBe('₹0');
    expect(formatMoney(150_000)).toBe('₹1,500');
    expect(formatMoney(12_345_678)).toBe('₹1,23,456.78');
    expect(formatMoney(123_456_700)).toBe('₹12,34,567');
    expect(formatMoney(-5_000)).toBe('-₹50');
  });
  it('parses user input', () => {
    expect(parseRupees('1,500')).toBe(150_000);
    expect(parseRupees('12.5')).toBe(1_250);
    expect(parseRupees('0.125')).toBe(13);
    expect(parseRupees('abc')).toBeNull();
    expect(parseRupees('-3')).toBeNull();
    expect(parseRupees('')).toBeNull();
  });
  it('compacts for chart axes', () => {
    expect(compactMoney(95_000)).toBe('₹950');
    expect(compactMoney(1_250_000)).toBe('₹12.5k');
    expect(compactMoney(12_000_000)).toBe('₹1.2L');
  });
  it('round-trips form input', () => {
    expect(moneyToInput(150_000)).toBe('1500');
    expect(moneyToInput(1_250)).toBe('12.50');
  });
});

describe('dates', () => {
  it('ranges are inclusive', () => {
    expect(rangeDays(r(0, 2))).toBe(3);
    expect(rangeContains(r(0, 2), d(2))).toBe(true);
    expect(rangeContains(r(0, 2), d(3))).toBe(false);
    expect(overlaps(r(0, 2), r(2, 5))).toBe(true);
    expect(overlaps(r(0, 2), r(3, 5))).toBe(false);
    expect(rangeDays(r(2, 0))).toBe(0);
  });
  it('crosses month and year ends', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
    expect(daysBetween('2026-12-30', '2027-01-02')).toBe(3);
  });
  it('today is the local calendar date east of UTC', () => {
    // npm test runs with TZ=Asia/Kolkata: 00:30 on 7 Oct local is still 6 Oct in UTC.
    expect(new Date(2026, 9, 7, 0, 30).getTimezoneOffset()).toBe(-330);
    expect(localToday(new Date(2026, 9, 7, 0, 30))).toBe('2026-10-07');
  });
  it('formats', () => {
    expect(formatRange(r(0, 2))).toBe('7 Oct – 9 Oct');
    expect(formatRange(r(0, 0))).toBe('7 Oct');
    expect(bookingCode(42)).toBe('RN-00042');
  });
  it('formats relative times', () => {
    const now = 10 * 86_400_000;
    expect(formatRelative(now - 30_000, now)).toBe('Just now');
    expect(formatRelative(now - 5 * 60_000, now)).toBe('5m ago');
    expect(formatRelative(now - 3 * 3_600_000, now)).toBe('3h ago');
    expect(formatRelative(now - 2 * 86_400_000, now)).toBe('2d ago');
  });
});

describe('phone numbers', () => {
  it('normalises Indian mobiles', () => {
    expect(displayPhone('9845012001')).toBe('+91 98450 12001');
    expect(displayPhone('+91 98450 12001')).toBe('+91 98450 12001');
    expect(displayPhone('09845012001')).toBe('+91 98450 12001');
    expect(e164Phone('+91 98450 12001')).toBe('+919845012001');
  });
  it('rejects invalid numbers', () => {
    expect(displayPhone('12345')).toBeNull();
    expect(displayPhone('1234567890')).toBeNull(); // mobiles start with 6-9
    expect(displayPhone('')).toBeNull();
  });
});

it('errors have user-facing messages', () => {
  expect(errorMessage({ kind: 'InvalidPhone' })).toBe('Enter a valid 10-digit mobile number.');
  expect(errorMessage({ kind: 'InvalidTransition', from: 'RETURNED', action: 'ACCEPT' })).toBe("That action isn't available for this booking anymore.");
});
