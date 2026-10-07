'use client';

import { CalendarCheck, CalendarX2, Hourglass, Star, TriangleAlert, Wallet } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { useLive, useServices } from '@/app/providers';
import { StatusTimeline } from '@/components/BookingParts';
import { Button } from '@/components/Button';
import { Dialog } from '@/components/Dialog';
import { EmptyState, SkeletonList } from '@/components/EmptyState';
import { inputClass } from '@/components/Field';
import { ItemArt } from '@/components/ItemArt';
import { StatusPill } from '@/components/StatusPill';
import { TabScaffold } from '@/components/TabScaffold';
import { useToast } from '@/components/Toast';
import { errorMessage } from '@/domain/errors';
import { bookingCode, formatDays, formatFull, formatRange, formatShort } from '@/domain/format/dates';
import { formatMoney } from '@/domain/format/money';
import { bookingRange, bookingTotal, DEMO_USER_ID, type Booking, type Item } from '@/domain/models';
import { canCancel, canReview } from '@/domain/rules/bookingStateMachine';
import { daysLate, isOverdue, lateFee } from '@/domain/rules/lateFees';
import { groupRentals, initialRentalTab, RENTAL_TABS, type RentalTab } from '@/lib/booking';
import { paths } from '@/lib/routes';

interface RentalRow { booking: Booking; item: Item | undefined; providerName: string; daysLate: number; lateFee: number }

export default function Rentals() {
  const { bookings } = useServices();
  const toast = useToast();
  const [tab, setTab] = useState<RentalTab | null>(null);
  const [cancelId, setCancelId] = useState<number | null>(null);
  const [reviewFor, setReviewFor] = useState<RentalRow | null>(null);
  const grouped = useLive(async s => {
    const [list, items, providers] = await Promise.all([s.bookings.bookingsForCustomer(DEMO_USER_ID), s.catalog.allItems(), s.catalog.providers()]);
    const itemMap = new Map(items.map(i => [i.id, i]));
    const shop = new Map(providers.map(p => [p.id, p.shopName]));
    const today = s.time.today();
    const rows: RentalRow[] = list.map(b => {
      const item = itemMap.get(b.itemId);
      const overdue = isOverdue(b, today);
      return {
        booking: b, item, providerName: item ? shop.get(item.providerId) ?? '' : '',
        daysLate: overdue ? daysLate(b.endDate, today) : 0,
        lateFee: overdue ? lateFee(b.endDate, today, item?.dailyRate ?? 0) : b.lateFee,
      };
    });
    return groupRentals(rows);
  });

  const current = tab ?? (grouped ? initialRentalTab(grouped) : 'ACTIVE');
  const rows = grouped?.[current] ?? [];
  const label = RENTAL_TABS.find(([t]) => t === current)![1];

  const cancel = async () => {
    if (cancelId == null) return;
    const r = await bookings.cancel(cancelId);
    setCancelId(null);
    toast(r.ok ? 'Booking cancelled' : errorMessage(r.error));
  };

  return (
    <TabScaffold title="My rentals">
      <div role="tablist" aria-label="Rentals" className="no-scrollbar -mx-4 mb-4 flex overflow-x-auto border-b border-outline-variant px-4">
        {RENTAL_TABS.map(([t, name]) => {
          const count = grouped?.[t].length ?? 0;
          return (
            <button key={t} type="button" role="tab" aria-selected={t === current} onClick={() => setTab(t)}
              className={`shrink-0 grow border-b-[3px] px-3 py-3 text-sm font-semibold ${t === current ? 'border-primary text-primary' : 'border-transparent text-on-surface-variant'}`}>
              {count > 0 ? `${name} (${count})` : name}
            </button>
          );
        })}
      </div>
      {!grouped ? <SkeletonList /> : rows.length === 0 ? (
        <EmptyState icon={CalendarX2} title="Nothing here yet" body={`Your ${label.toLowerCase()} rentals will show up here.`} />
      ) : (
        <div className="flex flex-col gap-3">
          {rows.map(r => <RentalCard key={r.booking.id} r={r} onCancel={() => setCancelId(r.booking.id)} onReview={() => setReviewFor(r)} />)}
        </div>
      )}

      <Dialog open={cancelId != null} title="Cancel this booking?" onClose={() => setCancelId(null)}>
        <p className="text-on-surface-variant">The store will be notified. Nothing has been charged.</p>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="text" onClick={() => setCancelId(null)}>Keep it</Button>
          <Button variant="text" onClick={cancel}>Cancel booking</Button>
        </div>
      </Dialog>
      <ReviewDialog row={reviewFor} onClose={() => setReviewFor(null)} onSubmit={async (rating, text) => {
        const r = await bookings.submitReview(reviewFor!.booking.id, rating, text);
        setReviewFor(null);
        toast(r.ok ? 'Thanks for your review!' : errorMessage(r.error));
      }} />
    </TabScaffold>
  );
}

function Note({ icon: Icon, children }: { icon: typeof Hourglass; children: React.ReactNode }) {
  return <p className="flex gap-2 text-xs text-on-surface-variant"><Icon size={16} className="shrink-0" aria-hidden />{children}</p>;
}

function RentalCard({ r, onCancel, onReview }: { r: RentalRow; onCancel: () => void; onReview: () => void }) {
  const b = r.booking;
  const extra = [
    b.lateFee > 0 ? `Late fee ${formatMoney(b.lateFee)}` : null,
    b.damageFee > 0 ? `Damage fee ${formatMoney(b.damageFee)}` : null,
    b.dropTransportFee > 0 ? `Transport fee ${formatMoney(b.dropTransportFee)}` : null,
    b.cleaningFee > 0 ? `Cleaning fee ${formatMoney(b.cleaningFee)}` : null,
  ].filter(Boolean);
  const cancellable = canCancel(b);
  const reviewable = canReview(b);
  return (
    <article className="flex flex-col gap-3 rounded-md bg-surface-low p-3.5">
      <div className="flex items-center gap-3">
        <Link href={paths.item(b.itemId)} className="flex min-w-0 flex-1 items-center gap-3">
          <ItemArt photoKey={r.item?.photos[0] ?? ''} iconSize={28} className="size-16 shrink-0 rounded-sm" />
          <span className="min-w-0">
            <span className="block truncate font-display text-sm font-semibold">{r.item?.title ?? 'Item'}</span>
            <span className="block text-xs text-on-surface-variant">{r.providerName}</span>
            <span className="block text-sm font-semibold">{formatRange(bookingRange(b))} · {formatMoney(b.subtotal)}</span>
          </span>
        </Link>
        <StatusPill status={b.status} />
      </div>
      <StatusTimeline status={b.status} />
      {b.status === 'REQUESTED' && <Note icon={Hourglass}>Waiting for the store to approve. You can pick it up only after approval.</Note>}
      {b.status === 'ACCEPTED' && <Note icon={Wallet}>Approved! Pay {formatMoney(bookingTotal(b))} at pickup on {formatShort(b.startDate)}, including the {formatMoney(b.deposit)} refundable advance.</Note>}
      {b.status === 'ACTIVE' && (r.daysLate > 0 ? (
        <p className="flex gap-2 rounded-sm bg-error-container p-2.5 text-xs text-on-error-container">
          <TriangleAlert size={18} className="shrink-0" aria-hidden />
          Overdue by {formatDays(r.daysLate)}. Late fee so far {formatMoney(r.lateFee)}, taken from your advance. Please return it today.
        </p>
      ) : <Note icon={CalendarCheck}>Return by {formatFull(b.endDate)}. Late days cost {formatMoney(r.item?.dailyRate ?? 0)} each.</Note>)}
      {b.status === 'RETURNED' && extra.length > 0 && <p className="text-xs text-error">{extra.join(' · ')} deducted from your advance</p>}
      {(cancellable || reviewable || b.reviewed || b.status === 'RETURNED') && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="flex-1 text-xs text-on-surface-variant">{bookingCode(b.id)}</span>
          {b.status === 'RETURNED' && <Link href={paths.bill(b.id, true, 'CUSTOMER')} className="inline-flex h-10 items-center rounded-sm border border-outline px-4 text-sm font-semibold text-primary">View bill</Link>}
          {cancellable && <Button variant="outlined" className="!min-h-10" onClick={onCancel}>Cancel</Button>}
          {reviewable && <Button variant="tonal" className="!min-h-10" icon={<Star size={18} aria-hidden />} onClick={onReview}>Rate rental</Button>}
          {b.reviewed && <span className="text-xs font-medium text-primary">Reviewed ✓</span>}
        </div>
      )}
    </article>
  );
}

function ReviewDialog({ row, onClose, onSubmit }: { row: RentalRow | null; onClose: () => void; onSubmit: (rating: number, text: string) => void }) {
  const [rating, setRating] = useState(0);
  const [text, setText] = useState('');
  const [prev, setPrev] = useState(row);
  if (row !== prev) { setPrev(row); setRating(0); setText(''); }
  return (
    <Dialog open={row != null} title={`How was ${row?.item?.title ?? 'your rental'}?`} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <div className="flex" role="radiogroup" aria-label="Rating">
          {[1, 2, 3, 4, 5].map(i => (
            <button key={i} type="button" role="radio" aria-checked={rating === i} aria-label={`${i} star${i > 1 ? 's' : ''}`} onClick={() => setRating(i)}
              className="flex size-12 items-center justify-center rounded-full hover:bg-on-surface/8">
              <Star size={34} className={i <= rating ? 'fill-[#F5A623] text-[#F5A623]' : 'text-[#F5A623]'} aria-hidden />
            </button>
          ))}
        </div>
        <label className="block text-sm text-on-surface-variant">
          Tell others about it (optional)
          <textarea rows={3} value={text} onChange={e => setText(e.target.value)} className={`${inputClass} mt-1.5 h-auto py-3`} />
        </label>
        <Button className="w-full" disabled={rating === 0} onClick={() => onSubmit(rating, text)}>Submit review</Button>
      </div>
    </Dialog>
  );
}
