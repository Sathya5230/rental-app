'use client';

import { SearchX } from 'lucide-react';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { useLive } from '@/app/providers';
import { AvailabilityCalendar } from '@/components/AvailabilityCalendar';
import { Button } from '@/components/Button';
import { EmptyState, SkeletonList } from '@/components/EmptyState';
import { Screen } from '@/components/Screen';
import { addDays, rangeDays } from '@/domain/dates';
import { errorMessage } from '@/domain/errors';
import { formatDays, formatRange } from '@/domain/format/dates';
import { formatMoney } from '@/domain/format/money';
import { isBookable, unavailableDates } from '@/domain/rules/availability';
import { quoteItem } from '@/domain/rules/pricing';
import { nextSelection, selectionRange, type Selection } from '@/lib/booking';
import { paths, parseId } from '@/lib/routes';

export default function BookingDates() {
  const id = parseId(useParams<{ id: string }>().id);
  const router = useRouter();
  const [selection, setSelection] = useState<Selection>(null);
  const data = useLive(async s => {
    const today = s.time.today();
    if (id == null) return { item: undefined, units: [], bookings: [], today, unavailable: new Set<string>() };
    const [item, units, bookings] = await Promise.all([s.catalog.item(id), s.inventory.unitsForItem(id), s.bookings.bookingsForItem(id)]);
    return { item, units, bookings, today, unavailable: unavailableDates({ start: today, end: addDays(today, 365) }, units, bookings) };
  }, [id]);

  if (!data) return <Screen title="Choose dates"><SkeletonList /></Screen>;
  const { item } = data;
  if (!item) {
    return (
      <Screen title="Choose dates">
        <EmptyState icon={SearchX} title="Item not found" body="It may have been removed by the provider." action={{ label: 'Go back', onClick: () => router.back() }} />
      </Screen>
    );
  }
  const range = selectionRange(selection);
  const bookable = range != null && isBookable(range, data.units, data.bookings);
  const quote = range && bookable ? quoteItem(item, range) : null;
  const breakdown = quote?.ok ? quote.value : null;

  const footer = (
    <div className="flex flex-col gap-3">
      <div aria-live="polite" className="text-sm">
        {range && !bookable && <p className="text-error">{errorMessage({ kind: 'DatesUnavailable' })}</p>}
        {range && breakdown && (
          <div className="flex items-center justify-between gap-3">
            <span>{formatRange(range)} · {formatDays(rangeDays(range))}</span>
            <span className="font-display font-semibold">{formatMoney(breakdown.totalDueNow)} at pickup</span>
          </div>
        )}
        {!range && <p className="text-on-surface-variant">Tap a start date, then an end date. Tap the same day twice for a one-day rental.</p>}
      </div>
      <Button className="w-full" disabled={!breakdown} onClick={() => range && router.push(paths.checkout(item.id, range.start, range.end))}>Continue</Button>
    </div>
  );

  return (
    <Screen title="Choose dates" footer={footer}>
      <p className="mb-2 text-sm text-on-surface-variant">{item.title}</p>
      <AvailabilityCalendar today={data.today} unavailable={data.unavailable} monthsAhead={3} selected={range}
        onPick={d => setSelection(nextSelection(selection, d))} />
    </Screen>
  );
}
