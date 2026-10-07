'use client';

import { SearchX } from 'lucide-react';
import { useParams, useRouter } from 'next/navigation';
import { useLive } from '@/app/providers';
import { EmptyState, SkeletonList } from '@/components/EmptyState';
import { ItemEditor } from '@/components/ItemEditor';
import { Screen } from '@/components/Screen';
import { formFromItem } from '@/lib/itemForm';
import { parseId } from '@/lib/routes';

export default function EditItem() {
  const id = parseId(useParams<{ id: string }>().id);
  const router = useRouter();
  // The form copies the item into its own state once, so later live updates (e.g. after saving) don't reset edits.
  const item = useLive(async s => (id == null ? null : (await s.catalog.item(id)) ?? null), [id]);
  return (
    <Screen title="Edit item" wide>
      {item === undefined ? <SkeletonList /> : item === null
        ? <EmptyState icon={SearchX} title="Item not found" body="It may have been removed." action={{ label: 'Go back', onClick: () => router.back() }} />
        : <ItemEditor key={item.id} itemId={item.id} initial={formFromItem(item)} />}
    </Screen>
  );
}
