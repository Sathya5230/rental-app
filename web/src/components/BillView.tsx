'use client';

import { House, Printer, Share2 } from 'lucide-react';
import { useLive } from '@/app/providers';
import { rangeDays } from '@/domain/dates';
import { bookingCode, formatFull } from '@/domain/format/dates';
import { formatMoney } from '@/domain/format/money';
import { ADMIN_USER_ID, bookingRange, type Booking } from '@/domain/models';
import { settle } from '@/domain/rules/lateFees';
import { billText } from '@/lib/booking';
import { Button } from './Button';
import { SkeletonList } from './EmptyState';
import { ItemArt } from './ItemArt';
import { useToast } from './Toast';

const SHOP_NAME = 'RentNest Store';

/** Loads what a bill shows. With [customerId], only that customer's bookings are found. */
export function useBill(bookingId: number, customerId?: number) {
  return useLive(async s => {
    const booking = await s.bookings.booking(bookingId);
    if (!booking || (customerId != null && booking.customerId !== customerId)) return { booking: undefined };
    const [item, units, customer, admin] = await Promise.all([
      s.catalog.item(booking.itemId), s.inventory.unitsForItem(booking.itemId), s.catalog.user(booking.customerId), s.catalog.user(ADMIN_USER_ID),
    ]);
    return { booking, item, unitTag: units.find(u => u.id === booking.unitId)?.tag, customer, shopPhone: admin?.phone ?? '' };
  }, [bookingId, customerId]);
}

export type BillData = NonNullable<ReturnType<typeof useBill>>;

/** Port of BillCard: pickup receipt or closing bill, laid out to print cleanly. */
export function BillView({ data, isReturn }: { data: BillData | undefined; isReturn: boolean }) {
  if (!data) return <SkeletonList rows={1} className="h-96" />;
  const b = data.booking;
  if (!b) return <p className="py-16 text-center text-on-surface-variant">We couldn&apos;t find that anymore.</p>;
  return (
    <article className="flex flex-col gap-3.5 rounded-md border border-outline-variant p-5 print:border-0 print:p-0">
      <div className="flex items-center gap-2.5">
        <House size={28} className="text-primary" aria-hidden />
        <div className="flex-1">
          <p className="font-display font-semibold">{SHOP_NAME}</p>
          {data.shopPhone && <p className="text-xs text-on-surface-variant">{data.shopPhone}</p>}
        </div>
        <div className="text-right">
          <p className="font-display text-sm font-semibold">{bookingCode(b.id)}</p>
          <p className="text-xs text-on-surface-variant">{formatFull(isReturn ? b.endDate : b.startDate)}</p>
        </div>
      </div>
      <hr className="border-outline-variant" />
      <div>
        <p className="text-xs font-medium text-on-surface-variant">To</p>
        <p className="font-display text-sm font-semibold">{data.customer?.name}</p>
        <p className="text-xs text-on-surface-variant">{b.contactPhone || data.customer?.phone}</p>
      </div>
      <hr className="border-outline-variant" />
      <table className="w-full text-sm">
        <thead className="text-left text-xs text-on-surface-variant">
          <tr><th className="w-1/2 pb-2 font-medium">Description</th><th className="pb-2 font-medium">Days</th><th className="pb-2 font-medium">Rate</th><th className="pb-2 text-right font-medium">Amount</th></tr>
        </thead>
        <tbody>
          <tr className="border-t border-outline-variant align-top">
            <td className="pt-2">
              <div className="flex items-center gap-2">
                <ItemArt photoKey={data.item?.photos[0] ?? ''} iconSize={14} className="size-8 shrink-0 rounded-xs" />
                <div>
                  <p>{data.item?.title}</p>
                  {data.unitTag && <p className="text-xs text-on-surface-variant">Unit {data.unitTag}</p>}
                </div>
              </div>
            </td>
            <td className="pt-2">{rangeDays(bookingRange(b))}</td>
            <td className="pt-2">{formatMoney(data.item?.dailyRate ?? 0)}</td>
            <td className="pt-2 text-right">{formatMoney(b.subtotal)}</td>
          </tr>
        </tbody>
      </table>
      <hr className="border-outline-variant" />
      {isReturn ? <ClosingCharges b={b} /> : <PickupCharges b={b} />}
    </article>
  );
}

function Line({ label, value, bold = false }: { label: string; value: string; bold?: boolean }) {
  return (
    <div className={`flex justify-between text-sm ${bold ? 'font-display font-bold' : ''}`}>
      <span>{label}</span><span>{value}</span>
    </div>
  );
}

function PickupCharges({ b }: { b: Booking }) {
  return (
    <div className="flex flex-col gap-1.5">
      <Line label="Rental charge" value={formatMoney(b.subtotal)} />
      <Line label="Advance (refundable)" value={formatMoney(b.deposit)} />
      {b.pickupTransportFee > 0 && <Line label="Transport charge (pickup)" value={formatMoney(b.pickupTransportFee)} />}
      <hr className="my-0.5 border-outline-variant" />
      <Line label="Total collected now" value={formatMoney(b.subtotal + b.deposit + b.pickupTransportFee)} bold />
    </div>
  );
}

function ClosingCharges({ b }: { b: Booking }) {
  const s = settle({ advance: b.deposit, damageFee: b.damageFee, lateFee: b.lateFee, cleaningFee: b.cleaningFee, dropTransportFee: b.dropTransportFee });
  return (
    <div className="flex flex-col gap-1.5">
      <p className="text-xs text-on-surface-variant">Rental of {formatMoney(b.subtotal)} was collected at pickup.</p>
      {b.lateFee > 0 && <Line label="Late fee" value={formatMoney(b.lateFee)} />}
      {b.damageFee > 0 && <Line label="Damage charge" value={formatMoney(b.damageFee)} />}
      {b.dropTransportFee > 0 && <Line label="Transport charge (drop)" value={formatMoney(b.dropTransportFee)} />}
      {b.cleaningFee > 0 && <Line label="Cleaning / others" value={formatMoney(b.cleaningFee)} />}
      {s.charges === 0 && <Line label="No extra charges" value="₹0" />}
      <hr className="my-0.5 border-outline-variant" />
      <Line label="Total Amt (new charges)" value={formatMoney(s.charges)} bold />
      <Line label="Advance" value={formatMoney(b.deposit)} />
      <hr className="my-0.5 border-outline-variant" />
      <Line label={s.balance >= 0 ? 'Refund to customer' : 'Bal. to pay (from customer)'} value={formatMoney(Math.abs(s.balance))} bold />
    </div>
  );
}

/** Share (system share sheet, or copy) and print buttons for a loaded bill. */
export function BillActions({ data, isReturn, children }: { data: BillData | undefined; isReturn: boolean; children?: React.ReactNode }) {
  const toast = useToast();
  const b = data?.booking;
  const share = async () => {
    if (!data || !b) return;
    const text = billText({
      isReturn, booking: b, shopName: SHOP_NAME, shopPhone: data.shopPhone, customerName: data.customer?.name ?? '',
      itemTitle: data.item?.title ?? '', dailyRate: data.item?.dailyRate ?? 0,
    });
    if (navigator.share) {
      try { await navigator.share({ title: `${SHOP_NAME} ${bookingCode(b.id)}`, text }); } catch { /* the user closed the share sheet */ }
    } else {
      await navigator.clipboard.writeText(text);
      toast('Bill copied');
    }
  };
  return (
    <div className="flex gap-3">
      <Button variant="outlined" className="flex-1" disabled={!b} icon={<Share2 size={18} aria-hidden />} onClick={share}>Share</Button>
      <Button variant="outlined" className="flex-1" disabled={!b} icon={<Printer size={18} aria-hidden />} onClick={() => window.print()}>Print / PDF</Button>
      {children}
    </div>
  );
}
