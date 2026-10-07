import type { SessionState } from '@/data/sessionRepository';
import type { IsoDate } from '@/domain/dates';
import type { AppMode } from '@/domain/models';

/** Where the app opens for this session: the Android NavHost's start destination. */
export function startPath(s: SessionState): string {
  if (!s.onboarded) return '/onboarding';
  if (!s.loggedIn) return '/login';
  if (!s.modeChosen) return '/choose-mode';
  return s.mode === 'CUSTOMER' ? '/home' : '/admin';
}

export const paths = {
  home: '/home',
  rentals: '/rentals',
  dashboard: '/admin',
  adminBookings: '/admin/bookings',
  search: (o: { category?: number; provider?: number } = {}) => {
    const q = new URLSearchParams();
    if (o.category != null) q.set('category', String(o.category));
    if (o.provider != null) q.set('provider', String(o.provider));
    const s = q.toString();
    return s ? `/search?${s}` : '/search';
  },
  item: (id: number) => `/item/${id}`,
  book: (id: number) => `/book/${id}`,
  checkout: (itemId: number, start: IsoDate, end: IsoDate) => `/checkout?item=${itemId}&start=${start}&end=${end}`,
  bookingSuccess: (id: number) => `/booking-success/${id}`,
  bill: (id: number, isReturn: boolean, mode: AppMode) => (mode === 'ADMIN' ? `/admin/bill/${id}${isReturn ? '?return=1' : ''}` : `/rentals/${id}/bill`),
  handover: (id: number, isReturn: boolean) => `/admin/handover/${id}${isReturn ? '?return=1' : ''}`,
  itemEditor: (id?: number) => (id ? `/admin/items/${id}` : '/admin/items/new'),
};

/** A route id segment as a positive integer, or null for anything else ("abc", "12abc", "0"). */
export function parseId(raw: string | undefined): number | null {
  if (!raw || !/^[1-9]\d{0,14}$/.test(raw)) return null;
  return Number(raw);
}
