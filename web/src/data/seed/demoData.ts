import type { TimeProvider } from '@/domain/time';
import type { RentNestDb } from '../db';
import { buildSeed } from './seedData';

/** Bump when the seed or schema changes, so browsers with older demo data reseed. */
export const SEED_VERSION = '1';
const SEED_KEY = 'seedVersion';

async function clearAll(db: RentNestDb) {
  await Promise.all(db.tables.map(t => t.clear()));
}

async function insertSeed(db: RentNestDb, time: TimeProvider) {
  const s = buildSeed(time.today(), time.nowMillis());
  await db.users.bulkAdd(s.users);
  await db.providers.bulkAdd(s.providers);
  await db.vendors.bulkAdd(s.vendors);
  await db.categories.bulkAdd(s.categories);
  await db.items.bulkAdd(s.items);
  await db.units.bulkAdd(s.units);
  await db.bookings.bulkAdd(s.bookings);
  await db.reviews.bulkAdd(s.reviews);
  await db.favourites.bulkAdd(s.favourites);
  await db.notifications.bulkAdd(s.notifications);
  await db.audits.bulkAdd(s.audits);
  await db.meta.put({ key: SEED_KEY, value: SEED_VERSION });
}

/** Seeds an empty database, or reseeds one written by an older seed version. */
export async function ensureSeeded(db: RentNestDb, time: TimeProvider) {
  await db.transaction('rw', db.tables, async () => {
    const version = (await db.meta.get(SEED_KEY))?.value;
    if (version === SEED_VERSION && (await db.users.count()) > 0) return;
    await clearAll(db);
    await insertSeed(db, time);
  });
}

/** Restores the demo data. Run it before each client demo. */
export async function resetDemoData(db: RentNestDb, time: TimeProvider) {
  await db.transaction('rw', db.tables, async () => {
    await clearAll(db);
    await insertSeed(db, time);
  });
}
