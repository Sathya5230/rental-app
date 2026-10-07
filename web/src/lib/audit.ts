import type { AuditRecord } from '@/domain/models';
import type { InventoryRow } from './inventory';

export type AuditScope = 'ALL' | 'OWNED' | 'BORROWED';
export const AUDIT_SCOPES: [AuditScope, string][] = [['ALL', 'All items'], ['OWNED', 'Owned'], ['BORROWED', 'Borrowed']];

export interface AuditLine {
  row: InventoryRow;
  /** Units that should be on the shelves: held units minus those out with customers. */
  expected: number;
  counted: number;
  notes: string;
  matches: boolean;
}

/** Port of AuditViewModel's lines: items due a count first, then by title. Counts default to what's expected. */
export function auditLines(rows: InventoryRow[], scope: AuditScope, counted: Map<number, number>, notes: Map<number, string>): AuditLine[] {
  return rows
    .filter(r => scope === 'ALL' || r.stock.item.ownership === scope)
    .sort((a, b) => Number(b.needsAudit) - Number(a.needsAudit) || a.stock.item.title.localeCompare(b.stock.item.title))
    .map(row => {
      const expected = row.stock.inStore;
      const c = counted.get(row.stock.item.id) ?? expected;
      return { row, expected, counted: c, notes: notes.get(row.stock.item.id) ?? '', matches: c === expected };
    });
}

/** One past audit: every record saved with the same timestamp. */
export interface AuditSession { timestamp: number; items: number; mismatches: { title: string; record: AuditRecord }[] }

export function auditSessions(audits: AuditRecord[], titles: Map<number, string>, limit = 10): AuditSession[] {
  const byTime = new Map<number, AuditRecord[]>();
  for (const a of audits) byTime.set(a.timestamp, [...(byTime.get(a.timestamp) ?? []), a]);
  return [...byTime]
    .map(([timestamp, records]) => ({
      timestamp,
      items: records.length,
      mismatches: records.filter(r => r.counted !== r.expected).map(record => ({ title: titles.get(record.itemId) ?? '', record })),
    }))
    .sort((a, b) => b.timestamp - a.timestamp)
    .slice(0, limit);
}
