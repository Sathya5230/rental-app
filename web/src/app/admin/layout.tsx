'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { startPath } from '@/lib/routes';
import { useSession } from '../providers';

/** Every /admin page needs a signed-in user who unlocked the dashboard with the PIN in this tab. */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const session = useSession();
  const router = useRouter();
  const signedIn = session.onboarded && session.loggedIn;
  const allowed = signedIn && session.mode === 'ADMIN';
  useEffect(() => {
    if (!signedIn) router.replace(startPath(session));
    else if (!allowed) router.replace('/admin-login');
  }, [allowed, router, session, signedIn]);
  return allowed ? children : null;
}
