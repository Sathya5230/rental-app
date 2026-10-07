import { bookingCode, formatDays, formatFull } from '../format/dates';
import { formatMoney } from '../format/money';
import type { Booking } from '../models';

export const overdueSms = (b: Booking, itemTitle: string, daysLate: number, lateFee: number) =>
  `RentNest: Your rental of ${itemTitle} (${bookingCode(b.id)}) was due back on ${formatFull(b.endDate)} ` +
  `and is ${formatDays(daysLate)} overdue. A late fee of ${formatMoney(lateFee)} so far will be ` +
  `deducted from your advance of ${formatMoney(b.deposit)}. Please return it today.`;
