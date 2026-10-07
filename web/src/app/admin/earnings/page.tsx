'use client';

import { CircleCheck, Clock, TrendingDown, TrendingUp } from 'lucide-react';
import { useState } from 'react';
import { useLive } from '@/app/providers';
import { Button } from '@/components/Button';
import { SkeletonList } from '@/components/EmptyState';
import { TabScaffold } from '@/components/TabScaffold';
import { compactMoney, formatMoney } from '@/domain/format/money';
import { EARNINGS_PERIODS, earnings, type EarningsBar, type EarningsPeriod } from '@/lib/earnings';
import { loadShop } from '@/lib/shop';

export default function Earnings() {
  const [period, setPeriod] = useState<EarningsPeriod>('WEEKLY');
  const [asTable, setAsTable] = useState(false);
  const s = useLive(async svc => {
    const shop = await loadShop(svc);
    return shop ? earnings(period, svc.time.today(), shop.bookings, new Map(shop.items.map(i => [i.id, i.title]))) : null;
  }, [period]);
  const current = EARNINGS_PERIODS.find(([p]) => p === period)![2];

  return (
    <TabScaffold title="Earnings">
      {!s ? <SkeletonList /> : (
        <div className="flex flex-col gap-4 lg:grid lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] lg:gap-8">
          <div className="flex flex-col gap-4">
            <div role="radiogroup" aria-label="Period" className="grid grid-cols-2 overflow-hidden rounded-full border border-outline">
              {EARNINGS_PERIODS.map(([p, label], i) => (
                <button key={p} type="button" role="radio" aria-checked={period === p} onClick={() => setPeriod(p)}
                  className={`h-10 text-sm font-medium ${i > 0 ? 'border-l border-outline' : ''} ${period === p ? 'bg-secondary-container text-on-secondary-container' : ''}`}>{label}</button>
              ))}
            </div>
            <div>
              <p className="text-sm text-on-surface-variant">{current}</p>
              <div className="flex flex-wrap items-end gap-2.5">
                <p className="font-display text-4xl font-extrabold">{formatMoney(s.total)}</p>
                {s.changePercent != null && (
                  <p className="flex items-center gap-1 pb-1 text-sm font-semibold text-on-surface-variant">
                    {s.changePercent >= 0 ? <TrendingUp size={18} aria-hidden /> : <TrendingDown size={18} aria-hidden />}
                    {s.changePercent >= 0 ? '+' : ''}{s.changePercent}% vs previous
                  </p>
                )}
              </div>
            </div>
            <section className="rounded-md bg-surface-lowest p-4">
              <div className="flex items-center">
                <h2 className="flex-1 font-display font-semibold">Completed rentals</h2>
                <Button variant="text" onClick={() => setAsTable(!asTable)}>{asTable ? 'Show chart' : 'View as table'}</Button>
              </div>
              {asTable ? (
                <table className="mt-2 w-full text-sm">
                  <tbody>{[...s.bars].reverse().map(b => <tr key={b.start}><td className="py-1.5">{b.label}</td><td className="py-1.5 text-right">{formatMoney(b.amount)}</td></tr>)}</tbody>
                </table>
              ) : <EarningsChart key={period} bars={s.bars} />}
            </section>
          </div>
          <div className="flex flex-col gap-4">
            <section>
              <h2 className="mb-2 text-xl font-bold">Top items</h2>
              {s.topItems.length === 0 && <p className="text-on-surface-variant">No completed rentals in this period yet.</p>}
              <ol className="flex flex-col gap-3">
                {s.topItems.map((t, i) => (
                  <li key={t.title} className="flex items-center gap-2">
                    <span className="w-7 font-display font-semibold text-primary">{i + 1}</span>
                    <span className="flex-1">
                      <span className="block font-display text-sm font-semibold">{t.title}</span>
                      <span className="block text-xs text-on-surface-variant">{t.rentals} rental{t.rentals > 1 ? 's' : ''}</span>
                    </span>
                    <span className="font-display text-sm font-semibold">{formatMoney(t.amount)}</span>
                  </li>
                ))}
              </ol>
            </section>
            <section>
              <h2 className="mb-2 text-xl font-bold">Payouts</h2>
              {s.payouts.length === 0 && <p className="text-on-surface-variant">Payouts appear after completed rentals.</p>}
              <ul className="flex flex-col">
                {s.payouts.map(p => (
                  <li key={p.label} className="flex items-center gap-3 py-2.5">
                    {p.pending ? <Clock aria-label="Pending" /> : <CircleCheck aria-label="Paid" />}
                    <span className="flex-1">
                      <span className="block text-sm">{p.label}</span>
                      <span className="block text-xs text-on-surface-variant">{p.pending ? 'Pending · pays out Monday' : 'Paid to HDFC •••• 2207'}</span>
                    </span>
                    <span className="font-display text-sm font-semibold">{formatMoney(p.amount)}</span>
                  </li>
                ))}
              </ul>
            </section>
          </div>
        </div>
      )}
    </TabScaffold>
  );
}

/** Single-series bar chart: one color, rounded tops, a recessive grid; tap a bar to read its value. */
function EarningsChart({ bars }: { bars: EarningsBar[] }) {
  const [selected, setSelected] = useState(bars.length - 1);
  const max = Math.max(1, ...bars.map(b => b.amount));
  return (
    <div className="mt-3 flex h-[220px] gap-2" role="img" aria-label={`Earnings chart: ${bars.map(b => `${b.label} ${formatMoney(b.amount)}`).join(', ')}`}>
      <div className="flex w-11 flex-col justify-between pb-6 pt-7 text-right text-[11px] text-on-surface-variant" aria-hidden>
        {[2, 1, 0].map(i => <span key={i}>{compactMoney((max * i) / 2)}</span>)}
      </div>
      <div className="relative flex flex-1 items-end">
        <div className="pointer-events-none absolute inset-x-0 bottom-6 top-7 flex flex-col justify-between" aria-hidden>
          {[0, 1, 2].map(i => <span key={i} className="border-t border-outline-variant/60" />)}
        </div>
        {bars.map((b, i) => (
          <button key={b.start} type="button" onClick={() => setSelected(i)} aria-label={`${b.label}: ${formatMoney(b.amount)}`}
            className="relative flex h-full flex-1 flex-col items-center justify-end pb-6">
            <span className="relative flex w-[56%] flex-1 items-end pt-7">
              <span className="w-full rounded-t-[4px] bg-chart-bar transition-[height] duration-700" style={{ height: `${(b.amount / max) * 100}%` }} />
              {i === selected && (
                <span className="absolute left-1/2 -translate-x-1/2 whitespace-nowrap text-xs font-medium" style={{ bottom: `calc(${(b.amount / max) * 100}% + 4px)` }}>
                  {compactMoney(b.amount)}
                </span>
              )}
            </span>
            <span className="absolute bottom-0 whitespace-nowrap text-[10px] text-on-surface-variant sm:text-[11px]">{b.label}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
