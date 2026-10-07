import { House, LoaderCircle } from 'lucide-react';

/** Shown while the database opens, and with [message] when the browser blocks storage. */
export function Splash({ message }: { message?: string }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 bg-primary-container p-6 text-on-primary-container">
      <div className="flex items-center gap-3">
        <House size={40} aria-hidden />
        <span className="font-display text-3xl font-extrabold">RentNest</span>
      </div>
      {message
        ? <p role="alert" className="max-w-sm rounded-md bg-surface-lowest p-5 text-center text-on-surface">{message}</p>
        : <LoaderCircle className="animate-spin" aria-label="Loading" />}
    </div>
  );
}
