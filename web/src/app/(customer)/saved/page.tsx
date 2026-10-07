'use client';

import { Heart } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useLive, useServices } from '@/app/providers';
import { EmptyState, SkeletonList } from '@/components/EmptyState';
import { ItemCard } from '@/components/ItemCard';
import { TabScaffold } from '@/components/TabScaffold';
import { DEMO_USER_ID } from '@/domain/models';
import { loadSnapshot, summarize } from '@/lib/catalog';
import { paths } from '@/lib/routes';

export default function Saved() {
  const { catalog } = useServices();
  const router = useRouter();
  const saved = useLive(async s => {
    const snap = await loadSnapshot(s.catalog);
    return snap.items.filter(i => snap.favourites.has(i.id)).map(i => summarize(snap, i));
  });

  return (
    <TabScaffold title="Saved">
      {!saved ? <SkeletonList /> : saved.length === 0 ? (
        <EmptyState icon={Heart} title="Nothing saved yet" body="Tap the heart on any item to keep it here." action={{ label: 'Explore gear', onClick: () => router.push(paths.search()) }} />
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(160px,1fr))] gap-3">
          {saved.map(s => <ItemCard key={s.item.id} summary={s} onToggleFavourite={() => catalog.toggleFavourite(DEMO_USER_ID, s.item.id)} />)}
        </div>
      )}
    </TabScaffold>
  );
}
