import { fail, success, type Outcome } from '@/domain/errors';
import { displayPhone } from '@/domain/format/phone';
import type { Category, RatingSummary, User, Vendor } from '@/domain/models';
import { draftToItem, type ItemDraft } from '@/domain/rules/itemValidator';
import { withoutId, type RentNestDb } from './db';
import { insertUnit } from './unitTagger';

export class CatalogRepository {
  constructor(private db: RentNestDb) {}

  categories() { return this.db.categories.orderBy('id').toArray(); }
  async activeItems() { return (await this.allItems()).filter(i => i.isActive); }
  allItems() { return this.db.items.orderBy('id').toArray(); }
  item(id: number) { return this.db.items.get(id); }
  async itemsByProvider(providerId: number) { return (await this.db.items.where('providerId').equals(providerId).sortBy('id')).reverse(); }
  async providers() { return (await this.db.providers.toArray()).sort((a, b) => b.rating - a.rating); }
  providerForUser(userId: number) { return this.db.providers.where('userId').equals(userId).first(); }
  async reviewsForItem(itemId: number) { return (await this.db.reviews.where('itemId').equals(itemId).toArray()).sort((a, b) => b.createdAt - a.createdAt); }
  user(id: number) { return this.db.users.get(id); }
  users() { return this.db.users.toArray(); }

  async ratingSummaries(): Promise<Map<number, RatingSummary>> {
    const sums = new Map<number, { total: number; count: number }>();
    await this.db.reviews.each(r => {
      const s = sums.get(r.itemId) ?? { total: 0, count: 0 };
      sums.set(r.itemId, { total: s.total + r.rating, count: s.count + 1 });
    });
    return new Map([...sums].map(([id, s]) => [id, { average: s.total / s.count, count: s.count }]));
  }

  /** Bookings per item, ignoring declined and cancelled ones. */
  async bookingCounts(): Promise<Map<number, number>> {
    const counts = new Map<number, number>();
    await this.db.bookings.each(b => {
      if (b.status === 'DECLINED' || b.status === 'CANCELLED') return;
      counts.set(b.itemId, (counts.get(b.itemId) ?? 0) + 1);
    });
    return counts;
  }

  async favouriteIds(userId: number) {
    return new Set((await this.db.favourites.where('userId').equals(userId).toArray()).map(f => f.itemId));
  }

  async toggleFavourite(userId: number, itemId: number) {
    await this.db.transaction('rw', this.db.favourites, async () => {
      const key: [number, number] = [userId, itemId];
      if (await this.db.favourites.get(key)) await this.db.favourites.delete(key);
      else await this.db.favourites.add({ userId, itemId });
    });
  }

  /** Creates (id 0, also adds a first unit) or updates an item. */
  async saveItem(draft: ItemDraft): Promise<Outcome<number>> {
    const r = draftToItem(draft);
    if (!r.ok) return r;
    const item = r.value;
    return this.db.transaction('rw', [this.db.items, this.db.categories, this.db.units], async () => {
      if (item.id === 0) {
        const id = await this.db.items.add(withoutId(item));
        await insertUnit(this.db, id);
        return success(id);
      }
      await this.db.items.put(item);
      return success(item.id);
    });
  }

  /** Adds a category, or returns the existing one with the same name. */
  async addCategory(name: string): Promise<Outcome<Category>> {
    const clean = name.trim().replace(/\s+/g, ' ');
    if (!clean) return fail('InvalidName');
    return this.db.transaction('rw', this.db.categories, async () => {
      const existing = await this.db.categories.where('name').equalsIgnoreCase(clean).first();
      if (existing) return success(existing);
      const iconKey = clean.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'other';
      const id = await this.db.categories.add({ name: clean, iconKey });
      return success({ id, name: clean, iconKey });
    });
  }

  async vendors() { return (await this.db.vendors.toArray()).sort((a, b) => a.name.localeCompare(b.name)); }

  async addVendor(name: string, phone: string): Promise<Outcome<Vendor>> {
    if (!name.trim()) return fail('InvalidName');
    const display = phone.trim() ? displayPhone(phone) : '';
    if (display === null) return fail('InvalidPhone');
    const vendor = { name: name.trim(), phone: display };
    const id = await this.db.vendors.add(vendor);
    return success({ ...vendor, id });
  }

  async updatePhone(userId: number, phone: string): Promise<Outcome<User>> {
    const display = displayPhone(phone);
    if (!display) return fail('InvalidPhone');
    return this.db.transaction('rw', this.db.users, async () => {
      const user = await this.db.users.get(userId);
      if (!user) return fail('NotFound');
      const updated = { ...user, phone: display };
      await this.db.users.put(updated);
      return success(updated);
    });
  }
}
