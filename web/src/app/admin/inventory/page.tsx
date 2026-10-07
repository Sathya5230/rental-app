'use client';

import { ClipboardCheck, EyeOff, Handshake, PackageSearch, PackageX, Plus, Search, TriangleAlert, Wrench } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { useLive } from '@/app/providers';
import { EmptyState, SkeletonList } from '@/components/EmptyState';
import { inputClass } from '@/components/Field';
import { ItemArt } from '@/components/ItemArt';
import { TabScaffold } from '@/components/TabScaffold';
import { formatMoney } from '@/domain/format/money';
import { inventoryTotals, type InventoryTotals } from '@/domain/rules/inventoryMetrics';
import { filterInventory, INVENTORY_FILTERS, inventoryCounts, inventoryRows, type InventoryFilter, type InventoryRow } from '@/lib/inventory';
import { paths } from '@/lib/routes';
import { loadShop } from '@/lib/shop';
import { useRouter } from 'next/navigation';

export default function Inventory() {
  const router = useRouter();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<InventoryFilter>('ALL');
  const rows = useLive(async s => {
    const shop = await loadShop(s);
    if (!shop) return null;
    return inventoryRows(shop, await s.inventory.audits(), await s.catalog.vendors(), s.time.today(), s.time.nowMillis());
  });
  const counts = rows ? inventoryCounts(rows) : null;
  const shown = rows ? filterInventory(rows, filter, query) : [];

  const actions = (
    <div className="flex items-center gap-2">
      <Link href="/admin/audit" className="relative inline-flex h-10 items-center gap-1.5 rounded-full px-3 text-sm font-semibold text-primary hover:bg-primary/8">
        <ClipboardCheck size={20} aria-hidden /> Audit
        {(counts?.NEEDS_AUDIT ?? 0) > 0 && <span className="rounded-full bg-error px-1.5 text-[10px] text-white" aria-label={`${counts!.NEEDS_AUDIT} need counting`}>{counts!.NEEDS_AUDIT}</span>}
      </Link>
      <Link href={paths.itemEditor()} className="hidden h-10 items-center gap-1.5 rounded-sm bg-primary px-4 text-sm font-semibold text-on-primary lg:inline-flex"><Plus size={18} aria-hidden /> Add item</Link>
    </div>
  );

  return (
    <TabScaffold title="Inventory" actions={actions}>
      {!rows ? <SkeletonList /> : (
        <div className="flex flex-col gap-3">
          <TotalsCard t={inventoryTotals(rows.map(r => r.stock))} />
          <div className="relative">
            <Search className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-on-surface-variant" aria-hidden />
            <input type="search" aria-label="Search inventory" placeholder="Search items, categories, vendors" value={query} onChange={e => setQuery(e.target.value)} className={`${inputClass} pl-12`} />
          </div>
          <div className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4 lg:mx-0 lg:flex-wrap lg:px-0">
            {INVENTORY_FILTERS.map(([f, label]) => (
              <button key={f} type="button" aria-pressed={filter === f} onClick={() => setFilter(f)}
                className={`h-8 shrink-0 rounded-xs border px-3 text-sm font-medium ${filter === f ? 'border-transparent bg-secondary-container text-on-secondary-container' : 'border-outline text-on-surface-variant'}`}>
                {label} ({counts![f]})
              </button>
            ))}
          </div>
          {shown.length === 0
            ? <EmptyState icon={PackageX} title="No items here" body="Add a product or change the filter." action={{ label: 'Add item', onClick: () => router.push(paths.itemEditor()) }} />
            : <div className="grid gap-2.5 lg:grid-cols-2">{shown.map(r => <InventoryCard key={r.stock.item.id} row={r} />)}</div>}
        </div>
      )}
      <Link href={paths.itemEditor()} className="fixed bottom-24 right-4 z-10 inline-flex h-14 items-center gap-2 rounded-md bg-primary-container px-5 font-semibold text-on-primary-container shadow-lg lg:hidden">
        <Plus aria-hidden /> Add item
      </Link>
    </TabScaffold>
  );
}

function Figure({ value, label }: { value: string; label: string }) {
  return <div className="min-w-0"><p className="truncate font-display font-semibold">{value}</p><p className="text-[11px]">{label}</p></div>;
}

function TotalsCard({ t }: { t: InventoryTotals }) {
  return (
    <section className="flex flex-col gap-3 rounded-md bg-primary-container p-4 text-on-primary-container">
      <div>
        <p className="text-sm font-semibold">Inventory net worth</p>
        <p className="font-display text-3xl font-bold">{formatMoney(t.totalWorth)}</p>
        <p className="text-xs">Owned {formatMoney(t.ownedWorth)} · Borrowed {formatMoney(t.borrowedWorth)} ({t.borrowedProducts} items)</p>
      </div>
      <hr className="border-current/15" />
      <div className="grid grid-cols-4 gap-2">
        <Figure value={`${t.products}`} label="Products" /><Figure value={`${t.units}`} label="Units" />
        <Figure value={`${t.unitsInStore}`} label="In store" /><Figure value={`${t.unitsOut}`} label="Rented out" />
      </div>
      <hr className="border-current/15" />
      <p className="text-sm font-semibold">Per-day rental</p>
      <div className="grid grid-cols-3 gap-2">
        <Figure value={formatMoney(t.dailyEarning)} label="Earning now" /><Figure value={formatMoney(t.dailyVendorCost)} label="Vendor cost" />
        <Figure value={formatMoney(t.netDaily)} label="Net / day" />
      </div>
      <p className="text-xs">If every rentable unit were out: {formatMoney(t.dailyPotential)}/day{t.unitsInService > 0 ? ` · ${t.unitsInService} in service` : ''}</p>
    </section>
  );
}

function Tag({ icon: Icon, children }: { icon: typeof Wrench; children: React.ReactNode }) {
  return <span className="inline-flex items-center gap-1 rounded-xs bg-tertiary-container px-1.5 py-0.5 text-[11px] font-medium text-on-tertiary-container"><Icon size={12} aria-hidden />{children}</span>;
}

function InventoryCard({ row }: { row: InventoryRow }) {
  const s = row.stock;
  const item = s.item;
  const due = row.vendorDueInDays;
  const dueText = due == null ? '' : due < 0 ? ` · return overdue ${-due}d` : due === 0 ? ' · return today' : ` · return in ${due}d`;
  const audit = row.lastAudit;
  return (
    <Link href={paths.itemEditor(item.id)} className="flex items-start gap-3 rounded-md bg-surface-low p-3 hover:bg-surface-container">
      <ItemArt photoKey={item.photos[0] ?? ''} iconSize={28} className="size-[72px] shrink-0 rounded-sm" />
      <div className="min-w-0 flex-1 text-xs text-on-surface-variant">
        <h3 className="font-display text-sm font-semibold text-on-surface">{item.title}</h3>
        <p>{row.categoryName} · {formatMoney(item.dailyRate)}/day</p>
        <p>Worth {formatMoney(s.worth)} · {s.inStore} in store, {s.out} out</p>
        {row.vendorName && <p className={(due ?? 1) <= 0 ? 'text-error' : ''}>From {row.vendorName} · {formatMoney(item.vendorCostPerDay)}/day{dueText}</p>}
        <div className="mt-1.5 flex flex-wrap gap-1.5">
          {item.ownership === 'BORROWED' && <Tag icon={Handshake}>Borrowed</Tag>}
          {!item.isActive && <Tag icon={EyeOff}>Inactive</Tag>}
          {row.lowStock && <Tag icon={PackageSearch}>Low stock</Tag>}
          {row.maintenance && <Tag icon={Wrench}>Service</Tag>}
          {!audit ? <Tag icon={ClipboardCheck}>Never audited</Tag>
            : audit.counted !== audit.expected ? <Tag icon={TriangleAlert}>Audit: {audit.counted}/{audit.expected} found</Tag>
              : row.needsAudit ? <Tag icon={ClipboardCheck}>Audit due</Tag> : null}
        </div>
      </div>
      <div className="text-right">
        <p className="font-display text-xl font-bold text-on-surface">{row.availableNow}/{s.units}</p>
        <p className="text-[11px] text-on-surface-variant">free today</p>
      </div>
    </Link>
  );
}
