'use client';

import { createContext, useCallback, useContext, useEffect, useState } from 'react';

const ToastContext = createContext<(message: string) => void>(() => {});

/** One snackbar at a time, above the tab bar, read out by screen readers. */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toast, setToast] = useState<{ text: string; key: number } | null>(null);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(t);
  }, [toast]);

  const show = useCallback((text: string) => setToast({ text, key: Date.now() }), []);

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div role="status" aria-live="polite" className="no-print pointer-events-none fixed inset-x-0 bottom-24 z-50 flex justify-center px-4 lg:bottom-8">
        {toast && (
          <div key={toast.key} className="pointer-events-auto max-w-md rounded-sm bg-on-surface px-4 py-3 text-sm text-surface shadow-lg">{toast.text}</div>
        )}
      </div>
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);
