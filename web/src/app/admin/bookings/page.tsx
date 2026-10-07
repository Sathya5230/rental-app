'use client';

import { ArrowDownToLine, ArrowUpFromLine, Inbox, ReceiptText, TriangleAlert } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { useLive, useServices } from '@/app/providers';
import { Button } from '@/components/Button';
import { Dialog } from '@/components/Dialog';
import { EmptyState, SkeletonList } from '@/components/EmptyState';
import { ItemArt } from '@/components/ItemArt';
import { StatusPill } from '@/components/StatusPill';
import { TabScaffold } from '@/components/TabScaffold';
import { useToast } from '@/components/Toast';
import { rangeDays } from '@/domain/dates';
import { errorMessage } from '@/domain/errors';
import { bookingCode, formatDays, formatRange } from '@/domain/format/dates';
import { formatMoney } from '@/domain/format/money';
import { bookingRange } from '@/domain/models';
import { paths } from '@/lib/routes';
import { groupProviderBookings, loadShop, PROVIDER_TABS, providerRows, type ProviderBookingRow, type ProviderTab } from '@/lib/shop';
import { singleFlight } from '@/lib/singleFlight';

export default function AdminBookings() {
  const [tab, setTab] = useState<ProviderTab>('REQUESTS');
  const grouped = useLive(async s => {
    const shop = await loadShop(s);
    return shop ? groupProviderBookings(providerRows(shop, s.time.today())) : null;
  });
  const rows = grouped?.[tab] ?? [];
  const empty = PROVIDER_TABS.find(([t]) => t === tab)![2];

  return (
    <TabScaffold title="Bookings">
      <div role="tablist" aria-label="Bookings" className="no-scrollbar -mx-4 mb-4 flex overflow-x-auto border-b border-outline-variant px-4 lg:mx-0 lg:px-0">
        {PROVIDER_TABS.map(([t, label]) => {
          const n = grouped?.[t].length ?? 0;
          return (
            <button key={t} type="button" role="tab" aria-selected={t === tab} onClick={() => setTab(t)}
              className={`shrink-0 grow border-b-[3px] px-3 py-3 text-sm font-semibold lg:grow-0 lg:px-6 ${t === tab ? 'border-primary text-primary' : 'border-transparent text-on-surface-variant'}`}>
              {n > 0 ? `${label} (${n})` : label}
            </button>
          );
        })}
      </div>
      {!grouped ? <SkeletonList /> : rows.length === 0 ? <EmptyState icon={Inbox} title="Nothing here" body={empty} /> : (
        <div className="grid gap-3 lg:grid-cols-2">
          {rows.map(r => (tab === 'REQUESTS' ? <RequestCard key={r.booking.id} r={r} /> : <BookingCard key={r.booking.id} r={r} tab={tab} />))}
        </div>
      )}
    </TabScaffold>
  );
}

function BookingHeader({ r }: { r: ProviderBookingRow }) {
  const b = r.booking;
  return (
    <div className="flex items-start gap-3">
      <ItemArt photoKey={r.item?.photos[0] ?? ''} iconSize={24} className="size-14 shrink-0 rounded-sm" />
      <div className="min-w-0 flex-1">
        <h3 className="font-display text-sm font-semibold">{r.item?.title ?? 'Item'}</h3>
        <p className="text-xs text-on-surface-variant">{r.customerName} · {formatRange(bookingRange(b))} ({formatDays(rangeDays(bookingRange(b)))})</p>
        {b.contactPhone && <p className="text-xs text-on-surface-variant">{b.contactPhone}</p>}
        <p className="text-sm font-semibold">{formatMoney(b.subtotal)} + {formatMoney(b.deposit)} advance</p>
      </div>
      {r.isOverdue ? (
        <span className="inline-flex items-center gap-1 rounded-full bg-error-container px-2.5 py-1 text-xs font-semibold text-on-error-container"><TriangleAlert size={14} aria-hidden /> Overdue</span>
      ) : <StatusPill status={b.status} />}
    </div>
  );
}

function BookingCard({ r, tab }: { r: ProviderBookingRow; tab: ProviderTab }) {
  const b = r.booking;
  const meta = [
    bookingCode(b.id), r.unitTag ? `Unit ${r.unitTag}` : null,
    r.lateFee > 0 ? `Late fee ${formatMoney(r.lateFee)}` : null,
    b.damageFee > 0 ? `Damage ${formatMoney(b.damageFee)}` : null,
    b.dropTransportFee > 0 ? `Transport ${formatMoney(b.dropTransportFee)}` : null,
    b.cleaningFee > 0 ? `Cleaning ${formatMoney(b.cleaningFee)}` : null,
  ].filter(Boolean).join(' · ');
  const action = 'inline-flex h-10 shrink-0 items-center gap-1.5 rounded-sm px-4 text-sm font-semibold';
  return (
    <article className="flex flex-col gap-2.5 rounded-md bg-surface-low p-3.5">
      <BookingHeader r={r} />
      <div className="flex items-center gap-2">
        <p className={`flex-1 text-xs ${r.isOverdue ? 'text-error' : 'text-on-surface-variant'}`}>{meta}</p>
        {tab === 'UPCOMING' && <Link href={paths.handover(b.id, false)} className={`${action} bg-primary text-on-primary`}><ArrowUpFromLine size={18} aria-hidden /> Check out</Link>}
        {tab === 'ACTIVE' && <Link href={paths.handover(b.id, true)} className={`${action} bg-primary text-on-primary`}><ArrowDownToLine size={18} aria-hidden /> Return & close</Link>}
        {tab === 'COMPLETED' && b.status === 'RETURNED' && (
          <Link href={paths.bill(b.id, true, 'ADMIN')} className={`${action} border border-outline text-primary`}><ReceiptText size={18} aria-hidden /> View bill</Link>
        )}
      </div>
    </article>
  );
}

/** Request card with approve (unit picker) and decline (confirm). Each action runs once, however often it's tapped. */
function RequestCard({ r }: { r: ProviderBookingRow }) {
  const { bookings } = useServices();
  const toast = useToast();
  const [picking, setPicking] = useState(false);
  const [declining, setDeclining] = useState(false);
  const [chosen, setChosen] = useState<number | null>(r.freeUnits[0]?.id ?? null);
  const [accept] = useState(() => singleFlight((unitId: number) => bookings.accept(r.booking.id, unitId)));
  const [decline] = useState(() => singleFlight(() => bookings.decline(r.booking.id)));

  const confirmAccept = async () => {
    if (chosen == null) return;
    const res = await accept(chosen);
    setPicking(false);
    toast(res.ok ? 'Request approved. The customer has been notified.' : errorMessage(res.error));
  };
  const confirmDecline = async () => {
    const res = await decline();
    setDeclining(false);
    toast(res.ok ? 'Request declined' : errorMessage(res.error));
  };

  return (
    <article className="flex flex-col gap-3 rounded-md bg-surface-low p-3.5">
      <BookingHeader r={r} />
      {r.freeUnits.length === 0 && (
        <p className="flex items-center gap-1.5 text-xs text-error"><TriangleAlert size={16} aria-hidden /> No unit is free for these dates</p>
      )}
      <div className="grid grid-cols-2 gap-2.5">
        <Button variant="outlined" onClick={() => setDeclining(true)}>Decline</Button>
        <Button disabled={r.freeUnits.length === 0} onClick={() => setPicking(true)}>Approve</Button>
      </div>

      <Dialog open={picking} title="Assign a unit" onClose={() => setPicking(false)}>
        <p className="text-sm">Approve and pick which {r.item?.title ?? 'unit'} goes to {r.customerName}.</p>
        <div role="radiogroup" aria-label="Unit" className="mt-2 flex flex-col">
          {r.freeUnits.map(u => (
            <label key={u.id} className="flex cursor-pointer items-center gap-3 py-2">
              <input type="radio" name={`unit-${r.booking.id}`} checked={chosen === u.id} onChange={() => setChosen(u.id)} className="size-5 accent-[var(--primary)]" />
              <span className="flex-1 font-display text-sm font-semibold">{u.tag}</span>
              <span className="text-xs">{u.condition.charAt(0) + u.condition.slice(1).toLowerCase()}</span>
            </label>
          ))}
        </div>
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="text" onClick={() => setPicking(false)}>Cancel</Button>
          <Button variant="text" disabled={chosen == null} onClick={confirmAccept}>Confirm</Button>
        </div>
      </Dialog>
      <Dialog open={declining} title="Decline this request?" onClose={() => setDeclining(false)}>
        <p className="text-on-surface-variant">{r.customerName} will be told the item isn&apos;t available.</p>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="text" onClick={() => setDeclining(false)}>Keep</Button>
          <Button variant="text" onClick={confirmDecline}>Decline</Button>
        </div>
      </Dialog>
    </article>
  );
}
