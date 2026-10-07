'use client';

import { CalendarCheck, CalendarDays, Heart, House, LayoutDashboard, Package, Search, ShieldCheck, User, Wallet } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useLive, useSession } from '@/app/providers';

const CUSTOMER_TABS = [
  { href: '/home', label: 'Home', Icon: House },
  { href: '/search', label: 'Search', Icon: Search },
  { href: '/rentals', label: 'Rentals', Icon: CalendarCheck },
  { href: '/saved', label: 'Saved', Icon: Heart },
  { href: '/profile', label: 'Profile', Icon: User },
];
const ADMIN_TABS = [
  { href: '/admin', label: 'Dashboard', Icon: LayoutDashboard },
  { href: '/admin/inventory', label: 'Inventory', Icon: Package },
  { href: '/admin/bookings', label: 'Bookings', Icon: CalendarDays },
  { href: '/admin/earnings', label: 'Earnings', Icon: Wallet },
  { href: '/profile', label: 'Admin', Icon: ShieldCheck },
];

/**
 * A top-level tab page. Customer screens sit in a phone-width column with a bottom bar.
 * Admin screens get a side rail and a wider column from the lg breakpoint.
 */
export function TabScaffold({ title, actions, children }: { title?: string; actions?: React.ReactNode; children: React.ReactNode }) {
  const { mode } = useSession();
  const pathname = usePathname();
  const admin = mode === 'ADMIN';
  const tabs = admin ? ADMIN_TABS : CUSTOMER_TABS;
  const pending = useLive(s => s.bookings.allBookings().then(all => all.filter(b => b.status === 'REQUESTED').length)) ?? 0;
  const badge = (label: string) => (admin && label === 'Bookings' && pending > 0 ? pending : 0);
  const isActive = (href: string) => (href === '/admin' ? pathname === '/admin' : pathname.startsWith(href));

  return (
    <div className={admin ? 'lg:flex' : ''}>
      {admin && (
        <nav aria-label="Admin" className="no-print sticky top-0 hidden h-dvh w-60 shrink-0 flex-col gap-1 bg-surface-container p-3 lg:flex">
          <p className="px-4 py-5 font-display text-lg font-extrabold text-primary">RentNest Admin</p>
          {tabs.map(t => (
            <Link key={t.href} href={t.href} aria-current={isActive(t.href) ? 'page' : undefined}
              className={`flex items-center gap-3 rounded-full px-4 py-3 text-sm font-semibold ${isActive(t.href) ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface-variant hover:bg-surface-high'}`}>
              <t.Icon size={20} aria-hidden /> {t.label}
              {badge(t.label) > 0 && <span className="ml-auto rounded-full bg-error px-2 text-xs text-white">{badge(t.label)}</span>}
            </Link>
          ))}
        </nav>
      )}
      <div className={`mx-auto w-full min-w-0 px-4 pb-28 pt-4 ${admin ? 'max-w-md lg:max-w-6xl lg:px-8 lg:pb-10' : 'max-w-md'}`}>
        {(title || actions) && (
          <header className="mb-4 flex min-h-12 items-center justify-between gap-3">
            {title && <h1 className="text-2xl font-extrabold">{title}</h1>}
            {actions}
          </header>
        )}
        {children}
      </div>
      <nav aria-label="Main" className={`no-print fixed inset-x-0 bottom-0 z-20 border-t border-outline-variant bg-surface-container pb-[env(safe-area-inset-bottom)] ${admin ? 'lg:hidden' : ''}`}>
        <ul className="mx-auto flex max-w-md justify-around">
          {tabs.map(t => (
            <li key={t.href}>
              <Link href={t.href} aria-current={isActive(t.href) ? 'page' : undefined} className="flex min-w-16 flex-col items-center gap-1 py-2 text-xs font-medium">
                <span className={`relative rounded-full px-4 py-1 transition ${isActive(t.href) ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface-variant'}`}>
                  <t.Icon size={22} aria-hidden />
                  {badge(t.label) > 0 && <span className="absolute -right-1 -top-1 rounded-full bg-error px-1.5 text-[10px] text-white">{badge(t.label)}</span>}
                </span>
                {t.label}
              </Link>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
