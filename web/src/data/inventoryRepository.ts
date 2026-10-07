import { fail, success, type Outcome } from '@/domain/errors';
import type { ItemUnit } from '@/domain/models';
import type { TimeProvider } from '@/domain/time';
import type { RentNestDb } from './db';
import { insertUnit } from './unitTagger';

export interface AuditEntry { itemId: number; expected: number; counted: number; notes: string }

export class InventoryRepository {
  constructor(private db: RentNestDb, private time: TimeProvider) {}

  unitsForItem(itemId: number) { return this.db.units.where('itemId').equals(itemId).sortBy('id'); }
  allUnits() { return this.db.units.toArray(); }

  async unitsForProvider(providerId: number) {
    const itemIds = new Set(await this.db.items.where('providerId').equals(providerId).primaryKeys());
    return (await this.db.units.orderBy('id').toArray()).filter(u => itemIds.has(u.itemId));
  }

  addUnit(itemId: number): Promise<ItemUnit> {
    return this.db.transaction('rw', [this.db.items, this.db.categories, this.db.units], () => insertUnit(this.db, itemId));
  }

  /** Refuses to take a unit out of service while it's out with, or promised to, a customer. */
  updateUnit(unit: ItemUnit): Promise<Outcome<ItemUnit>> {
    return this.db.transaction('rw', [this.db.units, this.db.bookings], async () => {
      const current = await this.db.units.get(unit.id);
      if (!current) return fail('NotFound');
      if (current.status === 'AVAILABLE' && unit.status !== 'AVAILABLE') {
        const today = this.time.today();
        const busy = (await this.db.bookings.where('itemId').equals(current.itemId).toArray()).some(
          b => b.unitId === unit.id && (b.status === 'ACTIVE' || (b.status === 'ACCEPTED' && b.endDate >= today)),
        );
        if (busy) return fail('UnitBusy');
      }
      await this.db.units.put(unit);
      return success(unit);
    });
  }

  async audits() {
    return (await this.db.audits.toArray()).sort((a, b) => b.timestamp - a.timestamp || a.itemId - b.itemId);
  }

  /** Records one stock count per entry, all with the same timestamp. */
  async submitAudit(entries: AuditEntry[]): Promise<Outcome<number>> {
    if (entries.length === 0) return success(0);
    const timestamp = this.time.nowMillis();
    await this.db.audits.bulkAdd(entries.map(e => ({ itemId: e.itemId, timestamp, expected: e.expected, counted: Math.max(0, e.counted), notes: e.notes.trim() })));
    return success(entries.length);
  }
}
