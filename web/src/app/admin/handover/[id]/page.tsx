'use client';

import { CircleAlert } from 'lucide-react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { useState } from 'react';
import { useLive, useServices } from '@/app/providers';
import { Button } from '@/components/Button';
import { EmptyState, SkeletonList } from '@/components/EmptyState';
import { Field, inputClass, TextInput } from '@/components/Field';
import { ItemArt } from '@/components/ItemArt';
import { Screen } from '@/components/Screen';
import { rangeDays } from '@/domain/dates';
import { errorMessage } from '@/domain/errors';
import { bookingCode, formatDays, formatRange, formatShort } from '@/domain/format/dates';
import { formatMoney, parseRupees } from '@/domain/format/money';
import { bookingRange, UNIT_CONDITIONS, type Booking, type Item, type ItemUnit, type UnitCondition } from '@/domain/models';
import { daysLate, lateFee, settle } from '@/domain/rules/lateFees';
import { paths, parseId } from '@/lib/routes';
import { singleFlight } from '@/lib/singleFlight';

const PICKUP_CHECKS = ['Customer ID verified', 'Advance collected', 'All accessories included', 'Condition photos taken'];
const RETURN_CHECKS = ['All accessories returned', 'Item cleaned', 'Functional test passed'];

const rupees = (v: string) => parseRupees(v.trim() === '' ? '0' : v);
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

export default function Handover() {
  const id = parseId(useParams<{ id: string }>().id);
  const isReturn = useSearchParams().get('return') === '1';
  const title = isReturn ? 'Return & close rental' : 'Check out';
  const data = useLive(async s => {
    const booking = id == null ? undefined : await s.bookings.booking(id);
    if (!booking) return { booking: undefined };
    const [item, units, customer] = await Promise.all([s.catalog.item(booking.itemId), s.inventory.unitsForItem(booking.itemId), s.catalog.user(booking.customerId)]);
    return { booking, item, unit: units.find(u => u.id === booking.unitId), customer: customer?.name ?? '', today: s.time.today() };
  }, [id]);

  if (!data) return <Screen title={title}><SkeletonList /></Screen>;
  if (!data.booking) return <Screen title={title}><EmptyState icon={CircleAlert} title="Booking not found" body="We couldn't find that anymore." /></Screen>;
  return <HandoverForm key={data.booking.id} isReturn={isReturn} title={title} {...data} booking={data.booking} />;
}

function HandoverForm({ isReturn, title, booking: b, item, unit, customer, today }: {
  isReturn: boolean; title: string; booking: Booking; item: Item | undefined; unit: ItemUnit | undefined; customer: string; today: string;
}) {
  const { bookings } = useServices();
  const router = useRouter();
  const checks = isReturn ? RETURN_CHECKS : PICKUP_CHECKS;
  const [checked, setChecked] = useState<Set<number>>(new Set());
  const [condition, setCondition] = useState<UnitCondition>(unit?.condition ?? 'GOOD');
  const [damage, setDamage] = useState('0');
  const [transport, setTransport] = useState('0');
  const [cleaning, setCleaning] = useState('0');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submit] = useState(() => singleFlight((input: { checklist: string[]; notes: string; damage: number; transport: number; cleaning: number; condition: UnitCondition }) =>
    isReturn
      ? bookings.processReturn(b.id, { checklist: input.checklist, conditionAfter: input.condition, notes: input.notes, damageFee: input.damage, transportFee: input.transport, cleaningFee: input.cleaning })
      : bookings.checkOut(b.id, { checklist: input.checklist, notes: input.notes, transportFee: input.transport })));

  const late = daysLate(b.endDate, today);
  const fee = lateFee(b.endDate, today, item?.dailyRate ?? 0);
  const transportPaise = rupees(transport) ?? 0;

  const confirm = async () => {
    const t = rupees(transport);
    const d = rupees(damage);
    const c = rupees(cleaning);
    if (t == null) { setError('Enter a valid transport charge'); return; }
    if (isReturn && (d == null || c == null)) { setError('Enter a valid damage and cleaning charge'); return; }
    setError(null);
    setSubmitting(true);
    const r = await submit({ checklist: [...checked].sort().map(i => checks[i]), notes, damage: d ?? 0, transport: t, cleaning: c ?? 0, condition });
    if (r.ok) router.replace(paths.bill(b.id, isReturn, 'ADMIN'));
    else { setSubmitting(false); setError(errorMessage(r.error)); }
  };

  const footer = (
    <div>
      {error && <p role="alert" className="mb-2 text-sm text-error">{error}</p>}
      <Button className="w-full" loading={submitting} onClick={confirm}>{isReturn ? 'Close rental' : 'Hand over item'}</Button>
    </div>
  );
  const s = settle({ advance: b.deposit, damageFee: rupees(damage) ?? 0, lateFee: fee, cleaningFee: rupees(cleaning) ?? 0, dropTransportFee: transportPaise });

  return (
    <Screen title={title} footer={footer}>
      <div className="flex flex-col gap-4">
        {isReturn && late > 0 && (
          <p className="rounded-sm bg-error-container p-3.5 text-sm text-on-error-container">
            Returned {formatDays(late)} late (due {formatShort(b.endDate)}). The late fee is taken from the advance.
          </p>
        )}
        <div className="flex items-center gap-3 rounded-md bg-surface-low p-3">
          <ItemArt photoKey={item?.photos[0] ?? ''} iconSize={28} className="size-16 shrink-0 rounded-sm" />
          <div>
            <p className="font-display text-sm font-semibold">{item?.title}</p>
            <p className="text-xs text-on-surface-variant">{customer} · {formatRange(bookingRange(b))}</p>
            <p className="text-sm font-semibold">Unit {unit?.tag ?? '—'} · {bookingCode(b.id)}</p>
          </div>
        </div>

        <fieldset>
          <legend className="mb-1 font-display font-semibold">Checklist ({checked.size}/{checks.length})</legend>
          {checks.map((label, i) => (
            <label key={label} className="flex cursor-pointer items-center gap-3 py-2">
              <input type="checkbox" className="size-5 accent-[var(--primary)]" checked={checked.has(i)}
                onChange={() => setChecked(prev => { const next = new Set(prev); if (next.has(i)) next.delete(i); else next.add(i); return next; })} />
              {label}
            </label>
          ))}
        </fieldset>

        {!isReturn && (
          <>
            <h2 className="font-display font-semibold">Charges</h2>
            <Field label="Transport charge (pickup) ₹" hint="For delivering the item, if any">
              <TextInput inputMode="decimal" value={transport} onChange={e => { setTransport(e.target.value); setError(null); }} />
            </Field>
            <section className="flex flex-col gap-1 rounded-sm bg-secondary-container p-3.5 text-sm text-on-secondary-container">
              <h3 className="font-display font-semibold">Collected at pickup</h3>
              <Line label={`Rental (${formatDays(rangeDays(bookingRange(b)))})`} value={formatMoney(b.subtotal)} />
              <Line label="Advance (refundable)" value={formatMoney(b.deposit)} />
              {transportPaise > 0 && <Line label="Transport charge" value={formatMoney(transportPaise)} />}
              <hr className="my-1 border-current/20" />
              <Line label="Total due now" value={formatMoney(b.subtotal + b.deposit + transportPaise)} />
            </section>
          </>
        )}

        {isReturn && (
          <>
            <fieldset>
              <legend className="mb-2 font-display font-semibold">Condition after return</legend>
              <div className="flex flex-wrap gap-2">
                {UNIT_CONDITIONS.map(c => (
                  <button key={c} type="button" aria-pressed={condition === c} onClick={() => setCondition(c)}
                    className={`h-8 rounded-xs border px-3 text-sm font-medium ${condition === c ? 'border-transparent bg-secondary-container text-on-secondary-container' : 'border-outline text-on-surface-variant'}`}>
                    {titleCase(c)}
                  </button>
                ))}
              </div>
              {condition === 'DAMAGED' && <p className="mt-2 text-xs text-error">The unit will be moved to maintenance automatically.</p>}
            </fieldset>
            <Field label="Damage fee ₹"><TextInput inputMode="decimal" value={damage} onChange={e => { setDamage(e.target.value); setError(null); }} /></Field>
            <div className="grid grid-cols-2 gap-2.5">
              <Field label="Transport (drop) ₹"><TextInput inputMode="decimal" value={transport} onChange={e => { setTransport(e.target.value); setError(null); }} /></Field>
              <Field label="Cleaning ₹"><TextInput inputMode="decimal" value={cleaning} onChange={e => { setCleaning(e.target.value); setError(null); }} /></Field>
            </div>
            <section className="flex flex-col gap-1 rounded-sm bg-secondary-container p-3.5 text-sm text-on-secondary-container" aria-live="polite">
              <h3 className="font-display font-semibold">Settlement</h3>
              <Line label="Advance paid" value={formatMoney(s.advance)} />
              {late > 0 && <Line label={`Late fee (${formatDays(late)} × ${formatMoney(item?.dailyRate ?? 0)})`} value={`− ${formatMoney(s.lateFee)}`} />}
              {s.damageFee > 0 && <Line label="Damage fee" value={`− ${formatMoney(s.damageFee)}`} />}
              {s.dropTransportFee > 0 && <Line label="Transport charge (drop)" value={`− ${formatMoney(s.dropTransportFee)}`} />}
              {s.cleaningFee > 0 && <Line label="Cleaning charge" value={`− ${formatMoney(s.cleaningFee)}`} />}
              <hr className="my-1 border-current/20" />
              <p className="font-display font-semibold">{s.balance >= 0 ? `Refund to customer: ${formatMoney(s.balance)}` : `Collect from customer: ${formatMoney(-s.balance)}`}</p>
            </section>
          </>
        )}

        <label className="block text-sm text-on-surface-variant">
          Notes (optional)
          <textarea rows={2} value={notes} onChange={e => setNotes(e.target.value)} className={`${inputClass} mt-1.5 h-auto py-3`} />
        </label>
      </div>
    </Screen>
  );
}

function Line({ label, value }: { label: string; value: string }) {
  return <div className="flex justify-between gap-3"><span>{label}</span><span>{value}</span></div>;
}
