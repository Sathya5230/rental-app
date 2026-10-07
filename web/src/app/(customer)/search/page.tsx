'use client';

import { ArrowUpDown, CalendarDays, SearchX, SlidersHorizontal, Store, X, Search as SearchIcon } from 'lucide-react';
import { useSearchParams } from 'next/navigation';
import { useState } from 'react';
import { useLive, useServices } from '@/app/providers';
import { Button } from '@/components/Button';
import { Dialog } from '@/components/Dialog';
import { EmptyState, SkeletonList } from '@/components/EmptyState';
import { inputClass } from '@/components/Field';
import { ItemCard } from '@/components/ItemCard';
import { TabScaffold } from '@/components/TabScaffold';
import type { DateRange } from '@/domain/dates';
import { formatRange } from '@/domain/format/dates';
import { DEMO_USER_ID, type Category } from '@/domain/models';
import { activeFilterCount, loadSnapshot, NO_FILTERS, search, SORT_LABELS, type SearchFilters, type SortOption } from '@/lib/catalog';

export default function SearchPage() {
  const params = useSearchParams();
  // Remount when the URL changes, so tapping a category (or the Search tab) starts from its filters.
  return <SearchContent key={params.toString()} category={Number(params.get('category')) || null} provider={Number(params.get('provider')) || null} />;
}

function SearchContent({ category, provider }: { category: number | null; provider: number | null }) {
  const { catalog } = useServices();
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<SearchFilters>({ ...NO_FILTERS, categoryId: category, providerId: provider });
  const [sort, setSort] = useState<SortOption>('RELEVANCE');
  const [showFilters, setShowFilters] = useState(false);
  const [showSort, setShowSort] = useState(false);
  const data = useLive(async s => ({ snap: await loadSnapshot(s.catalog), units: await s.inventory.allUnits(), bookings: await s.bookings.allBookings() }));

  const results = data ? search(data.snap, query, filters, sort, data.units, data.bookings) : [];
  const providerName = filters.providerId != null ? data?.snap.providers.get(filters.providerId)?.shopName : undefined;
  const count = activeFilterCount(filters);
  const clear = () => { setFilters(NO_FILTERS); setQuery(''); };

  return (
    <TabScaffold>
      <div className="relative">
        <SearchIcon className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-on-surface-variant" aria-hidden />
        <input type="search" aria-label="Search" placeholder="Search gear, providers…" value={query} onChange={e => setQuery(e.target.value)} className={`${inputClass} pl-12 pr-12`} />
        {query && (
          <button type="button" aria-label="Clear search" onClick={() => setQuery('')} className="absolute right-2 top-1/2 flex size-9 -translate-y-1/2 items-center justify-center rounded-full hover:bg-on-surface/8">
            <X size={18} />
          </button>
        )}
      </div>
      <div className="mt-3 flex items-center gap-2">
        <Chip selected={count > 0} onClick={() => setShowFilters(true)}><SlidersHorizontal size={16} aria-hidden /> {count > 0 ? `Filters · ${count}` : 'Filters'}</Chip>
        <Chip onClick={() => setShowSort(true)}><ArrowUpDown size={16} aria-hidden /> {SORT_LABELS[sort]}</Chip>
        <span className="ml-auto text-xs font-medium text-on-surface-variant" aria-live="polite">{data ? `${results.length} results` : ''}</span>
      </div>
      {providerName && (
        <button type="button" onClick={() => setFilters({ ...filters, providerId: null })} className="mt-2 inline-flex items-center gap-1.5 rounded-xs bg-secondary-container px-3 py-1.5 text-sm text-on-secondary-container">
          <Store size={16} aria-hidden /> {providerName} <X size={16} aria-label="Show all providers" />
        </button>
      )}
      <div className="mt-4">
        {!data ? <SkeletonList /> : results.length === 0 ? (
          <EmptyState icon={SearchX} title="No gear matches" body="Try a different word or loosen your filters." action={{ label: 'Clear filters', onClick: clear }} />
        ) : (
          <div className="grid grid-cols-[repeat(auto-fill,minmax(160px,1fr))] gap-3">
            {results.map(s => <ItemCard key={s.item.id} summary={s} onToggleFavourite={() => catalog.toggleFavourite(DEMO_USER_ID, s.item.id)} />)}
          </div>
        )}
      </div>

      <Dialog open={showSort} title="Sort by" onClose={() => setShowSort(false)}>
        <div className="flex flex-col">
          {(Object.keys(SORT_LABELS) as SortOption[]).map(o => (
            <button key={o} type="button" aria-pressed={sort === o} onClick={() => { setSort(o); setShowSort(false); }}
              className={`rounded-xs px-3 py-3 text-left ${sort === o ? 'bg-secondary-container font-semibold text-on-secondary-container' : 'hover:bg-on-surface/8'}`}>
              {SORT_LABELS[o]}
            </button>
          ))}
        </div>
      </Dialog>
      {data && (
        <FilterDialog open={showFilters} filters={filters} categories={data.snap.categories} onClose={() => setShowFilters(false)}
          onApply={f => { setFilters(f); setShowFilters(false); }} />
      )}
    </TabScaffold>
  );
}

function Chip({ selected = false, onClick, children }: { selected?: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick} aria-pressed={selected}
      className={`inline-flex h-8 items-center gap-1.5 rounded-xs border px-3 text-sm font-medium ${selected ? 'border-transparent bg-secondary-container text-on-secondary-container' : 'border-outline text-on-surface-variant'}`}>
      {children}
    </button>
  );
}

const MAX_PRICE_RUPEES = 2_500;

function FilterDialog({ open, filters, categories, onClose, onApply }: {
  open: boolean; filters: SearchFilters; categories: Category[]; onClose: () => void; onApply: (f: SearchFilters) => void;
}) {
  const [draft, setDraft] = useState(filters);
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) { setPrevOpen(open); if (open) setDraft(filters); }
  const rupees = draft.maxPrice == null ? MAX_PRICE_RUPEES : draft.maxPrice / 100;
  const setDates = (d: Partial<DateRange>) => {
    const start = d.start ?? draft.dates?.start ?? '';
    const end = d.end ?? draft.dates?.end ?? start;
    setDraft({ ...draft, dates: start ? { start, end: end < start ? start : end } : null });
  };

  return (
    <Dialog open={open} title="Filters" onClose={onClose}>
      <div className="flex max-h-[70dvh] flex-col gap-4 overflow-y-auto">
        <fieldset>
          <legend className="mb-2 text-sm font-semibold">Category</legend>
          <div className="flex flex-wrap gap-2">
            <Chip selected={draft.categoryId == null} onClick={() => setDraft({ ...draft, categoryId: null })}>All</Chip>
            {categories.map(c => <Chip key={c.id} selected={draft.categoryId === c.id} onClick={() => setDraft({ ...draft, categoryId: c.id })}>{c.name}</Chip>)}
          </div>
        </fieldset>
        <label className="block">
          <span className="mb-2 block text-sm font-semibold">Max price per day: {draft.maxPrice == null ? 'Any' : `₹${rupees}`}</span>
          <input type="range" min={100} max={MAX_PRICE_RUPEES} step={50} value={rupees} className="w-full accent-[var(--primary)]"
            onChange={e => { const v = Number(e.target.value); setDraft({ ...draft, maxPrice: v >= MAX_PRICE_RUPEES ? null : v * 100 }); }} />
        </label>
        <fieldset>
          <legend className="mb-2 text-sm font-semibold">Rating</legend>
          <div className="flex gap-2">
            {[null, 4.0, 4.5].map(r => <Chip key={String(r)} selected={draft.minRating === r} onClick={() => setDraft({ ...draft, minRating: r })}>{r == null ? 'Any' : `${r.toFixed(1)}+`}</Chip>)}
          </div>
        </fieldset>
        <fieldset>
          <legend className="mb-2 flex items-center gap-1.5 text-sm font-semibold"><CalendarDays size={16} aria-hidden /> Available on {draft.dates ? `· ${formatRange(draft.dates)}` : ''}</legend>
          <div className="grid grid-cols-2 gap-2">
            <label className="text-xs text-on-surface-variant">From<input type="date" className={`${inputClass} mt-1 px-2`} value={draft.dates?.start ?? ''} onChange={e => setDates({ start: e.target.value })} /></label>
            <label className="text-xs text-on-surface-variant">To<input type="date" className={`${inputClass} mt-1 px-2`} value={draft.dates?.end ?? ''} min={draft.dates?.start} onChange={e => setDates({ end: e.target.value })} /></label>
          </div>
          {draft.dates && <Button variant="text" className="mt-1" onClick={() => setDraft({ ...draft, dates: null })}>Any dates</Button>}
        </fieldset>
        <div className="flex gap-3">
          <Button variant="outlined" className="flex-1" onClick={() => setDraft({ ...NO_FILTERS, providerId: filters.providerId })}>Reset</Button>
          <Button className="flex-1" onClick={() => onApply(draft)}>Show results</Button>
        </div>
      </div>
    </Dialog>
  );
}
