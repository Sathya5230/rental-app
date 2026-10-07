import { isValidRange, type DateRange } from '@/domain/dates';
import { fail, success, type Outcome } from '@/domain/errors';
import { formatRange, formatShort } from '@/domain/format/dates';
import { formatMoney } from '@/domain/format/money';
import { displayPhone } from '@/domain/format/phone';
import { bookingRange, type Audience, type Booking, type BookingAction, type BookingStatus, type Review, type UnitCondition } from '@/domain/models';
import { freeUnitsFor, isBookable } from '@/domain/rules/availability';
import { canReview, nextStatus } from '@/domain/rules/bookingStateMachine';
import { isOverdue, lateFee } from '@/domain/rules/lateFees';
import { quoteItem } from '@/domain/rules/pricing';
import type { TimeProvider } from '@/domain/time';
import type { RentNestDb } from './db';
import { addNotification } from './notificationRepository';

export interface CheckOutInput {
  checklist: string[];
  notes: string;
  /** Charged to deliver the item to the customer at pickup. */
  transportFee?: number;
}

export interface ReturnInput {
  checklist: string[];
  conditionAfter: UnitCondition;
  notes: string;
  damageFee: number;
  /** Charged at return and deducted from the advance, like the damage fee. */
  transportFee?: number;
  cleaningFee?: number;
}

export class BookingRepository {
  constructor(private db: RentNestDb, private time: TimeProvider) {}

  allBookings() { return this.db.bookings.toArray(); }
  bookingsForItem(itemId: number) { return this.db.bookings.where('itemId').equals(itemId).toArray(); }
  booking(id: number) { return this.db.bookings.get(id); }

  async bookingsForCustomer(customerId: number) {
    return (await this.db.bookings.where('customerId').equals(customerId).toArray()).sort((a, b) => b.startDate.localeCompare(a.startDate));
  }

  async bookingsForProvider(providerId: number) {
    const itemIds = new Set(await this.db.items.where('providerId').equals(providerId).primaryKeys());
    return (await this.db.bookings.toArray()).filter(b => itemIds.has(b.itemId)).sort((a, b) => a.startDate.localeCompare(b.startDate));
  }

  async handovers(bookingId: number) {
    return (await this.db.handovers.where('bookingId').equals(bookingId).toArray()).sort((a, b) => a.timestamp - b.timestamp);
  }

  /** Creates a REQUESTED booking and notifies the admin. Nothing is reserved until the admin accepts. */
  requestBooking(itemId: number, customerId: number, range: DateRange, contactPhone: string): Promise<Outcome<Booking>> {
    return this.tx<Booking>(async () => {
      if (!isValidRange(range) || range.start < this.time.today()) return fail('InvalidDateRange');
      const phone = displayPhone(contactPhone);
      if (!phone) return fail('InvalidPhone');
      const item = await this.db.items.get(itemId);
      if (!item) return fail('NotFound');
      const provider = await this.db.providers.get(item.providerId);
      if (!provider) return fail('NotFound');
      if (provider.userId === customerId) return fail('OwnListing');
      const units = await this.db.units.where('itemId').equals(itemId).toArray();
      if (!isBookable(range, units, await this.bookingsForItem(itemId))) return fail('DatesUnavailable');
      const q = quoteItem(item, range);
      if (!q.ok) return q;
      const fields: Omit<Booking, 'id'> = {
        itemId, unitId: null, customerId, startDate: range.start, endDate: range.end, status: 'REQUESTED',
        subtotal: q.value.subtotal, deposit: q.value.deposit, damageFee: 0, createdAt: this.time.nowMillis(), reviewed: false,
        contactPhone: phone, lateFee: 0, overdueSmsAt: null, pickupTransportFee: 0, dropTransportFee: 0, cleaningFee: 0,
      };
      const id = await this.db.bookings.add(fields);
      const customer = (await this.db.users.get(customerId))?.name ?? 'A customer';
      await this.notify(provider.userId, 'ADMIN', 'New rental request', `${customer} wants ${item.title} · ${formatRange(range)}. Approve or decline it.`, id);
      return success({ ...fields, id });
    });
  }

  accept(bookingId: number, unitId: number) {
    return this.transition(bookingId, 'ACCEPT', async (b, next) => {
      const units = await this.db.units.where('itemId').equals(b.itemId).toArray();
      const others = (await this.bookingsForItem(b.itemId)).filter(o => o.id !== b.id);
      if (!freeUnitsFor(bookingRange(b), units, others).some(u => u.id === unitId)) return fail('NoUnitFree');
      const updated = { ...b, status: next, unitId };
      await this.db.bookings.put(updated);
      await this.notify(b.customerId, 'CUSTOMER', 'Request approved',
        `${await this.title(b)} is reserved for ${formatRange(bookingRange(b))}. Pay the advance of ${formatMoney(b.deposit)} at pickup.`, b.id);
      return success(updated);
    });
  }

  decline(bookingId: number) {
    return this.transition(bookingId, 'DECLINE', async (b, next) => {
      const updated = { ...b, status: next };
      await this.db.bookings.put(updated);
      await this.notify(b.customerId, 'CUSTOMER', 'Request declined', `${await this.title(b)} isn't available for those dates. Try other dates.`, b.id);
      return success(updated);
    });
  }

  cancel(bookingId: number) {
    return this.transition(bookingId, 'CANCEL', async (b, next) => {
      const updated = { ...b, status: next };
      await this.db.bookings.put(updated);
      const owner = await this.ownerOf(b);
      if (owner != null) await this.notify(owner, 'ADMIN', 'Booking cancelled', `${await this.title(b)} · ${formatRange(bookingRange(b))} was cancelled`, b.id);
      return success(updated);
    });
  }

  checkOut(bookingId: number, input: CheckOutInput) {
    return this.transition(bookingId, 'CHECK_OUT', async (b, next) => {
      const unit = b.unitId != null ? await this.db.units.get(b.unitId) : undefined;
      if (!unit) return fail('NoUnitFree');
      const transport = Math.max(0, input.transportFee ?? 0);
      const updated = { ...b, status: next, pickupTransportFee: transport };
      await this.db.bookings.put(updated);
      await this.db.handovers.add({
        bookingId: b.id, type: 'PICKUP', checklist: input.checklist, conditionAfter: unit.condition, notes: input.notes, damageFee: 0, timestamp: this.time.nowMillis(),
      });
      const transportText = transport > 0 ? ` Transport charge collected: ${formatMoney(transport)}.` : '';
      await this.notify(b.customerId, 'CUSTOMER', 'Rental started', `Enjoy your ${await this.title(b)}!${transportText} Return by ${formatShort(b.endDate)}.`, b.id);
      return success(updated);
    });
  }

  processReturn(bookingId: number, input: ReturnInput) {
    return this.transition(bookingId, 'RETURN', async (b, next) => {
      const damage = Math.max(0, input.damageFee);
      const transport = Math.max(0, input.transportFee ?? 0);
      const cleaning = Math.max(0, input.cleaningFee ?? 0);
      const dailyRate = (await this.db.items.get(b.itemId))?.dailyRate ?? 0;
      const late = lateFee(b.endDate, this.time.today(), dailyRate);
      const updated = { ...b, status: next, damageFee: damage, lateFee: late, dropTransportFee: transport, cleaningFee: cleaning };
      await this.db.bookings.put(updated);
      const unit = b.unitId != null ? await this.db.units.get(b.unitId) : undefined;
      if (unit) {
        await this.db.units.put({ ...unit, condition: input.conditionAfter, status: input.conditionAfter === 'DAMAGED' ? 'MAINTENANCE' : unit.status });
      }
      await this.db.handovers.add({
        bookingId: b.id, type: 'RETURN', checklist: input.checklist, conditionAfter: input.conditionAfter, notes: input.notes, damageFee: damage, timestamp: this.time.nowMillis(),
      });
      const charges = [
        late > 0 ? `late fee ${formatMoney(late)}` : null,
        damage > 0 ? `damage fee ${formatMoney(damage)}` : null,
        cleaning > 0 ? `cleaning charge ${formatMoney(cleaning)}` : null,
        transport > 0 ? `transport charge ${formatMoney(transport)}` : null,
      ].filter(Boolean);
      const feeText = charges.length === 0 ? ' Your advance is on its way back.' : ` Deducted from your advance: ${charges.join(', ')}.`;
      await this.notify(b.customerId, 'CUSTOMER', 'Rental closed', `Thanks for returning ${await this.title(b)}.${feeText} Leave a review?`, b.id);
      return success(updated);
    });
  }

  submitReview(bookingId: number, rating: number, text: string): Promise<Outcome<Review>> {
    return this.tx<Review>(async () => {
      const b = await this.db.bookings.get(bookingId);
      if (!b) return fail('NotFound');
      if (!canReview(b)) return fail('NotReviewable');
      if (!Number.isInteger(rating) || rating < 1 || rating > 5) return fail('InvalidRating');
      const review: Omit<Review, 'id'> = { itemId: b.itemId, bookingId: b.id, customerId: b.customerId, rating, text: text.trim(), createdAt: this.time.nowMillis() };
      const id = await this.db.reviews.add(review);
      await this.db.bookings.put({ ...b, reviewed: true });
      const owner = await this.ownerOf(b);
      if (owner != null) await this.notify(owner, 'ADMIN', `New ${rating}★ review`, `${await this.title(b)}: "${text.trim().slice(0, 80)}"`, b.id);
      return success({ ...review, id });
    });
  }

  /** Marks an overdue rental as reminded (SMS sent) and tells the customer in the app. */
  recordOverdueReminder(bookingId: number): Promise<Outcome<Booking>> {
    return this.tx<Booking>(async () => {
      const b = await this.db.bookings.get(bookingId);
      if (!b) return fail('NotFound');
      const today = this.time.today();
      if (!isOverdue(b, today)) return fail('NotOverdue');
      const updated = { ...b, overdueSmsAt: this.time.nowMillis() };
      await this.db.bookings.put(updated);
      const rate = (await this.db.items.get(b.itemId))?.dailyRate ?? 0;
      await this.notify(b.customerId, 'CUSTOMER', 'Return overdue',
        `${await this.title(b)} was due on ${formatShort(b.endDate)}. Late fee so far: ${formatMoney(lateFee(b.endDate, today, rate))}, taken from your advance. Please return it today.`, b.id);
      return success(updated);
    });
  }

  private tx<T>(fn: () => Promise<Outcome<T>>): Promise<Outcome<T>> {
    return this.db.transaction('rw', this.db.tables, fn);
  }

  private transition(bookingId: number, action: BookingAction, apply: (b: Booking, next: BookingStatus) => Promise<Outcome<Booking>>) {
    return this.tx<Booking>(async () => {
      const b = await this.db.bookings.get(bookingId);
      if (!b) return fail('NotFound');
      const next = nextStatus(b.status, action);
      if (!next.ok) return next;
      return apply(b, next.value);
    });
  }

  private async title(b: Booking) {
    return (await this.db.items.get(b.itemId))?.title ?? 'your item';
  }

  private async ownerOf(b: Booking): Promise<number | undefined> {
    const item = await this.db.items.get(b.itemId);
    return item ? (await this.db.providers.get(item.providerId))?.userId : undefined;
  }

  private notify(userId: number, audience: Audience, title: string, body: string, bookingId: number) {
    return addNotification(this.db, this.time, userId, audience, title, body, bookingId);
  }
}
