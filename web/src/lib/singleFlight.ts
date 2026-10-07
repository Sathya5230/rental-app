import type { Outcome } from '@/domain/errors';

/**
 * Wraps an action so repeat calls while it runs, or after it succeeded, get the first call's result
 * instead of running again. A failed run can be retried.
 */
export function singleFlight<T>(fn: () => Promise<Outcome<T>>): () => Promise<Outcome<T>> {
  let pending: Promise<Outcome<T>> | null = null;
  return () => {
    if (pending) return pending;
    pending = fn().then(
      r => { if (!r.ok) pending = null; return r; },
      e => { pending = null; throw e; },
    );
    return pending;
  };
}
