'use client';

import { Bell, Search, Trees } from 'lucide-react';
import Link from 'next/link';
import { useServices, useLive, useSession } from '@/app/providers';
import { CategoryIcon, categoryGradient } from '@/components/CategoryVisuals';
import { SkeletonList } from '@/components/EmptyState';
import { ItemCard, SectionHeader } from '@/components/ItemCard';
import { TabScaffold } from '@/components/TabScaffold';
import { DEMO_USER_ID } from '@/domain/models';
import { loadSnapshot, popular, summarize } from '@/lib/catalog';
import { paths } from '@/lib/routes';

function greeting(hour: number) {
  if (hour >= 5 && hour <= 11) return 'Good morning';
  if (hour >= 12 && hour <= 16) return 'Good afternoon';
  return 'Good evening';
}

export default function Home() {
  const { catalog } = useServices();
  const { recentItemIds } = useSession();
  const data = useLive(async s => ({
    snap: await loadSnapshot(s.catalog),
    user: await s.catalog.user(DEMO_USER_ID),
    unread: await s.notifications.unreadCount(DEMO_USER_ID, 'CUSTOMER'),
  }));

  if (!data) return <TabScaffold><SkeletonList /></TabScaffold>;
  const { snap, user, unread } = data;
  const byId = new Map(snap.items.map(i => [i.id, i]));
  // Skip ids that no longer exist (e.g. after a demo reset)
  const recent = recentItemIds.flatMap(id => (byId.has(id) ? [summarize(snap, byId.get(id)!)] : [])).slice(0, 6);
  const toggle = (id: number) => catalog.toggleFavourite(DEMO_USER_ID, id);

  return (
    <TabScaffold>
      <div className="flex flex-col gap-6">
        <header className="flex items-center gap-2">
          <div className="flex-1">
            <p className="text-on-surface-variant">{greeting(new Date().getHours())}, {user?.name.split(' ')[0]}</p>
            <h1 className="text-2xl font-bold">What will you rent today?</h1>
          </div>
          <Link href="/notifications" aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'} className="relative flex size-12 items-center justify-center rounded-full hover:bg-on-surface/8">
            <Bell aria-hidden />
            {unread > 0 && <span className="absolute right-1.5 top-1.5 rounded-full bg-error px-1.5 text-[10px] font-semibold text-white">{unread}</span>}
          </Link>
        </header>

        <Link href={paths.search()} className="flex h-[54px] items-center gap-3 rounded-sm bg-surface-high px-4 text-on-surface-variant">
          <Search aria-hidden /> Search cameras, tents, drills…
        </Link>

        <Link href={paths.search({ category: 3 })} className="relative overflow-hidden rounded-md bg-[linear-gradient(135deg,var(--primary),#134E5E)] p-5 text-white">
          <div className="w-[70%]">
            <h2 className="text-xl font-bold">Weekend getaway?</h2>
            <p className="mt-1 text-sm text-white/85">Tents, stoves and power stations. Weekly rates save up to 25%.</p>
            <span className="mt-3 inline-block rounded-full bg-white px-3.5 py-1.5 text-sm font-semibold text-[#134E5E]">Explore camping</span>
          </div>
          <Trees size={96} className="absolute right-4 top-1/2 -translate-y-1/2 text-white/25" aria-hidden />
        </Link>

        <nav aria-label="Categories" className="no-scrollbar -mx-4 flex gap-3 overflow-x-auto px-4">
          {snap.categories.map(c => {
            const [a, b] = categoryGradient(c.iconKey);
            return (
              <Link key={c.id} href={paths.search({ category: c.id })} className="flex w-[76px] shrink-0 flex-col items-center gap-1.5 rounded-sm py-1">
                <span className="flex size-[60px] items-center justify-center rounded-md text-white" style={{ backgroundImage: `linear-gradient(135deg, ${a}, ${b})` }}>
                  <CategoryIcon iconKey={c.iconKey} aria-hidden />
                </span>
                <span className="w-full truncate text-center text-xs font-medium">{c.name}</span>
              </Link>
            );
          })}
        </nav>

        <section>
          <SectionHeader title="Popular near you" action={{ label: 'See all', href: paths.search() }} />
          <Carousel>
            {popular(snap).map(s => <ItemCard key={s.item.id} summary={s} onToggleFavourite={() => toggle(s.item.id)} className="w-[200px] shrink-0 snap-start" />)}
          </Carousel>
        </section>

        {recent.length > 0 && (
          <section>
            <SectionHeader title="Recently viewed" />
            <Carousel>
              {recent.map(s => <ItemCard key={s.item.id} summary={s} className="w-[200px] shrink-0 snap-start" />)}
            </Carousel>
          </section>
        )}
      </div>
    </TabScaffold>
  );
}

function Carousel({ children }: { children: React.ReactNode }) {
  return <div className="no-scrollbar -mx-4 flex snap-x snap-mandatory scroll-px-4 gap-3 overflow-x-auto px-4 pb-1">{children}</div>;
}
