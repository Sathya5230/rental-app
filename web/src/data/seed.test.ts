import { describe, expect, it } from 'vitest';
import { bookingRange } from '@/domain/models';
import { overlaps } from '@/domain/dates';
import { buildSeed } from './seed/seedData';
import { ensureSeeded, resetDemoData } from './seed/demoData';
import { seededDb, testDb, time, TODAY } from './testing';

describe('seed', () => {
  const s = buildSeed(TODAY, time.nowMillis());

  it('has the demo catalogue', () => {
    expect(s.items).toHaveLength(40);
    expect(s.categories).toHaveLength(8);
    expect(s.bookings).toHaveLength(25);
    expect(s.units[0].tag).toBe('CAM1-01');
    expect(s.items.every(i => s.units.some(u => u.itemId === i.id))).toBe(true);
  });

  it('never double-books a unit', () => {
    const reserving = s.bookings.filter(b => b.status === 'ACCEPTED' || b.status === 'ACTIVE');
    for (const b of reserving) {
      expect(b.unitId).not.toBeNull();
      expect(s.units.find(u => u.id === b.unitId)!.itemId).toBe(b.itemId);
      const clash = reserving.some(o => o.id !== b.id && o.unitId === b.unitId && overlaps(bookingRange(o), bookingRange(b)));
      expect(clash, `unit double booked: ${b.id}`).toBe(false);
    }
  });

  it('seeding twice keeps existing data', async () => {
    const db = await seededDb();
    await db.items.update(1, { title: 'Changed' });
    await ensureSeeded(db, time);
    expect((await db.items.get(1))!.title).toBe('Changed');
    expect(await db.items.count()).toBe(40);
  });

  it('reseeds when the seed version changes', async () => {
    const db = await seededDb();
    await db.items.update(1, { title: 'Changed' });
    await db.meta.put({ key: 'seedVersion', value: '0' });
    await ensureSeeded(db, time);
    expect((await db.items.get(1))!.title).toBe('Sony A7 IV Mirrorless Kit');
  });

  it('reset restores the demo data', async () => {
    const db = testDb();
    await ensureSeeded(db, time);
    await db.bookings.clear();
    await resetDemoData(db, time);
    expect(await db.bookings.count()).toBe(25);
  });
});
