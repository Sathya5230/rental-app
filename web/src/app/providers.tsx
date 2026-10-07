'use client';

import { useLiveQuery } from 'dexie-react-hooks';
import { createContext, Suspense, useContext, useEffect, useState, useSyncExternalStore } from 'react';
import { Splash } from '@/components/Splash';
import { ToastProvider } from '@/components/Toast';
import { bootstrap, createBrowserServices, type Services } from '@/data/services';
import type { SessionState } from '@/data/sessionRepository';

const ServicesContext = createContext<Services | null>(null);

type Boot = { status: 'loading' } | { status: 'ready'; services: Services } | { status: 'error'; message: string };

export function AppProviders({ children }: { children: React.ReactNode }) {
  const [boot, setBoot] = useState<Boot>({ status: 'loading' });

  useEffect(() => {
    const services = createBrowserServices();
    bootstrap(services.db, services.time).then(r => setBoot(r.ok ? { status: 'ready', services } : { status: 'error', message: r.message }));
  }, []);

  if (boot.status === 'loading') return <Splash />;
  if (boot.status === 'error') return <Splash message={boot.message} />;
  return (
    <ServicesContext.Provider value={boot.services}>
      <ThemeSync />
      <ToastProvider>
        <Suspense fallback={<Splash />}>{children}</Suspense>
      </ToastProvider>
    </ServicesContext.Provider>
  );
}

/** Mirrors the session's mode and theme onto <html>, where the CSS tokens read them. */
function ThemeSync() {
  const { mode, theme } = useSession();
  useEffect(() => {
    const root = document.documentElement;
    root.dataset.mode = mode === 'ADMIN' ? 'admin' : 'customer';
    if (theme === 'SYSTEM') delete root.dataset.theme;
    else root.dataset.theme = theme === 'DARK' ? 'dark' : 'light';
  }, [mode, theme]);
  return null;
}

export function useServices(): Services {
  const s = useContext(ServicesContext);
  if (!s) throw new Error('useServices must be used inside AppProviders');
  return s;
}

export function useSession(): SessionState {
  const { session } = useServices();
  return useSyncExternalStore(session.subscribe, session.getSnapshot, session.getSnapshot);
}

/** Re-runs [query] whenever the tables it reads change, in this tab or another. undefined while loading. */
export function useLive<T>(query: (s: Services) => Promise<T>, deps: unknown[] = []): T | undefined {
  const s = useServices();
  return useLiveQuery(() => query(s), deps);
}
