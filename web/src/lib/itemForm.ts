import type { IsoDate } from '@/domain/dates';
import { moneyToInput, parseRupees } from '@/domain/format/money';
import type { Item, Ownership } from '@/domain/models';
import type { ItemDraft } from '@/domain/rules/itemValidator';

/** The item editor's fields as typed: money stays text until saved. Port of EditorForm. */
export interface ItemForm {
  title: string;
  categoryId: number | null;
  description: string;
  photos: string[];
  daily: string;
  weekly: string;
  deposit: string;
  threshold: number;
  specs: [string, string][];
  isActive: boolean;
  unitValue: string;
  ownership: Ownership;
  vendorId: number | null;
  vendorCost: string;
  vendorReturnBy: IsoDate | null;
}

export const EMPTY_FORM: ItemForm = {
  title: '', categoryId: null, description: '', photos: [], daily: '', weekly: '', deposit: '0', threshold: 1, specs: [],
  isActive: true, unitValue: '', ownership: 'OWNED', vendorId: null, vendorCost: '', vendorReturnBy: null,
};

export const formFromItem = (i: Item): ItemForm => ({
  title: i.title, categoryId: i.categoryId, description: i.description, photos: i.photos,
  daily: moneyToInput(i.dailyRate), weekly: moneyToInput(i.weeklyRate), deposit: moneyToInput(i.deposit),
  threshold: i.lowStockThreshold, specs: i.specs.map(([k, v]) => [k, v]), isActive: i.isActive, unitValue: moneyToInput(i.unitValue),
  ownership: i.ownership, vendorId: i.vendorId, vendorCost: moneyToInput(i.vendorCostPerDay), vendorReturnBy: i.vendorReturnBy,
});

export function formToDraft(f: ItemForm, id: number, providerId: number): ItemDraft {
  const borrowed = f.ownership === 'BORROWED';
  const orZero = (v: string) => parseRupees(v.trim() === '' ? '0' : v);
  return {
    id, providerId, title: f.title, categoryId: f.categoryId, description: f.description, photos: f.photos,
    dailyRate: parseRupees(f.daily), weeklyRate: parseRupees(f.weekly), deposit: parseRupees(f.deposit), specs: f.specs,
    lowStockThreshold: f.threshold, isActive: f.isActive, unitValue: orZero(f.unitValue), ownership: f.ownership,
    vendorId: borrowed ? f.vendorId : null, vendorCostPerDay: borrowed ? orZero(f.vendorCost) : 0, vendorReturnBy: borrowed ? f.vendorReturnBy : null,
  };
}
