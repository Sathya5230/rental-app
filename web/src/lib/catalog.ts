import type { CatalogRepository } from '@/data/catalogRepository';
import type { DateRange } from '@/domain/dates';
import { DEMO_USER_ID, type Booking, type Category, type Item, type ItemUnit, type Provider, type RatingSummary } from '@/domain/models';
import { isBookable } from '@/domain/rules/availability';

/** An item with what browsing screens show next to it. Port of ui/model/ItemSummary.kt. */
export interface ItemSummary {
  item: Item;
  rating: RatingSummary | undefined;
  providerName: string;
  isFavourite: boolean;
  categoryName: string;
}

export interface CatalogSnapshot {
  categories: Category[];
  items: Item[];
  providers: Map<number, Provider>;
  ratings: Map<number, RatingSummary>;
  bookingCounts: Map<number, number>;
  favourites: Set<number>;
}

/** Everything the customer browsing screens need, read in one live query. Active items only. */
export async function loadSnapshot(catalog: CatalogRepository): Promise<CatalogSnapshot> {
  const [categories, items, providers, ratings, bookingCounts, favourites] = await Promise.all([
    catalog.categories(), catalog.activeItems(), catalog.providers(), catalog.ratingSummaries(), catalog.bookingCounts(), catalog.favouriteIds(DEMO_USER_ID),
  ]);
  return { categories, items, providers: new Map(providers.map(p => [p.id, p])), ratings, bookingCounts, favourites };
}

export function summarize(snap: CatalogSnapshot, item: Item): ItemSummary {
  return {
    item,
    rating: snap.ratings.get(item.id),
    providerName: snap.providers.get(item.providerId)?.shopName ?? '',
    isFavourite: snap.favourites.has(item.id),
    categoryName: snap.categories.find(c => c.id === item.categoryId)?.name ?? '',
  };
}

/** The home screen's "Popular near you": most booked, then best rated. */
export function popular(snap: CatalogSnapshot, limit = 8): ItemSummary[] {
  const count = (i: Item) => snap.bookingCounts.get(i.id) ?? 0;
  const avg = (i: Item) => snap.ratings.get(i.id)?.average ?? 0;
  return [...snap.items].sort((a, b) => count(b) - count(a) || avg(b) - avg(a)).slice(0, limit).map(i => summarize(snap, i));
}

export type SortOption = 'RELEVANCE' | 'PRICE_LOW' | 'PRICE_HIGH' | 'RATING';
export const SORT_LABELS: Record<SortOption, string> = {
  RELEVANCE: 'Popular', PRICE_LOW: 'Price: low to high', PRICE_HIGH: 'Price: high to low', RATING: 'Top rated',
};

export interface SearchFilters {
  categoryId: number | null;
  providerId: number | null;
  /** Paise per day. */
  maxPrice: number | null;
  minRating: number | null;
  dates: DateRange | null;
}

export const NO_FILTERS: SearchFilters = { categoryId: null, providerId: null, maxPrice: null, minRating: null, dates: null };

export const activeFilterCount = (f: SearchFilters) => Object.values(f).filter(v => v != null).length;

/** Port of SearchLogic.filter. */
export function search(snap: CatalogSnapshot, query: string, f: SearchFilters, sort: SortOption, units: ItemUnit[], bookings: Booking[]): ItemSummary[] {
  const q = query.trim().toLowerCase();
  const matched = snap.items.map(i => summarize(snap, i)).filter(s => {
    const item = s.item;
    return (!q || [item.title, item.description, s.categoryName, s.providerName].some(t => t.toLowerCase().includes(q))) &&
      (f.categoryId == null || item.categoryId === f.categoryId) &&
      (f.providerId == null || item.providerId === f.providerId) &&
      (f.maxPrice == null || item.dailyRate <= f.maxPrice) &&
      (f.minRating == null || (s.rating?.average ?? 0) >= f.minRating) &&
      (f.dates == null || isBookable(f.dates, units.filter(u => u.itemId === item.id), bookings.filter(b => b.itemId === item.id)));
  });
  const titleHit = (s: ItemSummary) => (q && s.item.title.toLowerCase().includes(q) ? 1 : 0);
  const count = (s: ItemSummary) => snap.bookingCounts.get(s.item.id) ?? 0;
  const avg = (s: ItemSummary) => s.rating?.average ?? 0;
  switch (sort) {
    case 'RELEVANCE': return matched.sort((a, b) => titleHit(b) - titleHit(a) || count(b) - count(a));
    case 'PRICE_LOW': return matched.sort((a, b) => a.item.dailyRate - b.item.dailyRate);
    case 'PRICE_HIGH': return matched.sort((a, b) => b.item.dailyRate - a.item.dailyRate);
    case 'RATING': return matched.sort((a, b) => avg(b) - avg(a));
  }
}
