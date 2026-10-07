'use client';

import { CalendarCheck, Compass, Store, type LucideIcon } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useRef, useState } from 'react';
import { Button } from '@/components/Button';
import { useServices } from '../providers';

const SLIDES: { Icon: LucideIcon; title: string; body: string }[] = [
  { Icon: Compass, title: 'Rent anything, nearby', body: 'Cameras, tools, camping kits and more from trusted local providers.' },
  { Icon: CalendarCheck, title: 'Book in seconds', body: 'Live availability, simple date picking and transparent pricing. No surprises.' },
  { Icon: Store, title: 'Approved by the store', body: "Your request goes to the store. Once it's approved, pay the advance at pickup and you're set." },
];

export default function Onboarding() {
  const { session } = useServices();
  const router = useRouter();
  const pager = useRef<HTMLDivElement>(null);
  const [page, setPage] = useState(0);
  const last = page === SLIDES.length - 1;

  const finish = () => {
    session.completeOnboarding();
    router.replace('/login');
  };
  const goTo = (i: number) => pager.current?.scrollTo({ left: i * pager.current.clientWidth, behavior: 'smooth' });

  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col p-6">
      <div className="flex justify-end">
        <Button variant="text" onClick={finish}>Skip</Button>
      </div>
      <div
        ref={pager}
        onScroll={e => setPage(Math.round(e.currentTarget.scrollLeft / e.currentTarget.clientWidth))}
        className="no-scrollbar flex flex-1 snap-x snap-mandatory overflow-x-auto"
      >
        {SLIDES.map(({ Icon, title, body }, i) => (
          <section key={title} aria-hidden={i !== page} className="flex w-full shrink-0 snap-center flex-col items-center justify-center text-center">
            <div className="relative flex size-64 items-center justify-center">
              <div className="absolute size-56 rounded-full bg-primary-container/50" />
              <div className="relative flex size-36 animate-float items-center justify-center rounded-full bg-[linear-gradient(135deg,var(--primary),color-mix(in_srgb,var(--primary)_70%,transparent))] text-white">
                <Icon size={72} aria-hidden />
              </div>
              <div className="absolute right-5 top-8 size-9 rounded-full bg-[#FFB866]" />
              <div className="absolute bottom-8 left-6 size-6 rounded-full bg-tertiary" />
            </div>
            <h1 className="mt-10 text-3xl font-extrabold">{title}</h1>
            <p className="mt-3 text-on-surface-variant">{body}</p>
          </section>
        ))}
      </div>
      <div className="flex justify-center gap-2 py-6" aria-hidden>
        {SLIDES.map((s, i) => (
          <span key={s.title} className={`h-2 rounded-full transition-all ${i === page ? 'w-7 bg-primary' : 'w-2 bg-outline-variant'}`} />
        ))}
      </div>
      <Button className="w-full" onClick={() => (last ? finish() : goTo(page + 1))}>{last ? 'Get started' : 'Next'}</Button>
    </main>
  );
}
