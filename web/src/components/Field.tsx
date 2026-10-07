'use client';

import { cloneElement, useId, type InputHTMLAttributes, type ReactElement } from 'react';

export const inputClass =
  'h-12 w-full rounded-sm border border-outline bg-surface-lowest px-4 text-base text-on-surface outline-none placeholder:text-on-surface-variant/60 focus:border-primary focus:ring-2 focus:ring-primary/30 aria-[invalid=true]:border-error';

type ControlProps = { id?: string; 'aria-describedby'?: string; 'aria-invalid'?: boolean };

/** A labelled form control. The label, hint and error are wired to the control by id, so `getByLabel(label)` finds it. */
export function Field({ label, error, hint, className = '', children }: {
  label: string;
  error?: string | null;
  hint?: string;
  className?: string;
  children: ReactElement<ControlProps>;
}) {
  const id = useId();
  const noteId = `${id}-note`;
  const note = error || hint;
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-on-surface-variant">{label}</label>
      {cloneElement(children, { id, 'aria-describedby': note ? noteId : undefined, 'aria-invalid': error ? true : undefined })}
      {note && (
        <p id={noteId} role={error ? 'alert' : undefined} className={`mt-1 text-sm ${error ? 'text-error' : 'text-on-surface-variant'}`}>{note}</p>
      )}
    </div>
  );
}

/** A text input, optionally with a fixed prefix such as "+91 " or "₹". */
export function TextInput({ prefix, className = '', ...props }: InputHTMLAttributes<HTMLInputElement> & { prefix?: string }) {
  if (!prefix) return <input {...props} className={`${inputClass} ${className}`} />;
  return (
    <div className={`flex h-12 items-center rounded-sm border border-outline bg-surface-lowest focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/30 has-[[aria-invalid=true]]:border-error ${className}`}>
      <span className="pl-4 text-on-surface-variant">{prefix}</span>
      <input {...props} className="h-full w-full min-w-0 rounded-sm bg-transparent pl-1 pr-4 text-base text-on-surface outline-none" />
    </div>
  );
}
