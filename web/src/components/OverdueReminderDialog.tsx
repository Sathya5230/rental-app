'use client';

import { Check, Copy, MessageSquare } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useServices } from '@/app/providers';
import { smsLink, type SmsDraft } from '@/data/overdueReminders';
import { errorMessage } from '@/domain/errors';
import { Button } from './Button';
import { Dialog } from './Dialog';
import { useToast } from './Toast';

/**
 * A browser can't send SMS by itself, so the admin sends the overdue text from their own phone:
 * copy it or open the SMS app, then mark it as sent so the customer also gets an in-app notice.
 */
export function OverdueReminderDialog({ bookingId, onClose }: { bookingId: number | null; onClose: () => void }) {
  const { reminders } = useServices();
  const toast = useToast();
  const [draft, setDraft] = useState<SmsDraft | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (bookingId == null) return;
    let live = true;
    reminders.draft(bookingId).then(r => {
      if (!live) return;
      if (r.ok) { setDraft(r.value); setError(null); } else { setDraft(null); setError(errorMessage(r.error)); }
    });
    return () => { live = false; };
  }, [bookingId, reminders]);

  const copy = async () => {
    if (!draft) return;
    await navigator.clipboard.writeText(draft.message);
    toast('Message copied');
  };
  const markSent = async () => {
    if (bookingId == null) return;
    const r = await reminders.markSent(bookingId);
    toast(r.ok ? 'Reminder recorded. The customer was notified in the app.' : errorMessage(r.error));
    onClose();
  };

  return (
    <Dialog open={bookingId != null} title="Text the customer" onClose={onClose}>
      {error && <p role="alert" className="text-error">{error}</p>}
      {draft && (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-on-surface-variant">To {draft.phone}</p>
          <p className="rounded-sm bg-surface-lowest p-3 text-sm">{draft.message}</p>
          <div className="grid grid-cols-2 gap-2">
            <Button variant="outlined" icon={<Copy size={18} aria-hidden />} onClick={copy}>Copy message</Button>
            <a href={smsLink(draft)} className="inline-flex min-h-[54px] items-center justify-center gap-2 rounded-sm border border-outline px-4 text-sm font-semibold text-primary">
              <MessageSquare size={18} aria-hidden /> Open SMS app
            </a>
          </div>
          <Button icon={<Check size={18} aria-hidden />} onClick={markSent}>Mark as sent</Button>
          <p className="text-xs text-on-surface-variant">Send it from your phone, then mark it as sent so the reminder is recorded.</p>
        </div>
      )}
    </Dialog>
  );
}
