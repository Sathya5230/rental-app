import { systemTime, type TimeProvider } from '@/domain/time';
import { BookingRepository } from './bookingRepository';
import { CatalogRepository } from './catalogRepository';
import { RentNestDb } from './db';
import { InventoryRepository } from './inventoryRepository';
import { NotificationRepository } from './notificationRepository';
import { OverdueReminders } from './overdueReminders';
import { ensureSeeded } from './seed/demoData';
import { safeStorage, SessionRepository, type KeyValueStore } from './sessionRepository';

export interface Services {
  db: RentNestDb;
  time: TimeProvider;
  catalog: CatalogRepository;
  inventory: InventoryRepository;
  bookings: BookingRepository;
  notifications: NotificationRepository;
  session: SessionRepository;
  reminders: OverdueReminders;
}

export function createServices(o: { db: RentNestDb; time: TimeProvider; store: KeyValueStore; unlockStore: KeyValueStore }): Services {
  const catalog = new CatalogRepository(o.db);
  const bookings = new BookingRepository(o.db, o.time);
  return {
    db: o.db,
    time: o.time,
    catalog,
    bookings,
    inventory: new InventoryRepository(o.db, o.time),
    notifications: new NotificationRepository(o.db),
    session: new SessionRepository(o.store, o.unlockStore),
    reminders: new OverdueReminders(bookings, catalog, o.time),
  };
}

export function createBrowserServices(): Services {
  return createServices({
    db: new RentNestDb(),
    time: systemTime,
    store: safeStorage(() => window.localStorage),
    unlockStore: safeStorage(() => window.sessionStorage),
  });
}

export const STORAGE_BLOCKED_MESSAGE =
  "This browser is blocking site storage, so RentNest can't keep your demo data. Open it in a normal (not private) window, or allow site data for this page.";

/** Opens and seeds the database. Reports blocked storage instead of leaving the app on its splash screen. */
export async function bootstrap(db: RentNestDb, time: TimeProvider): Promise<{ ok: true } | { ok: false; message: string }> {
  try {
    await ensureSeeded(db, time);
    return { ok: true };
  } catch (e) {
    console.error('RentNest storage failed', e);
    return { ok: false, message: STORAGE_BLOCKED_MESSAGE };
  }
}
