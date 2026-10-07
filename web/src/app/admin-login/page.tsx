'use client';

import { ShieldCheck } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { Button } from '@/components/Button';
import { Field, TextInput } from '@/components/Field';
import { Screen } from '@/components/Screen';
import { useServices } from '../providers';

const MAX_ATTEMPTS = 5;

export default function AdminLogin() {
  const { session } = useServices();
  const router = useRouter();
  const [pin, setPin] = useState('');
  const [checking, setChecking] = useState(false);
  const [failures, setFailures] = useState(0);
  const [lockedOut, setLockedOut] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const unlock = async () => {
    if (checking || lockedOut || pin.length < 4) return;
    setChecking(true);
    setError(null);
    if (await session.unlockAdmin(pin)) {
      session.chooseMode('ADMIN');
      router.replace('/admin');
      return;
    }
    setChecking(false);
    setPin('');
    const count = failures + 1;
    if (count >= MAX_ATTEMPTS) {
      setFailures(0);
      setLockedOut(true);
      setError('Too many wrong PINs. Try again in 30 seconds.');
      setTimeout(() => { setLockedOut(false); setError(null); }, 30_000);
    } else {
      setFailures(count);
      setError(`Wrong PIN. ${MAX_ATTEMPTS - count} attempts left.`);
    }
  };

  return (
    <Screen title="Admin sign in">
      <form className="flex flex-col gap-4 pt-4" onSubmit={e => { e.preventDefault(); unlock(); }}>
        <ShieldCheck size={48} className="text-primary" aria-hidden />
        <h2 className="text-2xl font-bold">Store admin only</h2>
        <p className="text-on-surface-variant">
          The admin dashboard manages inventory, approves rental requests and texts customers about late returns. Enter the admin PIN to continue.
        </p>
        <Field label="Admin PIN" error={error}>
          <TextInput type="password" inputMode="numeric" autoComplete="off" value={pin} onChange={e => setPin(e.target.value.replace(/\D/g, '').slice(0, 6))} />
        </Field>
        <Button type="submit" className="w-full" disabled={pin.length < 4 || lockedOut} loading={checking}>Unlock dashboard</Button>
      </form>
    </Screen>
  );
}
