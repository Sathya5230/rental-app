'use client';

import { TriangleAlert } from 'lucide-react';
import Link from 'next/link';
import { useEffect } from 'react';
import { EmptyState } from '@/components/EmptyState';

/** Last-resort boundary: a screen that throws shows this instead of blanking the app. */
export default function ScreenError({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  useEffect(() => { console.error(error); }, [error]);
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col justify-center p-6">
      <EmptyState icon={TriangleAlert} title="Something went wrong" body="This screen hit a problem. Try again, or go back home."
        action={{ label: 'Try again', onClick: retry }} />
      <Link href="/" className="mx-auto text-sm font-semibold text-primary">Go home</Link>
    </main>
  );
}
