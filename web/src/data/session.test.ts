import { describe, expect, it } from 'vitest';
import { DEFAULT_ADMIN_PIN } from '@/domain/models';
import { paths, startPath } from '@/lib/routes';
import { memoryStore, safeStorage, SessionRepository, type SessionState } from './sessionRepository';

const repo = () => new SessionRepository(memoryStore(), memoryStore());

describe('session', () => {
  it('admin mode needs the right PIN', async () => {
    const r = repo();
    r.chooseMode('ADMIN');
    expect(r.getSnapshot().mode).toBe('CUSTOMER');
    expect(await r.unlockAdmin('0000')).toBe(false);
    expect(await r.unlockAdmin(DEFAULT_ADMIN_PIN)).toBe(true);
    r.chooseMode('ADMIN');
    expect(r.getSnapshot().mode).toBe('ADMIN');
    r.lockAdmin();
    expect(r.getSnapshot().mode).toBe('CUSTOMER');
  });

  it('a changed PIN replaces the default', async () => {
    const r = repo();
    expect(await r.changeAdminPin('9999', '4321')).toBe(false);
    expect(await r.changeAdminPin(DEFAULT_ADMIN_PIN, '12')).toBe(false); // too short
    expect(await r.changeAdminPin(DEFAULT_ADMIN_PIN, '4321')).toBe(true);
    expect(await r.unlockAdmin(DEFAULT_ADMIN_PIN)).toBe(false);
    expect(await r.unlockAdmin('4321')).toBe(true);
  });

  it('a new tab starts locked but remembers the chosen mode', async () => {
    const prefs = memoryStore();
    const first = new SessionRepository(prefs, memoryStore());
    await first.unlockAdmin(DEFAULT_ADMIN_PIN);
    first.chooseMode('ADMIN');
    const second = new SessionRepository(prefs, memoryStore());
    expect(second.getSnapshot()).toMatchObject({ mode: 'CUSTOMER', modeChosen: true });
  });

  it('log out locks admin and forgets login and mode', async () => {
    const r = repo();
    r.completeOnboarding();
    r.logIn();
    await r.unlockAdmin(DEFAULT_ADMIN_PIN);
    r.chooseMode('ADMIN');
    r.logOut();
    expect(r.getSnapshot()).toMatchObject({ onboarded: true, loggedIn: false, modeChosen: false, mode: 'CUSTOMER' });
  });

  it('keeps the ten most recent items, newest first', () => {
    const r = repo();
    for (let id = 1; id <= 12; id++) r.recordView(id);
    r.recordView(5);
    expect(r.getSnapshot().recentItemIds).toEqual([5, 12, 11, 10, 9, 8, 7, 6, 4, 3]);
  });

  it('notifies subscribers and keeps snapshots stable between changes', () => {
    const r = repo();
    const seen: SessionState[] = [];
    r.subscribe(() => seen.push(r.getSnapshot()));
    expect(r.getSnapshot()).toBe(r.getSnapshot());
    r.completeOnboarding();
    expect(seen.at(-1)!.onboarded).toBe(true);
  });

  it('falls back to memory when storage is blocked', () => {
    const store = safeStorage(() => { throw new Error('SecurityError'); });
    store.setItem('k', 'v');
    expect(store.getItem('k')).toBe('v');
  });
});

describe('start path', () => {
  const s = (o: Partial<SessionState>): SessionState => ({ onboarded: true, loggedIn: true, modeChosen: true, mode: 'CUSTOMER', theme: 'SYSTEM', recentItemIds: [], ...o });
  it('walks through onboarding, login and mode choice', () => {
    expect(startPath(s({ onboarded: false }))).toBe('/onboarding');
    expect(startPath(s({ loggedIn: false }))).toBe('/login');
    expect(startPath(s({ modeChosen: false }))).toBe('/choose-mode');
    expect(startPath(s({}))).toBe('/home');
    expect(startPath(s({ mode: 'ADMIN' }))).toBe('/admin');
  });
  it('builds routes', () => {
    expect(paths.checkout(11, '2026-10-17', '2026-10-19')).toBe('/checkout?item=11&start=2026-10-17&end=2026-10-19');
    expect(paths.bill(5, true, 'ADMIN')).toBe('/admin/bill/5?return=1');
    expect(paths.bill(5, true, 'CUSTOMER')).toBe('/rentals/5/bill');
  });
});
