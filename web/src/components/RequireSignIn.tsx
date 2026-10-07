'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useSession } from '@/app/providers';
import { startPath } from '@/lib/routes';

/** Screens past the entry flow need onboarding and sign-in, like the Android start destination. */
export function RequireSignIn({ children }: { children: React.ReactNode }) {
  const session = useSession();
  const router = useRouter();
  const blocked = !session.onboarded || !session.loggedIn;
  useEffect(() => { if (blocked) router.replace(startPath(session)); }, [blocked, router, session]);
  return blocked ? null : children;
}
