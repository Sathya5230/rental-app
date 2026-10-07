'use client';

import { ArrowLeft } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { IconButton } from './Button';

/** A pushed screen: top bar with back, title and actions, an optional sticky footer for the main action. */
export function Screen({ title, back = true, actions, footer, wide = false, children }: {
  title: string;
  back?: boolean;
  actions?: React.ReactNode;
  footer?: React.ReactNode;
  wide?: boolean;
  children: React.ReactNode;
}) {
  const router = useRouter();
  const width = wide ? 'max-w-md lg:max-w-4xl' : 'max-w-md';
  const goBack = () => (window.history.length > 1 ? router.back() : router.push('/'));
  return (
    <div className="min-h-dvh">
      <header className="no-print sticky top-0 z-10 bg-background/90 backdrop-blur">
        <div className={`mx-auto flex h-16 items-center gap-1 px-2 ${width}`}>
          {back && <IconButton label="Back" onClick={goBack}><ArrowLeft size={22} /></IconButton>}
          <h1 className="min-w-0 flex-1 truncate px-2 text-xl font-bold">{title}</h1>
          {actions}
        </div>
      </header>
      <main className={`mx-auto px-4 ${footer ? 'pb-36' : 'pb-16'} ${width}`}>{children}</main>
      {footer && (
        <div className="no-print fixed inset-x-0 bottom-0 z-20 border-t border-outline-variant bg-surface-container pb-[env(safe-area-inset-bottom)]">
          <div className={`mx-auto px-4 py-3 ${width}`}>{footer}</div>
        </div>
      )}
    </div>
  );
}
