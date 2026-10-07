import type { LucideIcon } from 'lucide-react';
import { Button } from './Button';

export function EmptyState({ icon: Icon, title, body, action }: {
  icon: LucideIcon;
  title: string;
  body: string;
  action?: { label: string; onClick: () => void };
}) {
  return (
    <div className="flex flex-col items-center px-8 py-12 text-center">
      <div className="flex size-24 items-center justify-center rounded-full bg-primary-container text-on-primary-container">
        <Icon size={44} aria-hidden />
      </div>
      <h2 className="mt-5 text-xl font-bold">{title}</h2>
      <p className="mt-1.5 text-sm text-on-surface-variant">{body}</p>
      {action && <Button variant="tonal" className="mt-5" onClick={action.onClick}>{action.label}</Button>}
    </div>
  );
}

/** Shimmering placeholder rows shown while live queries load. */
export function SkeletonList({ rows = 4, className = 'h-24' }: { rows?: number; className?: string }) {
  return (
    <div className="flex flex-col gap-3" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className={`animate-shimmer rounded-md bg-[linear-gradient(90deg,var(--surface-high)_25%,var(--surface-lowest)_50%,var(--surface-high)_75%)] bg-[length:200%_100%] ${className}`} />
      ))}
    </div>
  );
}
