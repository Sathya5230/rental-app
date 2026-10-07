import type { Services } from '@/data/services';
import { addDays, daysBetween, type IsoDate } from '@/domain/dates';
import { ADMIN_USER_ID, bookingRange, type Booking, type BookingStatus, type Category, type Item, type ItemUnit, type Provider } from '@/domain/models';
import { freeUnitsFor } from '@/domain/rules/availability';
import { isOverdue, lateFee } from '@/domain/rules/lateFees';
import { stockAlertsForItem, type StockAlert } from '@/domain/rules/stockAlerts';

/** Everything about the admin's store, in one read. Port of ObserveShop. */
export interface ShopSnapshot {
  provider: Provider;
  items: Item[];
  units: ItemUnit[];
  bookings: Booking[];
  userNames: Map<number, string>;
  categories: Category[];
}

export async function loadShop(s: Pick<Services, 'catalog' | 'inventory' | 'bookings'>): Promise<ShopSnapshot | null> {
  const provider = await s.catalog.providerForUser(ADMIN_USER_ID);
  if (!provider) return null;
  const [items, units, bookings, users, categories] = await Promise.all([
    s.catalog.itemsByProvider(provider.id), s.inventory.unitsForProvider(provider.id), s.bookings.bookingsForProvider(provider.id),
    s.catalog.users(), s.catalog.categories(),
  ]);
  return { provider, items, units, bookings, userNames: new Map(users.map(u => [u.id, u.name])), categories };
}

export type ScheduleKind = 'OVERDUE' | 'PICKUP' | 'RETURN';
export interface ScheduleEntry { booking: Booking; itemTitle: string; customerName: string; kind: ScheduleKind; lateFee: number }
/** A borrowed item that must go back to its vendor soon (or already should have). */
export interface VendorReturn { item: Item; dueInDays: number }

export interface DashboardStats {
  pickupsToday: number;
  returnsToday: number;
  activeRentals: number;
  pendingRequests: number;
  utilisationPercent: number;
  weekEarnings: number;
  alerts: StockAlert[];
  schedule: ScheduleEntry[];
  overdue: ScheduleEntry[];
  vendorReturns: VendorReturn[];
}

export const VENDOR_RETURN_WARN_DAYS = 3;
const KIND_ORDER: ScheduleKind[] = ['OVERDUE', 'PICKUP', 'RETURN'];

/** Port of DashboardCalculator.compute. */
export function dashboardStats(today: IsoDate, items: Item[], units: ItemUnit[], bookings: Booking[], names: Map<number, string>): DashboardStats {
  const byId = new Map(items.map(i => [i.id, i]));
  const entry = (b: Booking, kind: ScheduleKind, fee = 0): ScheduleEntry =>
    ({ booking: b, itemTitle: byId.get(b.itemId)?.title ?? '', customerName: names.get(b.customerId) ?? '', kind, lateFee: fee });
  const pickups = bookings.filter(b => b.status === 'ACCEPTED' && b.startDate <= today);
  const active = bookings.filter(b => b.status === 'ACTIVE');
  const returns = active.filter(b => b.endDate <= today);
  const usable = units.filter(u => u.status === 'AVAILABLE').length;
  const inUse = new Set(active.flatMap(b => (b.unitId != null ? [b.unitId] : []))).size;
  const schedule = [
    ...returns.map(b => entry(b, b.endDate < today ? 'OVERDUE' : 'RETURN', lateFee(b.endDate, today, byId.get(b.itemId)?.dailyRate ?? 0))),
    ...pickups.map(b => entry(b, 'PICKUP')),
  ].sort((a, b) => KIND_ORDER.indexOf(a.kind) - KIND_ORDER.indexOf(b.kind));
  const weekStart = addDays(today, -6);
  return {
    pickupsToday: pickups.length,
    returnsToday: returns.length,
    activeRentals: active.length,
    pendingRequests: bookings.filter(b => b.status === 'REQUESTED').length,
    utilisationPercent: usable === 0 ? 0 : Math.floor((inUse * 100) / usable),
    weekEarnings: bookings.filter(b => b.status === 'RETURNED' && b.endDate >= weekStart && b.endDate <= today).reduce((sum, b) => sum + b.subtotal + b.damageFee, 0),
    alerts: items.flatMap(i => stockAlertsForItem(i, units.filter(u => u.itemId === i.id), bookings.filter(b => b.itemId === i.id), today)),
    schedule,
    overdue: schedule.filter(e => e.kind === 'OVERDUE'),
    vendorReturns: items
      .flatMap(i => (i.ownership === 'BORROWED' && i.vendorReturnBy ? [{ item: i, dueInDays: daysBetween(today, i.vendorReturnBy) }] : []))
      .filter(v => v.dueInDays <= VENDOR_RETURN_WARN_DAYS)
      .sort((a, b) => a.dueInDays - b.dueInDays),
  };
}

export type ProviderTab = 'REQUESTS' | 'UPCOMING' | 'ACTIVE' | 'COMPLETED';
export const PROVIDER_TABS: [ProviderTab, string, string][] = [
  ['REQUESTS', 'Requests', 'Rental requests waiting for your approval appear here.'],
  ['UPCOMING', 'Upcoming', 'Confirmed bookings waiting for pickup.'],
  ['ACTIVE', 'Active', 'Items currently out with customers.'],
  ['COMPLETED', 'Done', 'Closed, declined and cancelled rentals.'],
];

export function providerTabOf(s: BookingStatus): ProviderTab {
  switch (s) {
    case 'REQUESTED': return 'REQUESTS';
    case 'ACCEPTED': return 'UPCOMING';
    case 'ACTIVE': return 'ACTIVE';
    default: return 'COMPLETED';
  }
}

export interface ProviderBookingRow {
  booking: Booking;
  item: Item | undefined;
  customerName: string;
  /** Units that could take a pending request. */
  freeUnits: ItemUnit[];
  unitTag: string | undefined;
  isOverdue: boolean;
  /** Late fee accrued so far for an overdue rental, or charged on a closed one. */
  lateFee: number;
}

/** Port of ProviderBookingsViewModel's row building. */
export function providerRows(shop: ShopSnapshot, today: IsoDate): ProviderBookingRow[] {
  const items = new Map(shop.items.map(i => [i.id, i]));
  const tags = new Map(shop.units.map(u => [u.id, u.tag]));
  return shop.bookings.map(b => {
    const freeUnits = b.status === 'REQUESTED'
      ? freeUnitsFor(bookingRange(b), shop.units.filter(u => u.itemId === b.itemId), shop.bookings.filter(o => o.itemId === b.itemId && o.id !== b.id))
      : [];
    const overdue = isOverdue(b, today);
    return {
      booking: b, item: items.get(b.itemId), customerName: shop.userNames.get(b.customerId) ?? 'Customer', freeUnits,
      unitTag: b.unitId != null ? tags.get(b.unitId) : undefined, isOverdue: overdue,
      lateFee: overdue ? lateFee(b.endDate, today, items.get(b.itemId)?.dailyRate ?? 0) : b.lateFee,
    };
  });
}

/** Done tab newest first; the rest soonest first. */
export function groupProviderBookings(rows: ProviderBookingRow[]): Record<ProviderTab, ProviderBookingRow[]> {
  const out: Record<ProviderTab, ProviderBookingRow[]> = { REQUESTS: [], UPCOMING: [], ACTIVE: [], COMPLETED: [] };
  for (const r of rows) out[providerTabOf(r.booking.status)].push(r);
  out.COMPLETED.sort((a, b) => b.booking.endDate.localeCompare(a.booking.endDate));
  for (const t of ['REQUESTS', 'UPCOMING', 'ACTIVE'] as const) out[t].sort((a, b) => a.booking.startDate.localeCompare(b.booking.startDate));
  return out;
}
