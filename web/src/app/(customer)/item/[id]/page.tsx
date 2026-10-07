'use client';

import { ArrowLeft, BadgeCheck, CalendarDays, SearchX } from 'lucide-react';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { useLive, useServices } from '@/app/providers';
import { AvailabilityCalendar } from '@/components/AvailabilityCalendar';
import { Button, IconButton } from '@/components/Button';
import { EmptyState, SkeletonList } from '@/components/EmptyState';
import { Avatar, FavouriteButton, PriceText, RatingBadge } from '@/components/ItemCard';
import { ItemArt } from '@/components/ItemArt';
import { addDays, localToday } from '@/domain/dates';
import { formatFull } from '@/domain/format/dates';
import { formatMoney } from '@/domain/format/money';
import { DEMO_USER_ID } from '@/domain/models';
import { unavailableDates, usableUnits } from '@/domain/rules/availability';
import { paths, parseId } from '@/lib/routes';

export default function ItemDetails() {
  const id = parseId(useParams<{ id: string }>().id);
  const { catalog, session } = useServices();
  const router = useRouter();
  const goBack = () => (window.history.length > 1 ? router.back() : router.push(paths.home));

  useEffect(() => { if (id != null) session.recordView(id); }, [id, session]);

  const state = useLive(async s => {
    const item = id == null ? undefined : await s.catalog.item(id);
    if (!item || id == null) return { item: undefined };
    const today = s.time.today();
    const [providers, users, categories, units, bookings, reviews, ratings, favourites] = await Promise.all([
      s.catalog.providers(), s.catalog.users(), s.catalog.categories(), s.inventory.unitsForItem(id), s.bookings.bookingsForItem(id),
      s.catalog.reviewsForItem(id), s.catalog.ratingSummaries(), s.catalog.favouriteIds(DEMO_USER_ID),
    ]);
    const provider = providers.find(p => p.id === item.providerId);
    const names = new Map(users.map(u => [u.id, u.name]));
    return {
      item,
      categoryName: categories.find(c => c.id === item.categoryId)?.name ?? '',
      provider,
      rating: ratings.get(id),
      reviews: reviews.map(r => ({ ...r, author: names.get(r.customerId) ?? 'Renter' })),
      isFavourite: favourites.has(id),
      isOwnListing: provider?.userId === DEMO_USER_ID,
      usableUnits: usableUnits(units).length,
      unavailable: unavailableDates({ start: today, end: addDays(today, 92) }, units, bookings),
      today,
    };
  }, [id]);

  if (!state) return <div className="mx-auto max-w-md p-4"><SkeletonList /></div>;
  if (!state.item) {
    return (
      <div className="mx-auto max-w-md p-4">
        <EmptyState icon={SearchX} title="Item not found" body="It may have been removed by the provider." action={{ label: 'Go back', onClick: goBack }} />
      </div>
    );
  }
  const { item } = state;
  const stockText = state.isOwnListing ? 'This is your listing' : state.usableUnits === 0 ? 'Currently unavailable' : `${state.usableUnits} unit${state.usableUnits > 1 ? 's' : ''} in stock`;

  return (
    <div className="mx-auto min-h-dvh max-w-md pb-32 lg:max-w-4xl">
      <div className="relative lg:mt-4">
        <PhotoPager photos={item.photos} />
        <div className="absolute inset-x-0 top-0 flex justify-between p-2">
          <IconButton label="Back" onClick={goBack} className="bg-surface/90"><ArrowLeft size={22} /></IconButton>
          <FavouriteButton active={state.isFavourite} onToggle={() => catalog.toggleFavourite(DEMO_USER_ID, item.id)} className="size-12" />
        </div>
      </div>

      <div className="flex flex-col gap-4 p-5">
        <div>
          <p className="text-xs font-semibold tracking-wide text-primary">{state.categoryName.toUpperCase()}</p>
          <h1 className="text-2xl font-bold">{item.title}</h1>
          <div className="mt-1"><RatingBadge summary={state.rating} /></div>
        </div>
        <div className="grid grid-cols-3 gap-2.5">
          {([['Per day', item.dailyRate], ['Per week', item.weeklyRate], ['Advance', item.deposit]] as const).map(([label, amount]) => (
            <div key={label} className="rounded-sm bg-surface-low p-3">
              <p className="text-xs text-on-surface-variant">{label}</p>
              <p className="font-display font-semibold">{formatMoney(amount)}</p>
            </div>
          ))}
        </div>
        {state.provider && (
          <div className="flex items-center gap-3 rounded-md bg-surface-low p-3.5">
            <Avatar name={state.provider.shopName} />
            <div className="min-w-0 flex-1">
              <p className="font-display text-sm font-semibold">{state.provider.shopName}</p>
              <p className="text-xs text-on-surface-variant">★ {state.provider.rating} · {state.provider.reviewCount} reviews · {state.provider.locationText}</p>
            </div>
            <BadgeCheck className="text-primary" aria-label="Verified provider" />
          </div>
        )}
        <p>{item.description}</p>

        <div className="lg:grid lg:grid-cols-2 lg:gap-8">
          <section>
            <h2 className="mb-1 text-xl font-bold">Availability</h2>
            <AvailabilityCalendar today={state.today} unavailable={state.unavailable} />
          </section>
          <div className="flex flex-col gap-4">
            {item.specs.length > 0 && (
              <section className="mt-4 lg:mt-0">
                <h2 className="mb-2 text-xl font-bold">Specifications</h2>
                <dl className="flex flex-col gap-2.5 rounded-md bg-surface-low p-4 text-sm">
                  {item.specs.map(([k, v]) => (
                    <div key={k} className="grid grid-cols-[2fr_3fr]"><dt className="text-on-surface-variant">{k}</dt><dd>{v}</dd></div>
                  ))}
                </dl>
              </section>
            )}
            <Reviews reviews={state.reviews} />
          </div>
        </div>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-outline-variant bg-surface-lowest pb-[env(safe-area-inset-bottom)] shadow-[0_-4px_16px_rgba(0,0,0,0.06)]">
        <div className="mx-auto flex max-w-md items-center gap-3 px-5 py-3 lg:max-w-4xl">
          <div className="flex-1">
            <PriceText dailyRate={item.dailyRate} />
            <p className="text-xs text-on-surface-variant">{stockText}</p>
          </div>
          {!state.isOwnListing && (
            <Button icon={<CalendarDays size={18} aria-hidden />} disabled={state.usableUnits === 0} onClick={() => router.push(paths.book(item.id))}>Select dates</Button>
          )}
        </div>
      </div>
    </div>
  );

}

function Reviews({ reviews }: { reviews: { id: number; author: string; rating: number; text: string; createdAt: number }[] }) {
  const [all, setAll] = useState(false);
  return (
    <section className="mt-4 lg:mt-0">
      <h2 className="mb-2 text-xl font-bold">Reviews</h2>
      {reviews.length === 0 && <p className="text-on-surface-variant">No reviews yet. Be the first to rent it!</p>}
      <ul className="flex flex-col gap-4">
        {(all ? reviews : reviews.slice(0, 3)).map(r => (
          <li key={r.id} className="flex gap-3">
            <Avatar name={r.author} size={36} />
            <div className="flex-1">
              <div className="flex items-center justify-between">
                <p className="font-display text-sm font-semibold">{r.author}</p>
                <span className="text-xs text-[#F5A623]" aria-label={`${r.rating} stars`}>{'★'.repeat(r.rating)}</span>
              </div>
              <p className="text-sm">{r.text}</p>
              <p className="text-xs text-on-surface-variant">{formatFull(localToday(new Date(r.createdAt)))}</p>
            </div>
          </li>
        ))}
      </ul>
      {reviews.length > 3 && !all && <Button variant="text" className="mt-2" onClick={() => setAll(true)}>See all {reviews.length} reviews</Button>}
    </section>
  );
}

/** Swipeable photos with dots, like the HorizontalPager on Android. */
function PhotoPager({ photos }: { photos: string[] }) {
  const ref = useRef<HTMLDivElement>(null);
  const [page, setPage] = useState(0);
  const keys = photos.length > 0 ? photos : [''];
  return (
    <div className="relative">
      <div ref={ref} onScroll={e => setPage(Math.round(e.currentTarget.scrollLeft / e.currentTarget.clientWidth))}
        className="no-scrollbar flex h-80 snap-x snap-mandatory overflow-x-auto lg:rounded-lg" aria-label="Photos">
        {keys.map((k, i) => <ItemArt key={i} photoKey={k} iconSize={96} className="h-full w-full shrink-0 snap-center" />)}
      </div>
      {keys.length > 1 && (
        <div className="absolute inset-x-0 bottom-3 flex justify-center gap-1.5">
          {keys.map((_, i) => (
            <button key={i} type="button" aria-label={`Photo ${i + 1}`} onClick={() => ref.current?.scrollTo({ left: i * ref.current.clientWidth, behavior: 'smooth' })}
              className={`rounded-full bg-white transition-all ${i === page ? 'size-2.5' : 'size-[7px] opacity-60'}`} />
          ))}
        </div>
      )}
    </div>
  );
}
