import { daysBetween, type IsoDate } from '@/domain/dates';
import type { AuditRecord, Vendor } from '@/domain/models';
import { freeCountOn } from '@/domain/rules/availability';
import { itemStock, type ItemStock } from '@/domain/rules/inventoryMetrics';
import { stockAlertsForItem } from '@/domain/rules/stockAlerts';
import type { ShopSnapshot } from './shop';

export type InventoryFilter = 'ALL' | 'OWNED' | 'BORROWED' | 'NEEDS_AUDIT' | 'LOW_STOCK' | 'MAINTENANCE' | 'INACTIVE';
export const INVENTORY_FILTERS: [InventoryFilter, string][] = [
  ['ALL', 'All'], ['OWNED', 'Owned'], ['BORROWED', 'Borrowed'], ['NEEDS_AUDIT', 'Needs audit'],
  ['LOW_STOCK', 'Low stock'], ['MAINTENANCE', 'Maintenance'], ['INACTIVE', 'Inactive'],
];

/** An item needs counting if it was never audited, the last count was off, or it's been a month. */
export const AUDIT_EVERY_DAYS = 30;

export interface InventoryRow {
  stock: ItemStock;
  categoryName: string;
  availableNow: number;
  lowStock: boolean;
  maintenance: boolean;
  vendorName: string | undefined;
  lastAudit: AuditRecord | undefined;
  needsAudit: boolean;
  /** Days until the borrowed item must go back to its vendor; negative when late. */
  vendorDueInDays: number | undefined;
}

/** Port of InventoryRows.build. */
export function inventoryRows(shop: ShopSnapshot, audits: AuditRecord[], vendors: Vendor[], today: IsoDate, nowMillis: number): InventoryRow[] {
  const cats = new Map(shop.categories.map(c => [c.id, c.name]));
  const vendorNames = new Map(vendors.map(v => [v.id, v.name]));
  const last = new Map<number, AuditRecord>();
  for (const a of audits) if ((last.get(a.itemId)?.timestamp ?? -Infinity) < a.timestamp) last.set(a.itemId, a);
  return shop.items.map(item => {
    const units = shop.units.filter(u => u.itemId === item.id);
    const bookings = shop.bookings.filter(b => b.itemId === item.id);
    const alerts = stockAlertsForItem(item, units, bookings, today);
    const audit = last.get(item.id);
    return {
      stock: itemStock(item, units, bookings),
      categoryName: cats.get(item.categoryId) ?? '',
      availableNow: freeCountOn(today, units, bookings),
      lowStock: alerts.some(a => a.kind === 'LowStock'),
      maintenance: alerts.some(a => a.kind === 'Maintenance'),
      vendorName: item.vendorId != null ? vendorNames.get(item.vendorId) : undefined,
      lastAudit: audit,
      needsAudit: !audit || audit.counted !== audit.expected || nowMillis - audit.timestamp > AUDIT_EVERY_DAYS * 86_400_000,
      vendorDueInDays: item.ownership === 'BORROWED' && item.vendorReturnBy ? daysBetween(today, item.vendorReturnBy) : undefined,
    };
  });
}

export function matchesFilter(r: InventoryRow, f: InventoryFilter): boolean {
  const item = r.stock.item;
  switch (f) {
    case 'ALL': return true;
    case 'OWNED': return item.ownership === 'OWNED';
    case 'BORROWED': return item.ownership === 'BORROWED';
    case 'NEEDS_AUDIT': return r.needsAudit;
    case 'LOW_STOCK': return r.lowStock;
    case 'MAINTENANCE': return r.maintenance;
    case 'INACTIVE': return !item.isActive;
  }
}

export const inventoryCounts = (rows: InventoryRow[]) =>
  Object.fromEntries(INVENTORY_FILTERS.map(([f]) => [f, rows.filter(r => matchesFilter(r, f)).length])) as Record<InventoryFilter, number>;

export function filterInventory(rows: InventoryRow[], f: InventoryFilter, query: string): InventoryRow[] {
  const q = query.trim().toLowerCase();
  return rows.filter(r => matchesFilter(r, f) && (!q || [r.stock.item.title, r.categoryName, r.vendorName ?? ''].some(t => t.toLowerCase().includes(q))));
}
