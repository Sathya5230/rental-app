'use client';

import { LoaderCircle } from 'lucide-react';
import type { ButtonHTMLAttributes, ReactNode } from 'react';

type Variant = 'filled' | 'tonal' | 'outlined' | 'text' | 'danger';

const VARIANTS: Record<Variant, string> = {
  filled: 'bg-primary text-on-primary hover:brightness-110 px-6',
  tonal: 'bg-secondary-container text-on-secondary-container hover:brightness-95 px-6',
  outlined: 'border border-outline text-primary hover:bg-primary/8 px-6',
  text: 'text-primary hover:bg-primary/8 px-3',
  danger: 'bg-error text-white hover:brightness-110 px-6',
};

type Props = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; loading?: boolean; icon?: ReactNode };

/** Mirrors PrimaryButton: while loading it shows a spinner but keeps its label for screen readers and tests. */
export function Button({ variant = 'filled', loading = false, icon, className = '', children, disabled, ...rest }: Props) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex min-h-[54px] items-center justify-center gap-2 rounded-sm text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-40 ${VARIANTS[variant]} ${className}`}
    >
      {loading ? <LoaderCircle className="animate-spin" size={20} aria-hidden /> : icon}
      <span className={loading ? 'sr-only' : ''}>{children}</span>
    </button>
  );
}

export function IconButton({ label, className = '', children, ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button type="button" aria-label={label} title={label} {...rest}
      className={`inline-flex size-12 shrink-0 items-center justify-center rounded-full text-on-surface hover:bg-on-surface/8 ${className}`}>
      {children}
    </button>
  );
}
