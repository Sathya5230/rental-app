'use client';

import { CalendarX, CircleAlert, Hourglass, Send, Wallet } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useMemo, useState } from 'react';
import { useLive, useServices } from '@/app/providers';
import { PriceBreakdownCard } from '@/components/BookingParts';
import { Button } from '@/components/Button';
import { EmptyState, SkeletonList } from '@/components/EmptyState';
import { Field, TextInput } from '@/components/Field';
import { ItemArt } from '@/components/ItemArt';
import { Screen } from '@/components/Screen';
import { rangeDays, type DateRange } from '@/domain/dates';
import { errorMessage, type DomainError } from '@/domain/errors';
import { formatDays, formatFull, formatRange } from '@/domain/format/dates';
import { formatMoney } from '@/domain/format/money';
import { nationalDigits } from '@/domain/format/phone';
import { DEMO_USER_ID } from '@/domain/models';
import { quoteItem } from '@/domain/rules/pricing';
import { paths } from '@/lib/routes';
import { singleFlight } from '@/lib/singleFlight';

const ISO = /^\d{4}-\d{2}-\d{2}$/;

export default function Checkout() {
  const params = useSearchParams();
  const router = useRouter();
  const itemId = Number(params.get('item'));
  const start = params.get('start') ?? '';
  const end = params.get('end') ?? '';
  const valid = itemId > 0 && ISO.test(start) && ISO.test(end) && end >= start;
  if (!valid) {
    return (
      <Screen title="Request to rent">
        <EmptyState icon={CalendarX} title="Pick your dates first" body="This request is missing its item or dates." action={{ label: 'Go back', onClick: () => router.back() }} />
      </Screen>
    );
  }
  return <CheckoutForm itemId={itemId} start={start} end={end} />;
}

function CheckoutForm({ itemId, start, end }: { itemId: number; start: string; end: string }) {
  const range: DateRange = useMemo(() => ({ start, end }), [start, end]);
  const { bookings } = useServices();
  const router = useRouter();
  const data = useLive(async s => {
    const item = await s.catalog.item(itemId);
    const providers = await s.catalog.providers();
    const user = await s.catalog.user(DEMO_USER_ID);
    return { item, providerName: providers.find(p => p.id === item?.providerId)?.shopName ?? '', userPhone: user?.phone ?? '' };
  }, [itemId]);
  // Until edited, use the number the customer signed in with.
  const [edited, setEdited] = useState<string | null>(null);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState<DomainError | null>(null);

  /** Idempotent: ignored while sending or once sent, so a double tap can't create two requests. */
  const submit = useMemo(() => singleFlight((phone: string) => bookings.requestBooking(itemId, DEMO_USER_ID, range, phone)), [bookings, itemId, range]);

  if (!data) return <Screen title="Request to rent"><SkeletonList /></Screen>;
  const { item } = data;
  if (!item) {
    return <Screen title="Request to rent"><EmptyState icon={CircleAlert} title="Item not found" body="It may have been removed by the provider." /></Screen>;
  }
  const phone = edited ?? nationalDigits(data.userPhone) ?? '';
  const phoneValid = nationalDigits(phone) != null;
  const quote = quoteItem(item, range);
  const breakdown = quote.ok ? quote.value : null;

  const send = async () => {
    setProcessing(true);
    setError(null);
    const r = await submit(phone);
    if (r.ok) {
      router.replace(paths.bookingSuccess(r.value.id));
    } else {
      setProcessing(false);
      setError(r.error);
    }
  };

  const footer = (
    <div>
      {error && <p role="alert" className="mb-2 text-sm text-error">{errorMessage(error)}</p>}
      <Button className="w-full" icon={<Send size={18} aria-hidden />} disabled={!breakdown || !phoneValid} loading={processing} onClick={send}>Send request</Button>
    </div>
  );

  return (
    <Screen title="Request to rent" footer={footer}>
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-3 rounded-md bg-surface-low p-3">
          <ItemArt photoKey={item.photos[0] ?? ''} iconSize={32} className="size-[72px] shrink-0 rounded-sm" />
          <div>
            <p className="font-display text-sm font-semibold">{item.title}</p>
            <p className="text-xs text-on-surface-variant">{data.providerName}</p>
            <p className="text-sm font-semibold">{formatRange(range)} · {formatDays(rangeDays(range))}</p>
          </div>
        </div>
        {breakdown && <PriceBreakdownCard breakdown={breakdown} dailyRate={item.dailyRate} weeklyRate={item.weeklyRate} />}
        <Field label="Your mobile number" hint="We'll text you here if the return is late."
          error={phone.length === 10 && !phoneValid ? errorMessage({ kind: 'InvalidPhone' }) : null}>
          <TextInput prefix="+91 " inputMode="tel" value={phone} onChange={e => { setEdited(e.target.value.replace(/\D/g, '').slice(0, 10)); setError(null); }} />
        </Field>
        <section className="flex flex-col gap-2.5 rounded-md bg-secondary-container p-4 text-sm text-on-secondary-container">
          <h2 className="font-display font-semibold">How it works</h2>
          <p className="flex gap-2.5"><Hourglass size={18} className="shrink-0" aria-hidden />The store reviews your request. You can rent only after it&apos;s approved.</p>
          <p className="flex gap-2.5"><Wallet size={18} className="shrink-0" aria-hidden />Pay {breakdown ? formatMoney(breakdown.totalDueNow) : 'the total'} at pickup, including the refundable advance.</p>
          <p className="flex gap-2.5"><CalendarX size={18} className="shrink-0" aria-hidden />Return by {formatFull(range.end)}. Each late day costs {formatMoney(item.dailyRate)}, taken from your advance.</p>
        </section>
      </div>
    </Screen>
  );
}
