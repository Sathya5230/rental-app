import { failure, success, type Outcome } from '../errors';
import type { Booking, BookingAction, BookingStatus } from '../models';

const TRANSITIONS: Partial<Record<`${BookingStatus}:${BookingAction}`, BookingStatus>> = {
  'REQUESTED:ACCEPT': 'ACCEPTED',
  'REQUESTED:DECLINE': 'DECLINED',
  'REQUESTED:CANCEL': 'CANCELLED',
  'ACCEPTED:CANCEL': 'CANCELLED',
  'ACCEPTED:CHECK_OUT': 'ACTIVE',
  'ACTIVE:RETURN': 'RETURNED',
};

export function nextStatus(from: BookingStatus, action: BookingAction): Outcome<BookingStatus> {
  const to = TRANSITIONS[`${from}:${action}`];
  return to ? success(to) : failure({ kind: 'InvalidTransition', from, action });
}

export const canCancel = (b: Booking) => TRANSITIONS[`${b.status}:CANCEL`] !== undefined;
export const canReview = (b: Booking) => b.status === 'RETURNED' && !b.reviewed;
