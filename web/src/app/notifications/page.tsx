'use client';

import { Bell, BellOff } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { EmptyState, SkeletonList } from '@/components/EmptyState';
import { Screen } from '@/components/Screen';
import { formatRelative } from '@/domain/format/dates';
import { ADMIN_USER_ID, DEMO_USER_ID } from '@/domain/models';
import { paths } from '@/lib/routes';
import { useLive, useServices, useSession } from '../providers';

export default function Notifications() {
  const { notifications, time } = useServices();
  const { mode } = useSession();
  const router = useRouter();
  const recipient = mode === 'ADMIN' ? ADMIN_USER_ID : DEMO_USER_ID;
  const audience = mode === 'ADMIN' ? 'ADMIN' : 'CUSTOMER';
  const items = useLive(s => s.notifications.notifications(recipient, audience), [recipient, audience]);

  useEffect(() => {
    // Let the unread dots register before clearing them
    const t = setTimeout(() => notifications.markAllRead(recipient, audience), 1_500);
    return () => clearTimeout(t);
  }, [notifications, recipient, audience]);

  const open = () => router.push(mode === 'ADMIN' ? paths.adminBookings : paths.rentals);
  const now = time.nowMillis();

  return (
    <Screen title="Notifications">
      {!items ? <SkeletonList /> : items.length === 0 ? (
        <EmptyState icon={BellOff} title="All caught up" body="Booking updates will show up here." />
      ) : (
        <ul className="-mx-4">
          {items.map(n => {
            const body = (
              <>
                <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary-container text-on-primary-container"><Bell size={20} aria-hidden /></span>
                <span className="min-w-0 flex-1">
                  <span className="block font-display text-sm font-semibold">{n.title}</span>
                  <span className="block text-sm text-on-surface-variant">{n.body}</span>
                  <span className="block text-xs text-on-surface-variant">{formatRelative(n.createdAt, now)}</span>
                </span>
                {!n.isRead && <span className="mt-1.5 size-2.5 shrink-0 rounded-full bg-primary" aria-label="Unread" />}
              </>
            );
            return (
              <li key={n.id}>
                {n.bookingId != null
                  ? <button type="button" onClick={open} className="flex w-full items-start gap-3.5 px-5 py-3.5 text-left hover:bg-on-surface/5">{body}</button>
                  : <div className="flex items-start gap-3.5 px-5 py-3.5">{body}</div>}
              </li>
            );
          })}
        </ul>
      )}
    </Screen>
  );
}
