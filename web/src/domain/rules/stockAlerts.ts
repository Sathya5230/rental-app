import { addDays, rangeDates, type IsoDate } from '../dates';
import type { Booking, Item, ItemUnit } from '../models';
import { freeCountOn } from './availability';

export type StockAlert =
  | { kind: 'LowStock'; itemId: number; itemTitle: string; minFree: number; date: IsoDate }
  | { kind: 'Maintenance'; itemId: number; itemTitle: string; unitTags: string[] };

export const STOCK_WINDOW_DAYS = 7;

export function stockAlertsForItem(item: Item, units: ItemUnit[], bookings: Booking[], today: IsoDate): StockAlert[] {
  if (!item.isActive) return [];
  const alerts: StockAlert[] = [];
  let worst = { date: today, free: Infinity };
  for (const date of rangeDates({ start: today, end: addDays(today, STOCK_WINDOW_DAYS - 1) })) {
    const free = freeCountOn(date, units, bookings);
    if (free < worst.free) worst = { date, free };
  }
  if (worst.free <= item.lowStockThreshold) {
    alerts.push({ kind: 'LowStock', itemId: item.id, itemTitle: item.title, minFree: worst.free, date: worst.date });
  }
  const attention = units.filter(u => u.status === 'MAINTENANCE' || (u.condition === 'DAMAGED' && u.status !== 'RETIRED'));
  if (attention.length > 0) alerts.push({ kind: 'Maintenance', itemId: item.id, itemTitle: item.title, unitTags: attention.map(u => u.tag) });
  return alerts;
}
