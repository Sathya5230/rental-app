import type { DateRange, IsoDate } from './dates';

export type UnitCondition = 'NEW' | 'GOOD' | 'FAIR' | 'DAMAGED';
export const UNIT_CONDITIONS: UnitCondition[] = ['NEW', 'GOOD', 'FAIR', 'DAMAGED'];
export type UnitStatus = 'AVAILABLE' | 'MAINTENANCE' | 'RETIRED';
export const UNIT_STATUSES: UnitStatus[] = ['AVAILABLE', 'MAINTENANCE', 'RETIRED'];
export type BookingStatus = 'REQUESTED' | 'ACCEPTED' | 'ACTIVE' | 'RETURNED' | 'DECLINED' | 'CANCELLED';
export const BOOKING_STATUSES: BookingStatus[] = ['REQUESTED', 'ACCEPTED', 'ACTIVE', 'RETURNED', 'DECLINED', 'CANCELLED'];
export type BookingAction = 'ACCEPT' | 'DECLINE' | 'CANCEL' | 'CHECK_OUT' | 'RETURN';
export const BOOKING_ACTIONS: BookingAction[] = ['ACCEPT', 'DECLINE', 'CANCEL', 'CHECK_OUT', 'RETURN'];
export type HandoverType = 'PICKUP' | 'RETURN';
export type Audience = 'CUSTOMER' | 'ADMIN';
/** Whether the store owns an item or has borrowed it from a vendor. */
export type Ownership = 'OWNED' | 'BORROWED';
export type ItemField =
  | 'TITLE' | 'CATEGORY' | 'PHOTOS' | 'DAILY_RATE' | 'WEEKLY_RATE' | 'DEPOSIT' | 'THRESHOLD' | 'UNIT_VALUE' | 'VENDOR' | 'VENDOR_COST';
export type AppMode = 'CUSTOMER' | 'ADMIN';
export type ThemePref = 'SYSTEM' | 'LIGHT' | 'DARK';

/** The signed-in demo customer. */
export const DEMO_USER_ID = 1;
/** The store admin. Owns the shop, receives booking requests and runs the admin dashboard. */
export const ADMIN_USER_ID = 100;
/** Admin PIN until the admin changes it from their profile. */
export const DEFAULT_ADMIN_PIN = '1234';

export interface User { id: number; name: string; phone: string; isProvider: boolean }

export interface Provider {
  id: number;
  userId: number;
  shopName: string;
  rating: number;
  reviewCount: number;
  locationText: string;
  joinedDate: IsoDate;
}

export interface Vendor { id: number; name: string; phone: string }

/** iconKey doubles as the art key used by ItemArt (e.g. "cameras"). */
export interface Category { id: number; name: string; iconKey: string }

/** All money is integer paise. */
export interface Item {
  id: number;
  providerId: number;
  categoryId: number;
  title: string;
  description: string;
  /** Data URLs of uploaded photos, or "category:variant" placeholder keys drawn by ItemArt. */
  photos: string[];
  dailyRate: number;
  weeklyRate: number;
  deposit: number;
  specs: [string, string][];
  lowStockThreshold: number;
  isActive: boolean;
  /** Purchase or replacement value of one unit, used for inventory net worth. */
  unitValue: number;
  ownership: Ownership;
  vendorId: number | null;
  /** What the store pays the vendor per unit per day for a borrowed item. */
  vendorCostPerDay: number;
  vendorReturnBy: IsoDate | null;
}

export interface ItemUnit { id: number; itemId: number; tag: string; condition: UnitCondition; status: UnitStatus }

export interface Booking {
  id: number;
  itemId: number;
  unitId: number | null;
  customerId: number;
  startDate: IsoDate;
  endDate: IsoDate;
  status: BookingStatus;
  subtotal: number;
  deposit: number;
  damageFee: number;
  createdAt: number;
  reviewed: boolean;
  contactPhone: string;
  lateFee: number;
  /** When the customer was last sent an overdue SMS, or null if never. */
  overdueSmsAt: number | null;
  /** Charged at pickup, for delivering the item to the customer. */
  pickupTransportFee: number;
  /** Charged at return, for collecting the item back from the customer. */
  dropTransportFee: number;
  /** Charged at return, for cleaning or servicing the item before it's listed again. */
  cleaningFee: number;
}

export const bookingRange = (b: Pick<Booking, 'startDate' | 'endDate'>): DateRange => ({ start: b.startDate, end: b.endDate });
export const bookingTotal = (b: Pick<Booking, 'subtotal' | 'deposit'>) => b.subtotal + b.deposit;

export interface HandoverRecord {
  id: number;
  bookingId: number;
  type: HandoverType;
  checklist: string[];
  conditionAfter: UnitCondition;
  notes: string;
  damageFee: number;
  timestamp: number;
}

export interface Review {
  id: number;
  itemId: number;
  bookingId: number | null;
  customerId: number;
  rating: number;
  text: string;
  createdAt: number;
}

export interface AppNotification {
  id: number;
  recipientUserId: number;
  audience: Audience;
  title: string;
  body: string;
  bookingId: number | null;
  isRead: boolean;
  createdAt: number;
}

/** One stock count of an item: units expected in the store vs. units physically found. */
export interface AuditRecord { id: number; itemId: number; timestamp: number; expected: number; counted: number; notes: string }
export const auditMatches = (a: AuditRecord) => a.expected === a.counted;

export interface RatingSummary { average: number; count: number }
