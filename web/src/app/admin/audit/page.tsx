'use client';

import { CircleCheck, Minus, Plus, TriangleAlert } from 'lucide-react';
import { useState } from 'react';
import { useLive, useServices } from '@/app/providers';
import { Button, IconButton } from '@/components/Button';
import { SkeletonList } from '@/components/EmptyState';
import { inputClass } from '@/components/Field';
import { ItemArt } from '@/components/ItemArt';
import { Screen } from '@/components/Screen';
import { useToast } from '@/components/Toast';
import { localToday } from '@/domain/dates';
import { errorMessage } from '@/domain/errors';
import { formatFull, formatRelative } from '@/domain/format/dates';
import { AUDIT_SCOPES, auditLines, auditSessions, type AuditLine, type AuditScope, type AuditSession } from '@/lib/audit';
import { inventoryRows } from '@/lib/inventory';
import { loadShop } from '@/lib/shop';

export default function Audit() {
  const { inventory } = useServices();
  const toast = useToast();
  const [scope, setScope] = useState<AuditScope>('ALL');
  const [counted, setCounted] = useState(new Map<number, number>());
  const [notes, setNotes] = useState(new Map<number, string>());
  const [showHistory, setShowHistory] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const data = useLive(async s => {
    const shop = await loadShop(s);
    if (!shop) return null;
    const audits = await s.inventory.audits();
    return {
      rows: inventoryRows(shop, audits, await s.catalog.vendors(), s.time.today(), s.time.nowMillis()),
      history: auditSessions(audits, new Map(shop.items.map(i => [i.id, i.title]))),
      now: s.time.nowMillis(),
    };
  });

  const lines = data ? auditLines(data.rows, scope, counted, notes) : [];
  const off = lines.filter(l => !l.matches).length;

  const submit = async () => {
    if (submitting || lines.length === 0) return;
    setSubmitting(true);
    const r = await inventory.submitAudit(lines.map(l => ({ itemId: l.row.stock.item.id, expected: l.expected, counted: l.counted, notes: l.notes })));
    setSubmitting(false);
    setCounted(new Map());
    setNotes(new Map());
    toast(r.ok ? (off === 0 ? `Audit saved: all ${r.value} items match.` : `Audit saved: ${off} of ${r.value} items don't match.`) : errorMessage(r.error));
  };

  const footer = showHistory ? undefined : (
    <div>
      <p className={`mb-2 text-sm ${off === 0 ? 'text-on-surface-variant' : 'text-error'}`}>
        {off === 0 ? `All ${lines.length} items match what should be in the store.` : `${off} of ${lines.length} items don't match. Add a note to explain.`}
      </p>
      <Button className="w-full" disabled={lines.length === 0} loading={submitting} onClick={submit}>Save audit ({lines.length} items)</Button>
    </div>
  );

  return (
    <Screen title="Stock audit" wide footer={footer}
      actions={<Button variant="text" onClick={() => setShowHistory(!showHistory)}>{showHistory ? 'Count' : 'History'}</Button>}>
      {!data ? <SkeletonList /> : showHistory ? <History sessions={data.history} now={data.now} /> : (
        <div className="flex flex-col gap-2.5">
          <p className="text-sm text-on-surface-variant">Count what&apos;s physically in the store. Units out with customers are already excluded.</p>
          <div role="radiogroup" aria-label="Scope" className="grid grid-cols-3 overflow-hidden rounded-full border border-outline">
            {AUDIT_SCOPES.map(([s, label], i) => (
              <button key={s} type="button" role="radio" aria-checked={scope === s} onClick={() => setScope(s)}
                className={`h-10 text-sm font-medium ${i > 0 ? 'border-l border-outline' : ''} ${scope === s ? 'bg-secondary-container text-on-secondary-container' : ''}`}>{label}</button>
            ))}
          </div>
          <div className="grid gap-2.5 lg:grid-cols-2">
            {lines.map(l => (
              <AuditCard key={l.row.stock.item.id} line={l} now={data.now}
                onCount={n => setCounted(new Map(counted).set(l.row.stock.item.id, Math.max(0, n)))}
                onNotes={t => setNotes(new Map(notes).set(l.row.stock.item.id, t))} />
            ))}
          </div>
        </div>
      )}
    </Screen>
  );
}

function AuditCard({ line, now, onCount, onNotes }: { line: AuditLine; now: number; onCount: (n: number) => void; onNotes: (t: string) => void }) {
  const { row } = line;
  const meta = [
    row.vendorName ? `Borrowed from ${row.vendorName}` : 'Owned',
    row.stock.out > 0 ? `${row.stock.out} out` : null,
    row.lastAudit ? `last counted ${formatRelative(row.lastAudit.timestamp, now)}` : 'never counted',
  ].filter(Boolean).join(' · ');
  return (
    <article className={`flex flex-col gap-2 rounded-md p-3 ${line.matches ? 'bg-surface-low' : 'bg-error-container text-on-error-container'}`}>
      <div className="flex items-center gap-3">
        <ItemArt photoKey={row.stock.item.photos[0] ?? ''} iconSize={22} className="size-[52px] shrink-0 rounded-sm" />
        <div>
          <h3 className="font-display text-sm font-semibold">{row.stock.item.title}</h3>
          <p className="text-xs opacity-80">{meta}</p>
        </div>
      </div>
      <div className="flex items-center gap-1">
        <span className="flex-1 text-sm">Expected {line.expected}</span>
        <span className="text-sm">Found</span>
        <IconButton label="One fewer" disabled={line.counted === 0} onClick={() => onCount(line.counted - 1)} className="disabled:opacity-30"><Minus /></IconButton>
        <span className="w-6 text-center font-display text-xl font-bold" aria-live="polite">{line.counted}</span>
        <IconButton label="One more" onClick={() => onCount(line.counted + 1)}><Plus /></IconButton>
      </div>
      {!line.matches && (
        <label className="text-xs">What happened?
          <input value={line.notes} onChange={e => onNotes(e.target.value)} className={`${inputClass} mt-1`} />
        </label>
      )}
    </article>
  );
}

function History({ sessions, now }: { sessions: AuditSession[]; now: number }) {
  if (sessions.length === 0) return <p className="py-6 text-on-surface-variant">No audits yet. Your saved counts will appear here.</p>;
  return (
    <div className="flex flex-col gap-2.5">
      {sessions.map(s => (
        <section key={s.timestamp} className="flex flex-col gap-1.5 rounded-md bg-surface-low p-3.5">
          <div className="flex items-center gap-2">
            {s.mismatches.length === 0 ? <CircleCheck className="text-primary" aria-hidden /> : <TriangleAlert className="text-error" aria-hidden />}
            <h3 className="flex-1 font-display text-sm font-semibold">{formatFull(localToday(new Date(s.timestamp)))} · {s.items} items</h3>
            <span className="text-xs text-on-surface-variant">{formatRelative(s.timestamp, now)}</span>
          </div>
          {s.mismatches.length === 0 && <p className="text-xs text-on-surface-variant">Everything matched.</p>}
          {s.mismatches.map(({ title, record }) => (
            <p key={record.id} className="text-xs text-error">{title}: found {record.counted} of {record.expected}{record.notes ? ` (${record.notes})` : ''}</p>
          ))}
        </section>
      ))}
    </div>
  );
}
