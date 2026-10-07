import { addDays, type IsoDate } from '@/domain/dates';
import { unwrap } from '@/domain/errors';
import { formatRange, formatShort } from '@/domain/format/dates';
import {
  ADMIN_USER_ID, DEMO_USER_ID, bookingRange,
  type AppNotification, type Audience, type AuditRecord, type Booking, type BookingStatus, type Category, type Item,
  type ItemUnit, type Provider, type Review, type UnitCondition, type UnitStatus, type User, type Vendor,
} from '@/domain/models';
import { quoteItem } from '@/domain/rules/pricing';
import type { Favourite } from '../db';
import { unitTag } from '../unitTagger';
import { REVIEW_TEXTS, SEED_CATEGORIES, SEED_ITEMS } from './catalog';

export interface SeedBundle {
  users: User[];
  providers: Provider[];
  vendors: Vendor[];
  categories: Category[];
  items: Item[];
  units: ItemUnit[];
  bookings: Booking[];
  reviews: Review[];
  favourites: Favourite[];
  notifications: AppNotification[];
  audits: AuditRecord[];
}

const DAY = 86_400_000;

interface BookingSpec { item: number; customer: number; from: number; to: number; status: BookingStatus; reviewed?: boolean; damage?: number }
const B = (item: number, customer: number, from: number, to: number, status: BookingStatus, extra: { reviewed?: boolean; damage?: number } = {}): BookingSpec =>
  ({ item, customer, from, to, status, ...extra });

const BOOKING_SPECS: BookingSpec[] = [
  // The demo customer's rentals
  B(2, 1, -20, -17, 'RETURNED', { reviewed: true }),
  B(3, 1, -10, -8, 'RETURNED'),
  B(4, 1, -2, 2, 'ACTIVE'),
  B(5, 1, 3, 5, 'ACCEPTED'),
  B(8, 1, 6, 8, 'REQUESTED'),
  B(9, 1, -5, -4, 'CANCELLED'),
  B(10, 1, 1, 2, 'DECLINED'),
  // Other customers
  B(1, 7, 2, 4, 'REQUESTED'),
  B(7, 8, 1, 3, 'REQUESTED'),
  B(13, 9, 0, 2, 'ACCEPTED'),
  B(19, 7, -3, 0, 'ACTIVE'),
  B(25, 8, -1, 4, 'ACTIVE'),
  B(31, 9, -14, -12, 'RETURNED', { reviewed: true }),
  B(37, 7, -30, -25, 'RETURNED', { reviewed: true, damage: 1_500 }),
  B(1, 8, -9, -7, 'RETURNED', { reviewed: true }),
  B(7, 9, -21, -19, 'RETURNED'),
  B(13, 7, -45, -40, 'RETURNED'),
  B(19, 8, -60, -58, 'RETURNED'),
  B(25, 9, -5, -3, 'RETURNED'),
  B(31, 8, -100, -96, 'RETURNED'),
  B(2, 8, -40, -38, 'RETURNED'),
  B(16, 9, -15, -14, 'RETURNED'),
  B(21, 7, -1, 1, 'ACTIVE'),
  B(27, 8, 4, 6, 'ACCEPTED'),
  // Overdue: should have come back two days ago
  B(33, 9, -6, -2, 'ACTIVE'),
];

const ASSIGNED: BookingStatus[] = ['ACCEPTED', 'ACTIVE', 'RETURNED'];

/**
 * Deterministic demo data: one store run by the admin, stocking its own items plus some borrowed from vendors.
 * Booking dates are relative to [today] so the dashboard always looks alive.
 */
export function buildSeed(today: IsoDate, now: number): SeedBundle {
  const users: User[] = [
    { id: 1, name: 'Arjun Mehta', phone: '+91 98450 12001', isProvider: true },
    { id: 2, name: 'Kavya Rao', phone: '+91 98450 12002', isProvider: true },
    { id: 3, name: 'Rohan Das', phone: '+91 98450 12003', isProvider: true },
    { id: 4, name: 'Meera Iyer', phone: '+91 98450 12004', isProvider: true },
    { id: 5, name: 'Farhan Ali', phone: '+91 98450 12005', isProvider: true },
    { id: 6, name: 'Neha Kapoor', phone: '+91 98450 12006', isProvider: true },
    { id: 7, name: 'Ishaan Verma', phone: '+91 98450 12007', isProvider: false },
    { id: 8, name: 'Ananya Singh', phone: '+91 98450 12008', isProvider: false },
    { id: 9, name: 'Vikram Nair', phone: '+91 98450 12009', isProvider: false },
    { id: ADMIN_USER_ID, name: 'Admin', phone: '+91 98450 10000', isProvider: true },
  ];
  const providers: Provider[] = [
    { id: 1, userId: ADMIN_USER_ID, shopName: 'RentNest Store', rating: 4.8, reviewCount: 968, locationText: 'Indiranagar, Bengaluru', joinedDate: '2023-03-12' },
  ];
  const vendors: Vendor[] = [
    { id: 1, name: 'LensLoop Rentals', phone: '+91 98450 22001' },
    { id: 2, name: 'ToolShed Co.', phone: '+91 98450 22002' },
    { id: 3, name: 'WildTrail Outfitters', phone: '+91 98450 22003' },
    { id: 4, name: 'PartyPal Events', phone: '+91 98450 22004' },
    { id: 5, name: 'RideOn Rentals', phone: '+91 98450 22005' },
  ];
  const categories: Category[] = SEED_CATEGORIES.map(([name, iconKey], i) => ({ id: i + 1, name, iconKey }));

  const items: Item[] = SEED_ITEMS.map((tpl, index) => {
    const key = SEED_CATEGORIES[Math.floor(index / 5)][1];
    // Every fourth item is borrowed from a vendor, at 40% of its daily rate.
    const borrowed = index % 4 === 3;
    return {
      id: index + 1, providerId: 1, categoryId: Math.floor(index / 5) + 1, title: tpl.title, description: tpl.description,
      photos: [0, 1, 2].map(k => `${key}:${(index + k) % 4}`),
      dailyRate: tpl.daily * 100, weeklyRate: tpl.weekly * 100, deposit: tpl.deposit * 100, specs: tpl.specs,
      lowStockThreshold: 1, isActive: true, unitValue: tpl.daily * 60 * 100,
      ownership: borrowed ? 'BORROWED' : 'OWNED',
      vendorId: borrowed ? (Math.floor(index / 8) % vendors.length) + 1 : null,
      vendorCostPerDay: borrowed ? tpl.daily * 40 : 0,
      vendorReturnBy: borrowed ? addDays(today, 2 + index) : null,
    };
  });

  const units: ItemUnit[] = [];
  const firstUnit = new Map<number, number>();
  for (const item of items) {
    const key = categories[item.categoryId - 1].iconKey;
    const count = 1 + (item.id % 4);
    for (let n = 1; n <= count; n++) {
      const id = units.length + 1;
      if (n === 1) firstUnit.set(item.id, id);
      let condition: UnitCondition = n === 1 ? (item.id % 3 === 0 ? 'NEW' : 'GOOD') : n === 3 ? 'FAIR' : 'GOOD';
      let status: UnitStatus = 'AVAILABLE';
      if (item.id === 7 && n === count) { condition = 'DAMAGED'; status = 'MAINTENANCE'; }
      if (item.id === 22 && n === 3) status = 'RETIRED';
      units.push({ id, itemId: item.id, tag: unitTag(key, item.id, n), condition, status });
    }
  }

  const bookings: Booking[] = BOOKING_SPECS.map((s, i) => {
    const range = { start: addDays(today, s.from), end: addDays(today, s.to) };
    const q = unwrap(quoteItem(items[s.item - 1], range));
    return {
      id: i + 1, itemId: s.item, unitId: ASSIGNED.includes(s.status) ? firstUnit.get(s.item)! : null,
      customerId: s.customer, startDate: range.start, endDate: range.end, status: s.status,
      subtotal: q.subtotal, deposit: q.deposit, damageFee: (s.damage ?? 0) * 100,
      createdAt: now - (i + 1) * 3 * 3_600_000, reviewed: s.reviewed ?? false,
      contactPhone: users.find(u => u.id === s.customer)!.phone,
      lateFee: 0, overdueSmsAt: null, pickupTransportFee: 0, dropTransportFee: 0, cleaningFee: 0,
    };
  });

  const reviews: Review[] = [];
  const addReview = (r: Omit<Review, 'id'>) => reviews.push({ ...r, id: reviews.length + 1 });
  for (const item of items) {
    if (item.id % 5 === 0) continue;
    const n = 1 + (item.id % 3);
    for (let k = 0; k < n; k++) {
      addReview({
        itemId: item.id, bookingId: null, customerId: 7 + ((item.id + k) % 3),
        rating: [5, 4, 5, 5, 4, 3, 5][(item.id + k) % 7],
        text: REVIEW_TEXTS[(item.id * 3 + k) % REVIEW_TEXTS.length],
        createdAt: now - (item.id * (k + 1) + 2) * DAY,
      });
    }
  }
  for (const b of bookings.filter(b => b.reviewed)) {
    addReview({ itemId: b.itemId, bookingId: b.id, customerId: b.customerId, rating: 5, text: REVIEW_TEXTS[b.id % REVIEW_TEXTS.length], createdAt: now - DAY });
  }

  const title = (id: number) => items[id - 1].title;
  const booking = (item: number, status: BookingStatus) => bookings.find(b => b.itemId === item && b.status === status)!;
  const notifications: AppNotification[] = [];
  const notify = (audience: Audience, t: string, body: string, bookingId: number | null, isRead: boolean, createdAt: number) =>
    notifications.push({
      id: notifications.length + 1, recipientUserId: audience === 'ADMIN' ? ADMIN_USER_ID : DEMO_USER_ID, audience,
      title: t, body, bookingId, isRead, createdAt,
    });
  const req1 = booking(1, 'REQUESTED');
  notify('ADMIN', 'New rental request', `Ishaan Verma wants ${title(1)} · ${formatRange(bookingRange(req1))}`, req1.id, false, now - 3_600_000);
  notify('ADMIN', 'New rental request', `Ananya Singh wants ${title(7)}`, booking(7, 'REQUESTED').id, false, now - 7_200_000);
  notify('ADMIN', 'New rental request', `Arjun Mehta wants ${title(8)}`, booking(8, 'REQUESTED').id, false, now - 9_000_000);
  notify('ADMIN', 'Return due today', `${title(19)} is due back from Ishaan Verma today.`, booking(19, 'ACTIVE').id, false, now - 10_800_000);
  notify('ADMIN', 'New 5★ review', `${title(1)}: "Exactly as described."`, null, true, now - 2 * DAY);
  notify('CUSTOMER', 'Request approved', `${title(5)} is reserved for you. Pickup in 3 days.`, booking(5, 'ACCEPTED').id, false, now - 5_400_000);
  notify('CUSTOMER', 'Rental started', `Enjoy your ${title(4)}! Return by ${formatShort(addDays(today, 2))}.`, booking(4, 'ACTIVE').id, true, now - 2 * DAY);
  notify('CUSTOMER', 'How was it?', `Rate your ${title(3)} rental to help other renters.`, booking(3, 'RETURNED').id, false, now - 8 * DAY);

  const favourites: Favourite[] = [4, 11, 21, 26].map(itemId => ({ userId: DEMO_USER_ID, itemId }));

  // Last week's stock count of the first 20 items: one unit of item 12 couldn't be found.
  const auditAt = now - 7 * DAY;
  const audits: AuditRecord[] = items.slice(0, 20).map((item, i) => {
    const held = units.filter(u => u.itemId === item.id && u.status !== 'RETIRED').length;
    const missing = item.id === 12 ? 1 : 0;
    return { id: i + 1, itemId: item.id, timestamp: auditAt, expected: held, counted: held - missing, notes: missing > 0 ? 'One unit missing from shelf B' : '' };
  });

  return { users, providers, vendors, categories, items, units, bookings, reviews, favourites, notifications, audits };
}
