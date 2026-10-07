import { Ban, CircleMinus } from 'lucide-react';
import { formatMoney } from '@/domain/format/money';
import type { BookingStatus } from '@/domain/models';
import type { PriceBreakdown } from '@/domain/rules/pricing';

const plural = (n: number, word: string) => `${n} ${word}${n > 1 ? 's' : ''}`;

export function PriceLine({ label, amount }: { label: string; amount: number }) {
  return (
    <div className="flex justify-between gap-3 text-sm">
      <span className="text-on-surface-variant">{label}</span>
      <span>{formatMoney(amount)}</span>
    </div>
  );
}

/** Port of PriceBreakdownCard. */
export function PriceBreakdownCard({ breakdown: b, dailyRate, weeklyRate }: { breakdown: PriceBreakdown; dailyRate: number; weeklyRate: number }) {
  const weekLabel = `${plural(b.weeks, 'week')} × ${formatMoney(weeklyRate)}`;
  return (
    <section className="flex flex-col gap-2 rounded-md bg-surface-low p-4">
      <h2 className="font-display text-base font-semibold">Price details</h2>
      {b.weeks > 0 && <PriceLine label={b.bestPriceApplied ? `${weekLabel} (best price)` : weekLabel} amount={b.weeklyCharge} />}
      {b.extraDays > 0 && <PriceLine label={`${plural(b.extraDays, 'day')} × ${formatMoney(dailyRate)}`} amount={b.dailyCharge} />}
      <PriceLine label="Advance (refundable)" amount={b.deposit} />
      <hr className="border-outline-variant" />
      <div className="flex justify-between font-display font-semibold">
        <span>Total at pickup</span>
        <span>{formatMoney(b.totalDueNow)}</span>
      </div>
      <p className="text-xs text-on-surface-variant">
        Rental {formatMoney(b.subtotal)} for {plural(b.days, 'day')}. The advance is paid at pickup and refunded on return, minus any late or damage fees.
      </p>
    </section>
  );
}

const STEPS = ['Requested', 'Approved', 'Picked up', 'Closed'];

/** Port of StatusTimeline: a four-step progress line, or a single line for declined and cancelled bookings. */
export function StatusTimeline({ status }: { status: BookingStatus }) {
  if (status === 'DECLINED' || status === 'CANCELLED') {
    const Icon = status === 'DECLINED' ? CircleMinus : Ban;
    return (
      <p className="flex items-center gap-1.5 text-xs font-medium text-on-surface-variant">
        <Icon size={16} className="text-[var(--st-bad-fg)]" aria-hidden /> {status === 'DECLINED' ? 'Declined by the store' : 'Cancelled'}
      </p>
    );
  }
  const index = status === 'REQUESTED' ? 0 : status === 'ACCEPTED' ? 1 : status === 'ACTIVE' ? 2 : 3;
  const pct = (i: number) => `${(i + 0.5) * 25}%`;
  return (
    <div role="img" aria-label={`Status: ${STEPS[index]}`}>
      <div className="relative h-3.5">
        <div className="absolute top-1/2 h-1 -translate-y-1/2 rounded-full bg-outline-variant" style={{ left: pct(0), right: '12.5%' }} />
        <div className="absolute top-1/2 h-1 -translate-y-1/2 rounded-full bg-primary transition-[width] duration-700" style={{ left: pct(0), width: `${index * 25}%` }} />
        {STEPS.map((_, i) => (
          <span key={i} className={`absolute top-1/2 size-3 -translate-x-1/2 -translate-y-1/2 rounded-full ${i <= index ? 'bg-primary' : 'bg-outline-variant'}`} style={{ left: pct(i) }} />
        ))}
      </div>
      <div className="grid grid-cols-4 text-center text-[11px]" aria-hidden>
        {STEPS.map((s, i) => <span key={s} className={i <= index ? 'text-on-surface' : 'text-on-surface-variant'}>{s}</span>)}
      </div>
    </div>
  );
}
