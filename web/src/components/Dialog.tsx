'use client';

import { useEffect, useId, useRef } from 'react';

/** A modal on the native <dialog>: focus trapping, Escape and the backdrop come from the browser. */
export function Dialog({ open, title, onClose, children }: { open: boolean; title: string; onClose: () => void; children: React.ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onCancel={e => { e.preventDefault(); onClose(); }}
      onClick={e => { if (e.target === ref.current) onClose(); }}
      className="m-auto w-[calc(100%-32px)] max-w-sm rounded-lg bg-surface-high p-0 text-on-surface backdrop:bg-black/40"
    >
      <div className="p-6">
        <h2 id={titleId} className="mb-3 text-xl font-bold">{title}</h2>
        {open && children}
      </div>
    </dialog>
  );
}
