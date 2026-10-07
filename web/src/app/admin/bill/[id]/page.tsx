'use client';

import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { BillActions, BillView, useBill } from '@/components/BillView';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { paths, parseId } from '@/lib/routes';

export default function AdminBill() {
  const id = parseId(useParams<{ id: string }>().id);
  const isReturn = useSearchParams().get('return') === '1';
  const router = useRouter();
  const data = useBill(id);
  return (
    <Screen title={isReturn ? 'Closing bill' : 'Pickup receipt'} footer={
      <BillActions data={data} isReturn={isReturn}>
        <Button className="flex-1" onClick={() => router.push(paths.adminBookings)}>Done</Button>
      </BillActions>
    }>
      <BillView data={data} isReturn={isReturn} />
    </Screen>
  );
}
