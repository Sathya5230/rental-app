'use client';

import { KeyRound, Lock, LogOut, RotateCcw, ShieldCheck, Store } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { Button } from '@/components/Button';
import { Dialog } from '@/components/Dialog';
import { Field, TextInput } from '@/components/Field';
import { Avatar } from '@/components/ItemCard';
import { TabScaffold } from '@/components/TabScaffold';
import { useToast } from '@/components/Toast';
import { resetDemoData } from '@/data/seed/demoData';
import { isValidPin } from '@/data/sessionRepository';
import { ADMIN_USER_ID, DEMO_USER_ID, type ThemePref } from '@/domain/models';
import { useLive, useServices, useSession } from '../providers';

const THEMES: [ThemePref, string][] = [['SYSTEM', 'System'], ['LIGHT', 'Light'], ['DARK', 'Dark']];

export default function Profile() {
  const services = useServices();
  const { session } = services;
  const state = useSession();
  const router = useRouter();
  const toast = useToast();
  const isAdmin = state.mode === 'ADMIN';
  const data = useLive(async s => ({
    user: await s.catalog.user(isAdmin ? ADMIN_USER_ID : DEMO_USER_ID),
    store: await s.catalog.providerForUser(ADMIN_USER_ID),
  }), [isAdmin]);
  const [confirmReset, setConfirmReset] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [changingPin, setChangingPin] = useState(false);

  const reset = async () => {
    setConfirmReset(false);
    setResetting(true);
    await resetDemoData(services.db, services.time);
    setResetting(false);
    toast('Demo data restored');
  };

  return (
    <TabScaffold title={isAdmin ? 'Admin' : 'Profile'}>
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-4">
          <Avatar name={data?.user?.name ?? ''} size={64} />
          <div>
            <p className="font-display text-xl font-bold">{data?.user?.name}</p>
            <p className="text-sm text-on-surface-variant">{data?.user?.phone}</p>
          </div>
        </div>

        {isAdmin ? (
          <>
            {data?.store && (
              <div className="flex items-center gap-3 rounded-md bg-primary-container p-4 text-on-primary-container">
                <Store aria-hidden />
                <div>
                  <p className="font-display font-semibold">{data.store.shopName}</p>
                  <p className="text-xs">{data.store.locationText} · ★ {data.store.rating} ({data.store.reviewCount})</p>
                </div>
              </div>
            )}
            <SettingCard title="Admin security" subtitle="Only people with the PIN can open the admin dashboard. It locks again when you close this tab.">
              <Button variant="outlined" className="w-full" icon={<KeyRound size={18} aria-hidden />} onClick={() => setChangingPin(true)}>Change admin PIN</Button>
              <Button className="w-full" icon={<Lock size={18} aria-hidden />} onClick={() => { session.lockAdmin(); router.replace('/home'); }}>Lock & exit admin</Button>
            </SettingCard>
          </>
        ) : (
          <SettingCard title="Store admin" subtitle="Manage inventory, approve requests and handle returns.">
            <Button variant="outlined" className="w-full" icon={<ShieldCheck size={18} aria-hidden />} onClick={() => router.push('/admin-login')}>Open admin dashboard</Button>
          </SettingCard>
        )}

        <SettingCard title="Appearance">
          <div role="radiogroup" aria-label="Theme" className="grid grid-cols-3 overflow-hidden rounded-full border border-outline">
            {THEMES.map(([pref, label], i) => (
              <button key={pref} type="button" role="radio" aria-checked={state.theme === pref} onClick={() => session.setTheme(pref)}
                className={`h-10 text-sm font-medium ${i > 0 ? 'border-l border-outline' : ''} ${state.theme === pref ? 'bg-secondary-container text-on-secondary-container' : ''}`}>
                {label}
              </button>
            ))}
          </div>
        </SettingCard>

        {isAdmin && (
          <SettingCard title="Demo" subtitle="Restore all sample items, bookings and notifications.">
            <Button variant="outlined" className="w-full" icon={<RotateCcw size={18} aria-hidden />} disabled={resetting} onClick={() => setConfirmReset(true)}>
              {resetting ? 'Restoring…' : 'Reset demo data'}
            </Button>
          </SettingCard>
        )}

        {!isAdmin && (
          <Button variant="text" className="mx-auto" icon={<LogOut size={18} aria-hidden />} onClick={() => { session.logOut(); router.replace('/login'); }}>Log out</Button>
        )}
      </div>

      <Dialog open={confirmReset} title="Reset demo data?" onClose={() => setConfirmReset(false)}>
        <p className="text-on-surface-variant">All changes you made will be replaced with fresh sample data.</p>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="text" onClick={() => setConfirmReset(false)}>Cancel</Button>
          <Button variant="text" onClick={reset}>Reset</Button>
        </div>
      </Dialog>
      <ChangePinDialog open={changingPin} onClose={() => setChangingPin(false)}
        onSave={async (current, next) => {
          const ok = await session.changeAdminPin(current, next);
          if (ok) setChangingPin(false);
          toast(ok ? 'Admin PIN changed' : 'Current PIN is wrong');
        }} />
    </TabScaffold>
  );
}

function SettingCard({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-2.5 rounded-md bg-surface-low p-4">
      <h2 className="font-display text-base font-semibold">{title}</h2>
      {subtitle && <p className="text-sm text-on-surface-variant">{subtitle}</p>}
      {children}
    </section>
  );
}

function ChangePinDialog({ open, onClose, onSave }: { open: boolean; onClose: () => void; onSave: (current: string, next: string) => void }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) { setPrevOpen(open); setCurrent(''); setNext(''); setConfirm(''); }
  const valid = isValidPin(next) && next === confirm && current !== '';
  const pin = (v: string) => v.replace(/\D/g, '').slice(0, 6);
  return (
    <Dialog open={open} title="Change admin PIN" onClose={onClose}>
      <form className="flex flex-col gap-3" onSubmit={e => { e.preventDefault(); if (valid) onSave(current, next); }}>
        <Field label="Current PIN"><TextInput type="password" inputMode="numeric" value={current} onChange={e => setCurrent(pin(e.target.value))} /></Field>
        <Field label="New PIN (4–6 digits)"><TextInput type="password" inputMode="numeric" value={next} onChange={e => setNext(pin(e.target.value))} /></Field>
        <Field label="Repeat new PIN" error={confirm && confirm !== next ? "PINs don't match" : null}>
          <TextInput type="password" inputMode="numeric" value={confirm} onChange={e => setConfirm(pin(e.target.value))} />
        </Field>
        <div className="mt-3 flex justify-end gap-2">
          <Button variant="text" onClick={onClose}>Cancel</Button>
          <Button variant="text" type="submit" disabled={!valid}>Save</Button>
        </div>
      </form>
    </Dialog>
  );
}
