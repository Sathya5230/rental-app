import { beforeEach, describe, expect, it } from 'vitest';
import { createServices, type Services } from '@/data/services';
import { memoryStore } from '@/data/sessionRepository';
import { seededDb, time, TODAY } from '@/data/testing';
import { addDays } from '@/domain/dates';
import { dashboardStats, groupProviderBookings, loadShop, providerRows, type ShopSnapshot } from './shop';

let s: Services;
let shop: ShopSnapshot;

beforeEach(async () => {
  s = createServices({ db: await seededDb(), time, store: memoryStore(), unlockStore: memoryStore() });
  shop = (await loadShop(s))!;
});

describe('shop snapshot', () => {
  it('loads the admin store with every item, unit and booking', () => {
    expect(shop.provider.shopName).toBe('RentNest Store');
    expect(shop.items).toHaveLength(40);
    expect(shop.bookings).toHaveLength(25);
    expect(shop.userNames.get(9)).toBe('Vikram Nair');
  });
});

describe('dashboard', () => {
  it('counts what is due today', () => {
    const d = dashboardStats(TODAY, shop.items, shop.units, shop.bookings, shop.userNames);
    expect(d).toMatchObject({ pendingRequests: 3, activeRentals: 5, pickupsToday: 1, returnsToday: 2 });
    expect(d.schedule.map(e => e.kind)).toEqual(['OVERDUE', 'PICKUP', 'RETURN']);
    expect(d.overdue[0]).toMatchObject({ customerName: 'Vikram Nair', lateFee: 2 * 50_000 });
    expect(d.utilisationPercent).toBeGreaterThan(0);
    expect(d.utilisationPercent).toBeLessThanOrEqual(100);
  });

  it('sums rentals closed in the last 7 days, damage included', () => {
    // Only the Golf Club Set (3 days at ₹1,000) closed in that window.
    expect(dashboardStats(TODAY, shop.items, shop.units, shop.bookings, shop.userNames).weekEarnings).toBe(300_000);
  });

  it('flags stock alerts and borrowed items due back soon', () => {
    const items = shop.items.map(i => (i.id === 4 ? { ...i, vendorReturnBy: addDays(TODAY, -1) } : i));
    const d = dashboardStats(TODAY, items, shop.units, shop.bookings, shop.userNames);
    expect(d.vendorReturns).toEqual([{ item: expect.objectContaining({ id: 4 }), dueInDays: -1 }]);
    expect(d.alerts).toContainEqual(expect.objectContaining({ kind: 'Maintenance', itemId: 7 }));
  });
});

describe('admin bookings', () => {
  it('works out free units, late fees and tabs', () => {
    const rows = providerRows(shop, TODAY);
    expect(rows).toHaveLength(25);
    const request = rows.find(r => r.booking.status === 'REQUESTED' && r.booking.itemId === 1)!;
    expect(request.customerName).toBe('Ishaan Verma');
    expect(request.freeUnits.map(u => u.tag)).toEqual(['CAM1-01', 'CAM1-02']);
    const overdue = rows.filter(r => r.isOverdue);
    expect(overdue).toHaveLength(1);
    expect(overdue[0]).toMatchObject({ customerName: 'Vikram Nair', lateFee: 100_000, unitTag: expect.stringMatching(/^VEH33-/) });

    const tabs = groupProviderBookings(rows);
    expect(tabs.REQUESTS).toHaveLength(3);
    expect(tabs.REQUESTS.map(r => r.booking.startDate)).toEqual([...tabs.REQUESTS.map(r => r.booking.startDate)].sort());
    expect(tabs.ACTIVE).toHaveLength(5);
    const done = tabs.COMPLETED.map(r => r.booking.endDate);
    expect(done).toEqual([...done].sort().reverse());
  });
});
