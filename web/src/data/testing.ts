import { fixedTime } from '@/domain/time';
import { RentNestDb } from './db';
import { ensureSeeded } from './seed/demoData';

export const TODAY = '2026-10-07';
export const time = fixedTime(TODAY);

export const testDb = () => new RentNestDb(`test-${crypto.randomUUID()}`);

export async function seededDb() {
  const db = testDb();
  await ensureSeeded(db, time);
  return db;
}
