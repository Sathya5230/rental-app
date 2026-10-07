import { beforeEach, describe, expect, it } from 'vitest';
import { createServices, type Services } from '@/data/services';
import { memoryStore } from '@/data/sessionRepository';
import { seededDb, time, TODAY } from '@/data/testing';
import type { AuditRecord, Vendor } from '@/domain/models';
import { auditLines, auditSessions } from './audit';
import { earnings } from './earnings';
import { filterInventory, inventoryCounts, inventoryRows, type InventoryRow } from './inventory';
import { formFromItem, formToDraft } from './itemForm';
import { loadShop, type ShopSnapshot } from './shop';

let s: Services;
let shop: ShopSnapshot;
let audits: AuditRecord[];
let vendors: Vendor[];
let rows: InventoryRow[];

beforeEach(async () => {
  s = createServices({ db: await seededDb(), time, store: memoryStore(), unlockStore: memoryStore() });
  shop = (await loadShop(s))!;
  audits = await s.inventory.audits();
  vendors = await s.catalog.vendors();
  rows = inventoryRows(shop, audits, vendors, TODAY, time.nowMillis());
});

const row = (id: number) => rows.find(r => r.stock.item.id === id)!;

describe('inventory rows', () => {
  it('flags stock, service and audit state per item', () => {
    expect(rows).toHaveLength(40);
    expect(row(4).availableNow).toBe(0); // its only unit is out
    expect(row(7).maintenance).toBe(true);
    expect(row(1).needsAudit).toBe(false); // counted last week, all found
    expect(row(12).needsAudit).toBe(true); // last count was one short
    expect(row(30).lastAudit).toBeUndefined();
    expect(row(4).vendorName).toBe('LensLoop Rentals');
  });

  it('counts and filters', () => {
    const counts = inventoryCounts(rows);
    expect(counts).toMatchObject({ ALL: 40, OWNED: 30, BORROWED: 10, NEEDS_AUDIT: 21, INACTIVE: 0 });
    expect(filterInventory(rows, 'BORROWED', '').every(r => r.stock.item.ownership === 'BORROWED')).toBe(true);
    expect(filterInventory(rows, 'ALL', 'lensloop').map(r => r.stock.item.id)).toEqual(expect.arrayContaining([4]));
    expect(filterInventory(rows, 'ALL', 'camping')).toHaveLength(5);
  });
});

describe('audit', () => {
  it('expects the units in store and puts items due a count first', () => {
    const lines = auditLines(rows, 'ALL', new Map(), new Map());
    expect(lines).toHaveLength(40);
    expect(lines[0].row.needsAudit).toBe(true);
    const tent = lines.find(l => l.row.stock.item.id === 11)!;
    expect(tent).toMatchObject({ expected: tent.row.stock.inStore, counted: tent.row.stock.inStore, matches: true });
    const short = auditLines(rows, 'ALL', new Map([[11, 1]]), new Map()).find(l => l.row.stock.item.id === 11)!;
    expect(short.matches).toBe(false);
    expect(auditLines(rows, 'BORROWED', new Map(), new Map())).toHaveLength(10);
  });

  it('groups history by count session with mismatches', () => {
    const titles = new Map(shop.items.map(i => [i.id, i.title]));
    const sessions = auditSessions(audits, titles);
    expect(sessions).toHaveLength(1);
    expect(sessions[0].items).toBe(20);
    expect(sessions[0].mismatches.map(m => m.title)).toEqual(['Sleeping Bag (-5°C)']);
  });
});

describe('earnings', () => {
  const titles = () => new Map(shop.items.map(i => [i.id, i.title]));
  it('buckets closed rentals by week', () => {
    const e = earnings('WEEKLY', TODAY, shop.bookings, titles());
    expect(e.bars).toHaveLength(8);
    expect(e.bars.at(-1)!.label).toBe('5 Oct'); // Monday of this week
    expect(e.total).toBe(0);
    expect(e.bars.at(-2)!.amount).toBe(300_000 + 540_000 + 180_000);
    expect(e.changePercent).toBe(-100);
    expect(e.topItems.length).toBeLessThanOrEqual(5);
    const amounts = e.topItems.map(t => t.amount);
    expect(amounts).toEqual([...amounts].sort((a, b) => b - a));
    expect(e.payouts.every(p => !p.pending)).toBe(true);
  });
  it('buckets by month', () => {
    const e = earnings('MONTHLY', TODAY, shop.bookings, titles());
    expect(e.bars.map(b => b.label)).toEqual(['May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct']);
  });
});

describe('item form', () => {
  it('round-trips an item through rupee text fields', () => {
    const item = shop.items.find(i => i.id === 4)!;
    const form = formFromItem(item);
    expect(form).toMatchObject({ daily: '1500', weekly: '8500', deposit: '20000', ownership: 'BORROWED' });
    const draft = formToDraft(form, item.id, 1);
    expect(draft).toMatchObject({ dailyRate: item.dailyRate, weeklyRate: item.weeklyRate, deposit: item.deposit, vendorId: item.vendorId, vendorCostPerDay: item.vendorCostPerDay });
  });
  it('drops vendor details for owned items and treats bad numbers as missing', () => {
    const form = { ...formFromItem(shop.items[3]), ownership: 'OWNED' as const, daily: 'abc' };
    expect(formToDraft(form, 4, 1)).toMatchObject({ vendorId: null, vendorCostPerDay: 0, vendorReturnBy: null, dailyRate: null });
  });
});
