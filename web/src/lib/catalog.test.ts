import { beforeEach, describe, expect, it } from 'vitest';
import { BookingRepository } from '@/data/bookingRepository';
import { CatalogRepository } from '@/data/catalogRepository';
import { InventoryRepository } from '@/data/inventoryRepository';
import { seededDb, time, TODAY } from '@/data/testing';
import { addDays } from '@/domain/dates';
import type { Booking, ItemUnit } from '@/domain/models';
import { loadSnapshot, NO_FILTERS, popular, search, summarize, type CatalogSnapshot } from './catalog';

let snap: CatalogSnapshot;
let units: ItemUnit[];
let bookings: Booking[];

beforeEach(async () => {
  const db = await seededDb();
  snap = await loadSnapshot(new CatalogRepository(db));
  units = await new InventoryRepository(db, time).allUnits();
  bookings = await new BookingRepository(db, time).allBookings();
});

const ids = (list: { item: { id: number } }[]) => list.map(s => s.item.id);

describe('summaries', () => {
  it('joins category, store, rating and favourite', () => {
    expect(summarize(snap, snap.items[3])).toMatchObject({ categoryName: 'Cameras', providerName: 'RentNest Store', isFavourite: true });
    expect(summarize(snap, snap.items[0]).rating?.count).toBe(3);
    expect(summarize(snap, snap.items[4]).rating).toBeUndefined();
  });

  it('popular items are the most booked, best rated first', () => {
    const top = popular(snap);
    expect(top).toHaveLength(8);
    const counts = top.map(s => snap.bookingCounts.get(s.item.id) ?? 0);
    expect(counts).toEqual([...counts].sort((a, b) => b - a));
  });
});

describe('search', () => {
  const run = (query: string, f = NO_FILTERS, sort: Parameters<typeof search>[3] = 'RELEVANCE') => search(snap, query, f, sort, units, bookings);

  it('matches title, description, category and store name', () => {
    expect(ids(run('dome tent'))).toEqual([11]);
    expect(ids(run('himalayan'))).toEqual([12]);
    expect(run('camping').length).toBeGreaterThanOrEqual(5);
    expect(run('rentnest store')).toHaveLength(40);
  });

  it('ranks title matches before description matches', () => {
    // "Camping Stove & Cookset" matches by title; the other camping items only by category or description.
    const results = run('camping');
    expect(results[0].item.title).toBe('Camping Stove & Cookset');
    expect(results.slice(1).every(s => !s.item.title.toLowerCase().includes('camping'))).toBe(true);
    expect(results.length).toBeGreaterThan(1);
  });

  it('filters by category, price and rating', () => {
    expect(run('', { ...NO_FILTERS, categoryId: 3 }).every(s => s.item.categoryId === 3)).toBe(true);
    expect(run('', { ...NO_FILTERS, categoryId: 3 })).toHaveLength(5);
    expect(run('', { ...NO_FILTERS, maxPrice: 30_000 }).every(s => s.item.dailyRate <= 30_000)).toBe(true);
    expect(run('', { ...NO_FILTERS, minRating: 4.5 }).every(s => (s.rating?.average ?? 0) >= 4.5)).toBe(true);
  });

  it('filters to items free on the chosen dates', () => {
    // Item 4 has one unit, out on an ACTIVE rental until today+2
    const busy = { ...NO_FILTERS, dates: { start: addDays(TODAY, 1), end: addDays(TODAY, 1) } };
    const free = { ...NO_FILTERS, dates: { start: addDays(TODAY, 3), end: addDays(TODAY, 5) } };
    expect(ids(run('', busy))).not.toContain(4);
    expect(ids(run('', free))).toContain(4);
  });

  it('sorts by price and rating', () => {
    const low = run('', NO_FILTERS, 'PRICE_LOW').map(s => s.item.dailyRate);
    expect(low).toEqual([...low].sort((a, b) => a - b));
    const high = run('', NO_FILTERS, 'PRICE_HIGH').map(s => s.item.dailyRate);
    expect(high).toEqual([...high].sort((a, b) => b - a));
    const rated = run('', NO_FILTERS, 'RATING').map(s => s.rating?.average ?? 0);
    expect(rated).toEqual([...rated].sort((a, b) => b - a));
  });
});
