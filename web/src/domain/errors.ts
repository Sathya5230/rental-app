import type { BookingAction, BookingStatus, ItemField } from './models';

export type DomainError =
  | { kind: 'InvalidDateRange' }
  | { kind: 'DatesUnavailable' }
  | { kind: 'NoUnitFree' }
  | { kind: 'OwnListing' }
  | { kind: 'NotFound' }
  | { kind: 'UnitBusy' }
  | { kind: 'NotReviewable' }
  | { kind: 'InvalidRating' }
  | { kind: 'InvalidPhone' }
  | { kind: 'InvalidName' }
  | { kind: 'NotOverdue' }
  | { kind: 'InvalidTransition'; from: BookingStatus; action: BookingAction }
  | { kind: 'ValidationFailed'; fields: ItemField[] };

type SimpleKind = Exclude<DomainError['kind'], 'InvalidTransition' | 'ValidationFailed'>;
type Failure = { ok: false; error: DomainError };

export type Outcome<T> = { ok: true; value: T } | Failure;

export const success = <T>(value: T): Outcome<T> => ({ ok: true, value });
export const failure = (error: DomainError): Failure => ({ ok: false, error });
/** Shorthand for errors that carry no data. */
export const fail = (kind: SimpleKind): Failure => failure({ kind } as DomainError);

/** The value of a successful outcome. Throws otherwise; for seed data and tests. */
export function unwrap<T>(o: Outcome<T>): T {
  if (!o.ok) throw new Error(`Expected success, got ${o.error.kind}`);
  return o.value;
}

export function errorMessage(e: DomainError): string {
  switch (e.kind) {
    case 'InvalidDateRange': return 'Please pick a valid date range starting today or later.';
    case 'DatesUnavailable': return 'Some of these dates are fully booked. Try a different range.';
    case 'NoUnitFree': return 'No unit is free for these dates.';
    case 'OwnListing': return "You can't rent your own listing.";
    case 'NotFound': return "We couldn't find that anymore.";
    case 'UnitBusy': return 'This unit has an active or upcoming booking. Return or reassign it first.';
    case 'NotReviewable': return "This rental can't be reviewed.";
    case 'InvalidRating': return 'Choose a rating from 1 to 5 stars.';
    case 'InvalidPhone': return 'Enter a valid 10-digit mobile number.';
    case 'InvalidName': return 'Please enter a name.';
    case 'NotOverdue': return "This rental isn't overdue.";
    case 'InvalidTransition': return "That action isn't available for this booking anymore.";
    case 'ValidationFailed': return 'Please fix the highlighted fields.';
  }
}
