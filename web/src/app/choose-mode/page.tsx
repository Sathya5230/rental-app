'use client';

import { ArrowRight, Search, ShieldCheck } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useServices } from '../providers';

export default function ChooseMode() {
  const { session } = useServices();
  const router = useRouter();

  const rent = () => {
    session.chooseMode('CUSTOMER');
    router.replace('/home');
  };

  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col justify-center p-6">
      <h1 className="text-3xl font-extrabold">Welcome to RentNest</h1>
      <p className="mt-2 text-on-surface-variant">Pick your dates, send a request, and collect your gear once the store approves it.</p>
      <button type="button" onClick={rent} className="mt-8 flex items-center gap-4 rounded-md bg-surface-low p-5 text-left transition hover:bg-surface-container">
        <span className="flex size-14 shrink-0 items-center justify-center rounded-full bg-[#00695C] text-white"><Search aria-hidden /></span>
        <span className="flex-1">
          <span className="block font-display text-xl font-bold">I want to rent</span>
          <span className="block text-sm text-on-surface-variant">Browse gear nearby, book by the day or week.</span>
        </span>
        <ArrowRight aria-hidden />
      </button>
      <Link href="/admin-login" className="mx-auto mt-6 inline-flex items-center gap-1.5 rounded-full px-4 py-2.5 text-sm font-semibold text-primary hover:bg-primary/8">
        <ShieldCheck size={18} aria-hidden /> Store admin? Sign in
      </Link>
    </main>
  );
}
