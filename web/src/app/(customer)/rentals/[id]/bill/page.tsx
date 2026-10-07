'use client';

import { useParams } from 'next/navigation';
import { BillActions, BillView, useBill } from '@/components/BillView';
import { Screen } from '@/components/Screen';
import { DEMO_USER_ID } from '@/domain/models';

/** The customer's copy of a closed rental's bill. Only their own bookings are shown. */
export default function CustomerBill() {
  const id = Number(useParams<{ id: string }>().id);
  const data = useBill(id, DEMO_USER_ID);
  return (
    <Screen title="Closing bill" footer={<BillActions data={data} isReturn />}>
      <BillView data={data} isReturn />
    </Screen>
  );
}
