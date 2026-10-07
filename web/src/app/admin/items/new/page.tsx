'use client';

import { ItemEditor } from '@/components/ItemEditor';
import { Screen } from '@/components/Screen';
import { EMPTY_FORM } from '@/lib/itemForm';

export default function NewItem() {
  return (
    <Screen title="New item" wide>
      <ItemEditor itemId={0} initial={EMPTY_FORM} />
    </Screen>
  );
}
