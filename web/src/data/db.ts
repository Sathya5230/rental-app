import Dexie, { type EntityTable, type Table } from 'dexie';
import type { AppNotification, AuditRecord, Booking, Category, HandoverRecord, Item, ItemUnit, Provider, Review, User, Vendor } from '@/domain/models';

export interface Favourite { userId: number; itemId: number }
export interface Meta { key: string; value: string }

export class RentNestDb extends Dexie {
  users!: EntityTable<User, 'id'>;
  providers!: EntityTable<Provider, 'id'>;
  vendors!: EntityTable<Vendor, 'id'>;
  categories!: EntityTable<Category, 'id'>;
  items!: EntityTable<Item, 'id'>;
  units!: EntityTable<ItemUnit, 'id'>;
  bookings!: EntityTable<Booking, 'id'>;
  handovers!: EntityTable<HandoverRecord, 'id'>;
  reviews!: EntityTable<Review, 'id'>;
  notifications!: EntityTable<AppNotification, 'id'>;
  audits!: EntityTable<AuditRecord, 'id'>;
  favourites!: Table<Favourite, [number, number]>;
  meta!: EntityTable<Meta, 'key'>;

  constructor(name = 'rentnest') {
    super(name);
    this.version(1).stores({
      users: '++id',
      providers: '++id, userId',
      vendors: '++id',
      categories: '++id, name',
      items: '++id, providerId, categoryId',
      units: '++id, itemId',
      bookings: '++id, itemId, customerId, status',
      handovers: '++id, bookingId',
      reviews: '++id, itemId, bookingId',
      notifications: '++id, [recipientUserId+audience]',
      audits: '++id, itemId, timestamp',
      favourites: '[userId+itemId], userId',
      meta: 'key',
    });
  }
}

/** A row without its id, so IndexedDB assigns one. An explicit id of 0 would be stored as key 0. */
export function withoutId<T extends { id: number }>(row: T): Omit<T, 'id'> {
  const copy: Partial<T> = { ...row };
  delete copy.id;
  return copy as Omit<T, 'id'>;
}
