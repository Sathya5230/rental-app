import type { Outcome } from '@/domain/errors';

/**
 * Wraps an action so repeat calls while it runs, or after it succeeded, get the first call's result
 * instead of running again. A failed run can be retried. Arguments of repeat calls are ignored.
 */
export function singleFlight<A extends unknown[], T>(fn: (...args: A) => Promise<Outcome<T>>): (...args: A) => Promise<Outcome<T>> {
  let pending: Promise<Outcome<T>> | null = null;
  return (...args: A) => {
    if (pending) return pending;
    pending = fn(...args).then(
      r => { if (!r.ok) pending = null; return r; },
      e => { pending = null; throw e; },
    );
    return pending;
  };
}

/**
 * Wraps an action so taps while it runs share that run, but a new call after it settles runs again.
 * For repeatable actions, like saving a form that stays on screen.
 */
export function exclusive<A extends unknown[], T>(fn: (...args: A) => Promise<Outcome<T>>): (...args: A) => Promise<Outcome<T>> {
  let pending: Promise<Outcome<T>> | null = null;
  return (...args: A) => {
    if (pending) return pending;
    pending = fn(...args).finally(() => { pending = null; });
    return pending;
  };
}
