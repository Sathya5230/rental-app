'use client';

import { House } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { Button } from '@/components/Button';
import { Field, inputClass, TextInput } from '@/components/Field';
import { nationalDigits } from '@/domain/format/phone';
import { DEMO_USER_ID } from '@/domain/models';
import { useServices } from '../providers';

const digits = (v: string, max: number) => v.replace(/\D/g, '').slice(0, max);

export default function Login() {
  const { catalog, session } = useServices();
  const router = useRouter();
  const [phone, setPhone] = useState('');
  const [otpStep, setOtpStep] = useState(false);
  const [otp, setOtp] = useState('');
  const [verifying, setVerifying] = useState(false);
  const phoneValid = nationalDigits(phone) != null;

  const verify = async () => {
    setVerifying(true);
    await new Promise(r => setTimeout(r, 800));
    await catalog.updatePhone(DEMO_USER_ID, phone);
    session.logIn();
    router.replace('/choose-mode');
  };

  return (
    <main className="mx-auto min-h-dvh max-w-md p-6 pt-16">
      <House size={48} className="text-primary" aria-hidden />
      <h1 className="mt-6 text-3xl font-extrabold">Welcome to RentNest</h1>
      {!otpStep ? (
        <form onSubmit={e => { e.preventDefault(); if (phoneValid) setOtpStep(true); }}>
          <p className="mt-2 text-on-surface-variant">Sign in with your mobile number to continue.</p>
          <Field className="mt-8" label="Mobile number" hint="We'll text you here about your rentals."
            error={phone.length === 10 && !phoneValid ? 'Enter a valid 10-digit mobile number.' : null}>
            <TextInput prefix="+91 " inputMode="tel" autoComplete="tel-national" value={phone} onChange={e => setPhone(digits(e.target.value, 10))} />
          </Field>
          <Button type="submit" className="mt-6 w-full" disabled={!phoneValid}>Send OTP</Button>
        </form>
      ) : (
        <form onSubmit={e => { e.preventDefault(); if (otp.length === 4 && !verifying) verify(); }}>
          <p className="mt-2 text-on-surface-variant">Enter the 4-digit code sent to +91 {phone.slice(0, 5)} {phone.slice(5)}</p>
          <Field className="mt-8" label="One-time code" hint="Demo mode: any 4 digits work.">
            <input
              autoFocus inputMode="numeric" autoComplete="one-time-code" maxLength={4} value={otp}
              onChange={e => setOtp(digits(e.target.value, 4))}
              className={`${inputClass} h-16 text-center font-display text-2xl tracking-[0.8em]`}
            />
          </Field>
          <Button type="submit" className="mt-6 w-full" disabled={otp.length !== 4} loading={verifying}>Verify & continue</Button>
          <div className="mt-2 flex justify-center">
            <Button variant="text" onClick={() => { setOtpStep(false); setOtp(''); }}>Change number</Button>
          </div>
        </form>
      )}
    </main>
  );
}
