'use client';

import { ArrowDownToLine, ArrowUpFromLine, Bell, ChevronRight, CirclePlay, Handshake, Inbox, MailWarning, MessageSquare, PackageSearch, Plus, TriangleAlert, Wrench } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { useLive } from '@/app/providers';
import { SkeletonList } from '@/components/EmptyState';
import { OverdueReminderDialog } from '@/components/OverdueReminderDialog';
import { TabScaffold } from '@/components/TabScaffold';
import { formatRange, formatRelative, formatShort } from '@/domain/format/dates';
import { formatMoney } from '@/domain/format/money';
import { ADMIN_USER_ID, bookingRange } from '@/domain/models';
import type { StockAlert } from '@/domain/rules/stockAlerts';
import { paths } from '@/lib/routes';
import { dashboardStats, loadShop, type ScheduleEntry, type VendorReturn } from '@/lib/shop';

function greeting(hour: number) {
  if (hour >= 5 && hour <= 11) return 'Good morning';
  if (hour >= 12 && hour <= 16) return 'Good afternoon';
  return 'Good evening';
}

export default function Dashboard() {
  const [smsFor, setSmsFor] = useState<number | null>(null);
  const data = useLive(async s => {
    const shop = await loadShop(s);
    if (!shop) return null;
    return {
      shop,
      stats: dashboardStats(s.time.today(), shop.items, shop.units, shop.bookings, shop.userNames),
      unread: await s.notifications.unreadCount(ADMIN_USER_ID, 'ADMIN'),
      now: s.time.nowMillis(),
    };
  });

  if (!data) return <TabScaffold><SkeletonList /></TabScaffold>;
  const { shop, stats, unread, now } = data;
  const today = stats.schedule.filter(e => e.kind !== 'OVERDUE');

  return (
    <TabScaffold>
      <header className="mb-4 flex items-center gap-2">
        <div className="flex-1">
          <p className="text-on-surface-variant">{greeting(new Date().getHours())}, {shop.userNames.get(ADMIN_USER_ID)?.split(' ')[0]}</p>
          <h1 className="text-2xl font-bold">{shop.provider.shopName}</h1>
        </div>
        <Link href="/notifications" aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'} className="relative flex size-12 items-center justify-center rounded-full hover:bg-on-surface/8">
          <Bell aria-hidden />
          {unread > 0 && <span className="absolute right-1.5 top-1.5 rounded-full bg-error px-1.5 text-[10px] font-semibold text-white">{unread}</span>}
        </Link>
      </header>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:gap-8">
        <div className="flex flex-col gap-4">
          <section className="flex items-center rounded-md bg-primary p-5 text-on-primary">
            <div className="flex-1">
              <p className="text-sm">Earned in the last 7 days</p>
              <p className="font-display text-4xl font-extrabold">{formatMoney(stats.weekEarnings)}</p>
            </div>
            <UtilisationRing percent={stats.utilisationPercent} />
          </section>
          <div className="grid grid-cols-2 gap-3">
            <Kpi label="Pending requests" value={stats.pendingRequests} icon={MailWarning} />
            <Kpi label="Active rentals" value={stats.activeRentals} icon={CirclePlay} />
            <Kpi label="Pickups due" value={stats.pickupsToday} icon={ArrowUpFromLine} />
            <Kpi label="Returns due" value={stats.returnsToday} icon={ArrowDownToLine} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Link href={paths.itemEditor()} className="flex min-h-[52px] items-center justify-center gap-1.5 rounded-sm bg-secondary-container font-semibold text-on-secondary-container"><Plus aria-hidden /> Add item</Link>
            <Link href={paths.adminBookings} className="flex min-h-[52px] items-center justify-center gap-1.5 rounded-sm bg-secondary-container font-semibold text-on-secondary-container"><Inbox aria-hidden /> Requests</Link>
          </div>
        </div>

        <div className="flex flex-col gap-4">
          {stats.overdue.length > 0 && (
            <section className="flex flex-col gap-3">
              <h2 className="text-xl font-bold">Overdue returns</h2>
              {stats.overdue.map(e => <OverdueRow key={e.booking.id} e={e} now={now} onSms={() => setSmsFor(e.booking.id)} />)}
            </section>
          )}
          <section className="flex flex-col gap-3">
            <h2 className="text-xl font-bold">Today</h2>
            {today.length === 0 && <p className="text-on-surface-variant">No pickups or returns due today.</p>}
            {today.map(e => <ScheduleRow key={e.booking.id} e={e} />)}
          </section>
          <section className="flex flex-col gap-3">
            <h2 className="text-xl font-bold">Needs attention</h2>
            {stats.alerts.length === 0 && stats.vendorReturns.length === 0 && <p className="text-on-surface-variant">All stock looks healthy.</p>}
            {stats.vendorReturns.map(v => <VendorReturnRow key={`v${v.item.id}`} v={v} />)}
            {stats.alerts.map(a => <AlertRow key={`${a.kind}${a.itemId}`} a={a} />)}
          </section>
        </div>
      </div>
      <OverdueReminderDialog bookingId={smsFor} onClose={() => setSmsFor(null)} />
    </TabScaffold>
  );
}

function UtilisationRing({ percent }: { percent: number }) {
  const c = 2 * Math.PI * 30;
  return (
    <div className="relative size-[72px]" role="img" aria-label={`${percent}% of units in use`}>
      <svg viewBox="0 0 72 72" className="size-full -rotate-90">
        <circle cx="36" cy="36" r="30" fill="none" stroke="currentColor" strokeOpacity="0.25" strokeWidth="7" />
        <circle cx="36" cy="36" r="30" fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round" strokeDasharray={c} strokeDashoffset={c * (1 - percent / 100)} />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center leading-tight" aria-hidden>
        <span className="font-display font-semibold">{percent}%</span>
        <span className="text-[10px]">in use</span>
      </div>
    </div>
  );
}

function Kpi({ label, value, icon: Icon }: { label: string; value: number; icon: typeof Inbox }) {
  return (
    <Link href={paths.adminBookings} className="flex flex-col gap-1 rounded-md bg-surface-low p-4 hover:bg-surface-container">
      <Icon size={22} className="text-primary" aria-hidden />
      <span className="font-display text-2xl font-bold">{value}</span>
      <span className="text-sm text-on-surface-variant">{label}</span>
    </Link>
  );
}

function Row({ href, icon: Icon, tone, title, body, bodyClass = 'text-on-surface-variant' }: {
  href: string; icon: typeof Inbox; tone: string; title: string; body: string; bodyClass?: string;
}) {
  return (
    <Link href={href} className="flex items-center gap-3 rounded-sm bg-surface-low p-3.5 hover:bg-surface-container">
      <span className={`flex size-10 shrink-0 items-center justify-center rounded-full ${tone}`}><Icon size={20} aria-hidden /></span>
      <span className="min-w-0 flex-1">
        <span className="block font-display text-sm font-semibold">{title}</span>
        <span className={`block text-xs ${bodyClass}`}>{body}</span>
      </span>
      <ChevronRight size={20} aria-hidden />
    </Link>
  );
}

function ScheduleRow({ e }: { e: ScheduleEntry }) {
  const label = e.kind === 'PICKUP' ? 'Pickup' : 'Return';
  return <Row href={paths.adminBookings} icon={e.kind === 'PICKUP' ? ArrowUpFromLine : ArrowDownToLine} tone="bg-secondary-container text-on-secondary-container"
    title={e.itemTitle} body={`${label} · ${e.customerName} · ${formatRange(bookingRange(e.booking))}`} />;
}

function OverdueRow({ e, now, onSms }: { e: ScheduleEntry; now: number; onSms: () => void }) {
  const b = e.booking;
  return (
    <article className="flex flex-col gap-2 rounded-sm bg-error-container p-3.5 text-on-error-container">
      <Link href={paths.adminBookings} className="flex items-center gap-2.5">
        <TriangleAlert aria-hidden />
        <span>
          <span className="block font-display text-sm font-semibold">{e.itemTitle}</span>
          <span className="block text-xs">{e.customerName} · {b.contactPhone}</span>
        </span>
      </Link>
      <p className="text-xs">Due {formatShort(b.endDate)} · late fee so far {formatMoney(e.lateFee)} of {formatMoney(b.deposit)} advance</p>
      <div className="flex items-center gap-2">
        <span className="flex-1 text-xs font-medium">{b.overdueSmsAt != null ? `SMS sent ${formatRelative(b.overdueSmsAt, now).toLowerCase()}` : 'Not texted yet'}</span>
        <button type="button" onClick={onSms} className="inline-flex h-10 items-center gap-1.5 rounded-sm bg-surface-lowest/70 px-4 text-sm font-semibold">
          <MessageSquare size={18} aria-hidden /> {b.overdueSmsAt == null ? 'Send SMS' : 'Send again'}
        </button>
      </div>
    </article>
  );
}

function VendorReturnRow({ v }: { v: VendorReturn }) {
  const late = v.dueInDays < 0;
  const body = late ? `Was due back to the vendor ${-v.dueInDays}d ago` : v.dueInDays === 0 ? 'Due back to the vendor today' : `Due back to the vendor in ${v.dueInDays}d`;
  return <Row href={paths.itemEditor(v.item.id)} icon={Handshake} tone={late ? 'bg-error-container text-on-error-container' : 'bg-tertiary-container text-on-tertiary-container'}
    title={v.item.title} body={body} bodyClass={late ? 'text-error' : 'text-on-surface-variant'} />;
}

function AlertRow({ a }: { a: StockAlert }) {
  const body = a.kind === 'LowStock'
    ? (a.minFree === 0 ? `Fully booked on ${formatShort(a.date)}` : `Only ${a.minFree} free on ${formatShort(a.date)}`)
    : `Needs service: ${a.unitTags.join(', ')}`;
  return <Row href={paths.itemEditor(a.itemId)} icon={a.kind === 'LowStock' ? PackageSearch : Wrench} tone="bg-tertiary-container text-on-tertiary-container" title={a.itemTitle} body={body} />;
}
