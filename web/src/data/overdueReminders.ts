import { fail, success, type Outcome } from '@/domain/errors';
import { e164Phone } from '@/domain/format/phone';
import type { Booking } from '@/domain/models';
import { daysLate, isOverdue, lateFee } from '@/domain/rules/lateFees';
import { overdueSms } from '@/domain/rules/overdueMessage';
import type { TimeProvider } from '@/domain/time';
import type { BookingRepository } from './bookingRepository';
import type { CatalogRepository } from './catalogRepository';

/** The SMS a customer should get. The admin sends it from their own phone. */
export interface SmsDraft { bookingId: number; phone: string; message: string }

/** Opens the device's SMS app with the message filled in. */
export const smsLink = (d: SmsDraft) => `sms:${e164Phone(d.phone) ?? d.phone}?&body=${encodeURIComponent(d.message)}`;

export class OverdueReminders {
  constructor(private bookings: BookingRepository, private catalog: CatalogRepository, private time: TimeProvider) {}

  async draft(bookingId: number): Promise<Outcome<SmsDraft>> {
    const b = await this.bookings.booking(bookingId);
    if (!b) return fail('NotFound');
    const today = this.time.today();
    if (!isOverdue(b, today)) return fail('NotOverdue');
    const item = await this.catalog.item(b.itemId);
    const message = overdueSms(b, item?.title ?? 'your item', daysLate(b.endDate, today), lateFee(b.endDate, today, item?.dailyRate ?? 0));
    const phone = b.contactPhone || (await this.catalog.user(b.customerId))?.phone || '';
    return success({ bookingId: b.id, phone, message });
  }

  /** Records a reminder the admin sent by hand through their SMS app. */
  markSent(bookingId: number) {
    return this.bookings.recordOverdueReminder(bookingId);
  }

  /** One reminder per day: never sent, or last sent before today. */
  needsReminder(b: Booking) {
    return b.overdueSmsAt == null || b.overdueSmsAt < this.time.startOfTodayMillis();
  }
}
