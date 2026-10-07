import type { IsoDate } from '../dates';
import { failure, success, type Outcome } from '../errors';
import type { Item, ItemField, Ownership } from '../models';

export interface ItemDraft {
  id: number;
  providerId: number;
  title: string;
  categoryId: number | null;
  description: string;
  photos: string[];
  dailyRate: number | null;
  weeklyRate: number | null;
  deposit: number | null;
  specs: [string, string][];
  lowStockThreshold: number;
  isActive: boolean;
  unitValue: number | null;
  ownership: Ownership;
  vendorId: number | null;
  vendorCostPerDay: number | null;
  vendorReturnBy: IsoDate | null;
}

export const emptyDraft = (providerId: number, overrides: Partial<ItemDraft> = {}): ItemDraft => ({
  id: 0, providerId, title: '', categoryId: null, description: '', photos: [], dailyRate: null, weeklyRate: null, deposit: 0,
  specs: [], lowStockThreshold: 1, isActive: true, unitValue: 0, ownership: 'OWNED', vendorId: null, vendorCostPerDay: 0, vendorReturnBy: null,
  ...overrides,
});

export function validateItem(d: ItemDraft): ItemField[] {
  const errors: ItemField[] = [];
  if (!d.title.trim()) errors.push('TITLE');
  if (d.categoryId == null) errors.push('CATEGORY');
  if (d.photos.length === 0) errors.push('PHOTOS');
  const daily = d.dailyRate;
  if (daily == null || daily <= 0) errors.push('DAILY_RATE');
  const weekly = d.weeklyRate;
  if (weekly == null || weekly <= 0 || (daily != null && daily > 0 && weekly > daily * 7)) errors.push('WEEKLY_RATE');
  if (d.deposit == null || d.deposit < 0) errors.push('DEPOSIT');
  if (d.lowStockThreshold < 0) errors.push('THRESHOLD');
  if (d.unitValue == null || d.unitValue < 0) errors.push('UNIT_VALUE');
  if (d.ownership === 'BORROWED') {
    if (d.vendorId == null) errors.push('VENDOR');
    if (d.vendorCostPerDay == null || d.vendorCostPerDay < 0) errors.push('VENDOR_COST');
  }
  return errors;
}

export function draftToItem(d: ItemDraft): Outcome<Item> {
  const fields = validateItem(d);
  if (fields.length > 0) return failure({ kind: 'ValidationFailed', fields });
  const borrowed = d.ownership === 'BORROWED';
  return success({
    id: d.id,
    providerId: d.providerId,
    categoryId: d.categoryId!,
    title: d.title.trim(),
    description: d.description.trim(),
    photos: d.photos,
    dailyRate: d.dailyRate!,
    weeklyRate: d.weeklyRate!,
    deposit: d.deposit!,
    specs: d.specs.filter(([k]) => k.trim()).map(([k, v]) => [k.trim(), v.trim()]),
    lowStockThreshold: d.lowStockThreshold,
    isActive: d.isActive,
    unitValue: d.unitValue!,
    ownership: d.ownership,
    vendorId: borrowed ? d.vendorId : null,
    vendorCostPerDay: borrowed ? d.vendorCostPerDay! : 0,
    vendorReturnBy: borrowed ? d.vendorReturnBy : null,
  });
}

export const itemToDraft = (item: Item): ItemDraft => ({ ...item, specs: item.specs.map(([k, v]) => [k, v]) });
