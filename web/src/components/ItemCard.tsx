'use client';

import { Heart, Star } from 'lucide-react';
import Link from 'next/link';
import { formatMoney } from '@/domain/format/money';
import type { RatingSummary } from '@/domain/models';
import type { ItemSummary } from '@/lib/catalog';
import { paths } from '@/lib/routes';
import { ItemArt } from './ItemArt';

export function PriceText({ dailyRate }: { dailyRate: number }) {
  return (
    <span className="whitespace-nowrap text-sm">
      <span className="font-bold text-on-surface">{formatMoney(dailyRate)}</span>
      <span className="text-on-surface-variant"> /day</span>
    </span>
  );
}

export function RatingBadge({ summary }: { summary: RatingSummary | undefined }) {
  return (
    <span className="inline-flex items-center gap-0.5 whitespace-nowrap text-xs font-medium">
      <Star size={16} className="fill-[#F5A623] text-[#F5A623]" aria-hidden />
      {summary ? `${summary.average.toFixed(1)} (${summary.count})` : 'New'}
    </span>
  );
}

export function FavouriteButton({ active, onToggle, className = '' }: { active: boolean; onToggle: () => void; className?: string }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      aria-label={active ? 'Remove from saved' : 'Save'}
      onClick={e => { e.preventDefault(); e.stopPropagation(); onToggle(); }}
      className={`flex size-10 items-center justify-center rounded-full bg-surface/90 transition active:scale-90 ${className}`}
    >
      <Heart size={20} className={active ? 'scale-110 fill-[#E53950] text-[#E53950]' : 'text-on-surface'} aria-hidden />
    </button>
  );
}

/** Port of ItemCard: art, title, store, price per day and rating. The whole card links to the item. */
export function ItemCard({ summary, onToggleFavourite, className = '' }: { summary: ItemSummary; onToggleFavourite?: () => void; className?: string }) {
  const { item } = summary;
  return (
    <Link href={paths.item(item.id)} className={`block overflow-hidden rounded-md bg-surface-low transition hover:bg-surface-container ${className}`}>
      <div className="relative">
        <ItemArt photoKey={item.photos[0] ?? ''} className="aspect-[1.3] w-full rounded-md" />
        {onToggleFavourite && <FavouriteButton active={summary.isFavourite} onToggle={onToggleFavourite} className="absolute right-1.5 top-1.5" />}
      </div>
      <div className="px-3 py-2.5">
        <h3 className="truncate font-display text-sm font-semibold">{item.title}</h3>
        <p className="truncate text-xs text-on-surface-variant">{summary.providerName}</p>
        <div className="mt-1.5 flex items-center justify-between gap-1.5">
          <PriceText dailyRate={item.dailyRate} />
          <RatingBadge summary={summary.rating} />
        </div>
      </div>
    </Link>
  );
}

/** Initials in a tinted circle. */
export function Avatar({ name, size = 44 }: { name: string; size?: number }) {
  const initials = name.split(' ').filter(Boolean).slice(0, 2).map(w => w[0].toUpperCase()).join('');
  return (
    <span aria-hidden className="flex shrink-0 items-center justify-center rounded-full bg-secondary-container font-display text-sm font-semibold text-on-secondary-container" style={{ width: size, height: size }}>
      {initials}
    </span>
  );
}

export function SectionHeader({ title, action }: { title: string; action?: { label: string; href: string } }) {
  return (
    <div className="mb-2 flex items-center justify-between">
      <h2 className="text-xl font-bold">{title}</h2>
      {action && <Link href={action.href} className="rounded-full px-3 py-2 text-sm font-semibold text-primary hover:bg-primary/8">{action.label}</Link>}
    </div>
  );
}
