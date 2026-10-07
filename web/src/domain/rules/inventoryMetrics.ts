import type { Booking, Item, ItemUnit } from '../models';

/** Per-item stock and money figures for the inventory screen. */
export interface ItemStock {
  item: Item;
  /** Units the store holds, whatever their state (excludes retired). */
  units: number;
  /** Units out with customers right now. */
  out: number;
  /** Units in maintenance. */
  inService: number;
  inStore: number;
  worth: number;
  /** Earnings per day if every rentable unit were out. */
  dailyPotential: number;
  /** Earnings per day from units out right now. */
  dailyEarning: number;
  dailyVendorCost: number;
}

export interface InventoryTotals {
  products: number;
  units: number;
  unitsOut: number;
  unitsInStore: number;
  unitsInService: number;
  ownedWorth: number;
  borrowedWorth: number;
  totalWorth: number;
  dailyPotential: number;
  dailyEarning: number;
  dailyVendorCost: number;
  /** Today's rental income minus what borrowed stock costs per day. */
  netDaily: number;
  borrowedProducts: number;
}

export function itemStock(item: Item, units: ItemUnit[], bookings: Booking[]): ItemStock {
  const held = units.filter(u => u.status !== 'RETIRED');
  const heldIds = new Set(held.map(u => u.id));
  const out = new Set(bookings.filter(b => b.status === 'ACTIVE' && b.unitId != null && heldIds.has(b.unitId)).map(b => b.unitId)).size;
  const inService = held.filter(u => u.status === 'MAINTENANCE').length;
  return {
    item, units: held.length, out, inService,
    inStore: held.length - out,
    worth: item.unitValue * held.length,
    dailyPotential: item.dailyRate * (held.length - inService),
    dailyEarning: item.dailyRate * out,
    dailyVendorCost: item.ownership === 'BORROWED' ? item.vendorCostPerDay * held.length : 0,
  };
}

export function inventoryTotals(stocks: ItemStock[]): InventoryTotals {
  const sum = (list: ItemStock[], f: (s: ItemStock) => number) => list.reduce((acc, s) => acc + f(s), 0);
  const borrowed = stocks.filter(s => s.item.ownership === 'BORROWED');
  const owned = stocks.filter(s => s.item.ownership !== 'BORROWED');
  const ownedWorth = sum(owned, s => s.worth);
  const borrowedWorth = sum(borrowed, s => s.worth);
  const dailyEarning = sum(stocks, s => s.dailyEarning);
  const dailyVendorCost = sum(stocks, s => s.dailyVendorCost);
  return {
    products: stocks.length,
    units: sum(stocks, s => s.units),
    unitsOut: sum(stocks, s => s.out),
    unitsInStore: sum(stocks, s => s.inStore),
    unitsInService: sum(stocks, s => s.inService),
    ownedWorth, borrowedWorth, totalWorth: ownedWorth + borrowedWorth,
    dailyPotential: sum(stocks, s => s.dailyPotential),
    dailyEarning, dailyVendorCost, netDaily: dailyEarning - dailyVendorCost,
    borrowedProducts: borrowed.length,
  };
}
