'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { Splash } from '@/components/Splash';
import { startPath } from '@/lib/routes';
import { useSession } from './providers';

export default function Start() {
  const session = useSession();
  const router = useRouter();
  useEffect(() => { router.replace(startPath(session)); }, [router, session]);
  return <Splash />;
}
