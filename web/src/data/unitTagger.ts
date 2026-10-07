import type { ItemUnit } from '@/domain/models';
import type { RentNestDb } from './db';

/** e.g. "CAM12-03": category prefix, item id, per-item sequence. Units are never deleted, so tags stay unique. */
export const unitTag = (categoryKey: string, itemId: number, sequence: number) =>
  `${categoryKey.slice(0, 3).toUpperCase()}${itemId}-${String(sequence).padStart(2, '0')}`;

/** Adds a new, available unit to an item. Call inside a transaction over items, categories and units. */
export async function insertUnit(db: RentNestDb, itemId: number): Promise<ItemUnit> {
  const item = await db.items.get(itemId);
  if (!item) throw new Error(`Item ${itemId} not found`);
  const key = (await db.categories.get(item.categoryId))?.iconKey ?? 'itm';
  const sequence = (await db.units.where('itemId').equals(itemId).count()) + 1;
  const unit: Omit<ItemUnit, 'id'> = { itemId, tag: unitTag(key, itemId, sequence), condition: 'NEW', status: 'AVAILABLE' };
  const id = await db.units.add(unit);
  return { ...unit, id };
}
