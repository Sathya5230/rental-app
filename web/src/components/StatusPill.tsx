import { Ban, CalendarCheck, CircleCheck, CircleMinus, CirclePlay, Hourglass } from 'lucide-react';
import type { BookingStatus } from '@/domain/models';

const STYLES: Record<BookingStatus, { label: string; Icon: typeof Hourglass; bg: string; fg: string }> = {
  REQUESTED: { label: 'Awaiting approval', Icon: Hourglass, bg: 'var(--st-requested-bg)', fg: 'var(--st-requested-fg)' },
  ACCEPTED: { label: 'Approved', Icon: CalendarCheck, bg: 'var(--st-accepted-bg)', fg: 'var(--st-accepted-fg)' },
  ACTIVE: { label: 'Active', Icon: CirclePlay, bg: 'var(--st-active-bg)', fg: 'var(--st-active-fg)' },
  RETURNED: { label: 'Closed', Icon: CircleCheck, bg: 'var(--surface-highest)', fg: 'var(--on-surface-variant)' },
  DECLINED: { label: 'Declined', Icon: CircleMinus, bg: 'var(--st-bad-bg)', fg: 'var(--st-bad-fg)' },
  CANCELLED: { label: 'Cancelled', Icon: Ban, bg: 'var(--st-bad-bg)', fg: 'var(--st-bad-fg)' },
};

export const statusLabel = (s: BookingStatus) => STYLES[s].label;

export function StatusPill({ status }: { status: BookingStatus }) {
  const { label, Icon, bg, fg } = STYLES[status];
  return (
    <span className="inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold" style={{ background: bg, color: fg }}>
      <Icon size={14} aria-hidden /> {label}
    </span>
  );
}
