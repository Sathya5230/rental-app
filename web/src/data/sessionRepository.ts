import { DEFAULT_ADMIN_PIN, type AppMode, type ThemePref } from '@/domain/models';

export interface KeyValueStore {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export function memoryStore(): KeyValueStore {
  const m = new Map<string, string>();
  return { getItem: k => m.get(k) ?? null, setItem: (k, v) => void m.set(k, v), removeItem: k => void m.delete(k) };
}

/** The browser store if it works; private modes and blocked site data fall back to memory for this visit. */
export function safeStorage(get: () => Storage): KeyValueStore {
  try {
    const s = get();
    s.setItem('rn.probe', '1');
    s.removeItem('rn.probe');
    return s;
  } catch {
    return memoryStore();
  }
}

export interface SessionState {
  onboarded: boolean;
  loggedIn: boolean;
  modeChosen: boolean;
  mode: AppMode;
  theme: ThemePref;
  recentItemIds: number[];
}

const K = { onboarded: 'rn.onboarded', loggedIn: 'rn.loggedIn', mode: 'rn.mode', theme: 'rn.theme', recent: 'rn.recent', pin: 'rn.adminPinSha256' };
const UNLOCKED = 'rn.adminUnlocked';

export const isValidPin = (pin: string) => /^\d{4,6}$/.test(pin);

async function hashPin(pin: string): Promise<string> {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`rentnest-admin:${pin}`));
  return [...new Uint8Array(bytes)].map(b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Preferences live in [store] (localStorage). The admin unlock lives in [unlockStore] (sessionStorage),
 * so the dashboard locks again in a new tab, like the Android app after a restart.
 */
export class SessionRepository {
  private listeners = new Set<() => void>();
  private snapshot: SessionState;

  constructor(private store: KeyValueStore, private unlockStore: KeyValueStore) {
    this.snapshot = this.read();
  }

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  };

  getSnapshot = () => this.snapshot;

  completeOnboarding() { this.write(K.onboarded, 'true'); }
  logIn() { this.write(K.loggedIn, 'true'); }
  setTheme(pref: ThemePref) { this.write(K.theme, pref); }

  /** Choosing ADMIN only takes effect while the admin is unlocked. */
  chooseMode(mode: AppMode) {
    if (mode === 'ADMIN' && !this.adminUnlocked()) return;
    this.write(K.mode, mode);
  }

  recordView(itemId: number) {
    const recent = [itemId, ...this.snapshot.recentItemIds.filter(id => id !== itemId)].slice(0, 10);
    this.write(K.recent, recent.join(','));
  }

  logOut() {
    this.unlockStore.removeItem(UNLOCKED);
    this.store.removeItem(K.loggedIn);
    this.store.removeItem(K.mode);
    this.changed();
  }

  /** Unlocks the admin dashboard for this tab. False if [pin] is wrong. */
  async unlockAdmin(pin: string): Promise<boolean> {
    if (!(await this.pinMatches(pin))) return false;
    this.unlockStore.setItem(UNLOCKED, '1');
    this.changed();
    return true;
  }

  /** Locks the admin dashboard and returns to customer mode. */
  lockAdmin() {
    this.unlockStore.removeItem(UNLOCKED);
    this.write(K.mode, 'CUSTOMER');
  }

  async changeAdminPin(current: string, next: string): Promise<boolean> {
    if (!(await this.pinMatches(current)) || !isValidPin(next)) return false;
    this.store.setItem(K.pin, await hashPin(next));
    return true;
  }

  private adminUnlocked() { return this.unlockStore.getItem(UNLOCKED) === '1'; }

  private async pinMatches(pin: string) {
    return (await hashPin(pin)) === (this.store.getItem(K.pin) ?? (await hashPin(DEFAULT_ADMIN_PIN)));
  }

  private read(): SessionState {
    const stored = this.store.getItem(K.mode);
    const mode: AppMode = stored === 'ADMIN' && this.adminUnlocked() ? 'ADMIN' : 'CUSTOMER';
    const theme = this.store.getItem(K.theme);
    return {
      onboarded: this.store.getItem(K.onboarded) === 'true',
      loggedIn: this.store.getItem(K.loggedIn) === 'true',
      modeChosen: stored !== null,
      mode,
      theme: theme === 'LIGHT' || theme === 'DARK' ? theme : 'SYSTEM',
      recentItemIds: (this.store.getItem(K.recent) ?? '').split(',').filter(Boolean).map(Number),
    };
  }

  private write(key: string, value: string) {
    this.store.setItem(key, value);
    this.changed();
  }

  private changed() {
    this.snapshot = this.read();
    this.listeners.forEach(l => l());
  }
}
