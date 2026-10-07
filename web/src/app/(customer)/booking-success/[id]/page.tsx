'use client';

import { SearchX } from 'lucide-react';
import { useParams, useRouter } from 'next/navigation';
import { useLive } from '@/app/providers';
import { Button } from '@/components/Button';
import { EmptyState } from '@/components/EmptyState';
import { SuccessAnimation } from '@/components/SuccessAnimation';
import { bookingCode, formatRange } from '@/domain/format/dates';
import { bookingRange, DEMO_USER_ID } from '@/domain/models';
import { paths, parseId } from '@/lib/routes';

export default function BookingSuccess() {
  const id = parseId(useParams<{ id: string }>().id);
  const router = useRouter();
  const data = useLive(async s => {
    const booking = id == null ? undefined : await s.bookings.booking(id);
    if (!booking || booking.customerId !== DEMO_USER_ID) return null;
    return { booking, item: await s.catalog.item(booking.itemId) };
  }, [id]);

  if (data === null) {
    return (
      <main className="mx-auto flex min-h-dvh max-w-md flex-col justify-center p-6">
        <EmptyState icon={SearchX} title="Booking not found" body="We couldn't find that anymore." action={{ label: 'View my rentals', onClick: () => router.push(paths.rentals) }} />
      </main>
    );
  }

  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center p-6 text-center">
      <SuccessAnimation />
      <h1 className="text-3xl font-bold">Request sent to the store</h1>
      <p className="mt-2 whitespace-pre-line text-on-surface-variant">
        {data?.item
          ? `${data.item.title} · ${formatRange(bookingRange(data.booking))}\nBooking ${bookingCode(data.booking.id)}. We'll notify you as soon as the admin approves it.`
          : "We'll notify you as soon as the admin approves it."}
      </p>
      <Button className="mt-8 w-full" onClick={() => router.push(paths.rentals)}>View my rentals</Button>
      <Button variant="text" className="mt-1" onClick={() => router.push(paths.home)}>Back to home</Button>
    </main>
  );
}
