import { beforeEach, describe, expect, it } from 'vitest';
import { addDays } from '@/domain/dates';
import { failure, unwrap } from '@/domain/errors';
import { ADMIN_USER_ID, DEMO_USER_ID } from '@/domain/models';
import { emptyDraft } from '@/domain/rules/itemValidator';
import { CatalogRepository } from './catalogRepository';
import type { RentNestDb } from './db';
import { InventoryRepository } from './inventoryRepository';
import { NotificationRepository } from './notificationRepository';
import { seededDb, time, TODAY } from './testing';

let db: RentNestDb;
let catalog: CatalogRepository;
let inventory: InventoryRepository;
let notifications: NotificationRepository;

beforeEach(async () => {
  db = await seededDb();
  catalog = new CatalogRepository(db);
  inventory = new InventoryRepository(db, time);
  notifications = new NotificationRepository(db);
});

describe('catalog', () => {
  it('every item belongs to the admin store', async () => {
    const store = (await catalog.providerForUser(ADMIN_USER_ID))!;
    expect((await catalog.allItems()).every(i => i.providerId === store.id)).toBe(true);
  });

  it('saving a new item adds its first unit', async () => {
    const draft = emptyDraft(1, { title: 'Tripod', categoryId: 1, photos: ['data:image/jpeg;base64,x'], dailyRate: 20_000, weeklyRate: 100_000 });
    const id = unwrap(await catalog.saveItem(draft));
    expect(id).toBe(41);
    const units = await inventory.unitsForItem(id);
    expect(units.map(u => u.tag)).toEqual([`CAM${id}-01`]);
    expect((await catalog.saveItem({ ...draft, title: '' })).ok).toBe(false);
    expect((await catalog.saveItem({ ...draft, photos: [] })).ok).toBe(false);
  });

  it('saving an existing item updates it in place', async () => {
    const item = (await catalog.item(11))!;
    unwrap(await catalog.saveItem({ ...emptyDraft(1), ...item, title: 'Dome Tent XL' }));
    expect((await catalog.item(11))!.title).toBe('Dome Tent XL');
    expect(await inventory.unitsForItem(11)).toHaveLength(4);
  });

  it('a borrowed item keeps vendor details', async () => {
    const vendor = unwrap(await catalog.addVendor('Hill Gear', '98450 33333'));
    expect(vendor.phone).toBe('+91 98450 33333');
    const id = unwrap(await catalog.saveItem(emptyDraft(1, {
      title: 'Snow boots', categoryId: 5, photos: ['data:image/jpeg;base64,x'], dailyRate: 10_000, weeklyRate: 50_000,
      unitValue: 600_000, ownership: 'BORROWED', vendorId: vendor.id, vendorCostPerDay: 4_000, vendorReturnBy: addDays(TODAY, 10),
    })));
    expect(await catalog.item(id)).toMatchObject({ ownership: 'BORROWED', vendorId: vendor.id, vendorCostPerDay: 4_000, vendorReturnBy: addDays(TODAY, 10), unitValue: 600_000 });
  });

  it('adding a category reuses an existing name', async () => {
    const drones = unwrap(await catalog.addCategory('  Drones '));
    expect(drones).toMatchObject({ name: 'Drones', iconKey: 'drones' });
    expect(unwrap(await catalog.addCategory('drones')).id).toBe(drones.id);
    expect(unwrap(await catalog.addCategory('cameras')).id).toBe(1);
    expect(await catalog.addCategory('   ')).toEqual(failure({ kind: 'InvalidName' }));
  });

  it('updating a phone normalises it', async () => {
    expect(unwrap(await catalog.updatePhone(DEMO_USER_ID, '9900011122')).phone).toBe('+91 99000 11122');
    expect(await catalog.updatePhone(DEMO_USER_ID, '000')).toEqual(failure({ kind: 'InvalidPhone' }));
  });

  it('favourites toggle', async () => {
    expect(await catalog.favouriteIds(DEMO_USER_ID)).toEqual(new Set([4, 11, 21, 26]));
    await catalog.toggleFavourite(DEMO_USER_ID, 4);
    await catalog.toggleFavourite(DEMO_USER_ID, 5);
    expect(await catalog.favouriteIds(DEMO_USER_ID)).toEqual(new Set([5, 11, 21, 26]));
  });

  it('summarises ratings and counts live bookings', async () => {
    const ratings = await catalog.ratingSummaries();
    expect(ratings.has(5)).toBe(false); // no seeded reviews for every fifth item
    expect(ratings.get(1)!.count).toBe(3); // two seeded + one from a reviewed booking
    const counts = await catalog.bookingCounts();
    expect(counts.get(1)).toBe(2);
    expect(counts.has(9)).toBe(false); // only a cancelled booking
  });
});

describe('inventory', () => {
  it('refuses to take a reserved unit out of service', async () => {
    const [reserved, spare] = await inventory.unitsForItem(13); // the first is reserved by a seeded ACCEPTED booking
    expect(await inventory.updateUnit({ ...reserved, status: 'MAINTENANCE' })).toEqual(failure({ kind: 'UnitBusy' }));
    expect((await inventory.updateUnit({ ...reserved, condition: 'FAIR' })).ok).toBe(true);
    expect((await inventory.updateUnit({ ...spare, status: 'RETIRED' })).ok).toBe(true);
  });

  it('adds units with the next tag', async () => {
    const unit = await inventory.addUnit(11); // item 11 is camping ("CAM") and already has 4 units
    expect(unit.tag).toBe('CAM11-05');
  });

  it('stores one audit record per item', async () => {
    const before = (await inventory.audits()).length;
    expect(await inventory.submitAudit([{ itemId: 1, expected: 2, counted: 2, notes: '' }, { itemId: 2, expected: 3, counted: 1, notes: ' two missing ' }])).toEqual({ ok: true, value: 2 });
    const saved = await inventory.audits();
    expect(saved).toHaveLength(before + 2);
    const mismatch = saved.find(a => a.itemId === 2 && a.timestamp === time.nowMillis())!;
    expect(mismatch.counted).not.toBe(mismatch.expected);
    expect(mismatch.notes).toBe('two missing');
  });
});

describe('notifications', () => {
  it('counts unread and marks all read', async () => {
    expect(await notifications.unreadCount(ADMIN_USER_ID, 'ADMIN')).toBe(4);
    const list = await notifications.notifications(ADMIN_USER_ID, 'ADMIN');
    expect(list[0].createdAt).toBeGreaterThan(list[1].createdAt);
    await notifications.markAllRead(ADMIN_USER_ID, 'ADMIN');
    expect(await notifications.unreadCount(ADMIN_USER_ID, 'ADMIN')).toBe(0);
    expect(await notifications.unreadCount(DEMO_USER_ID, 'CUSTOMER')).toBe(2);
  });
});
