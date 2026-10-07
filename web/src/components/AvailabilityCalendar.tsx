'use client';

import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useState } from 'react';
import { isoDate, rangeContains, type DateRange, type IsoDate } from '@/domain/dates';
import { formatFull } from '@/domain/format/dates';
import { IconButton } from './Button';

const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const WEEKDAYS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

interface Month { year: number; month: number }

function monthsFrom(today: IsoDate, count: number): Month[] {
  const [y, m] = today.split('-').map(Number);
  return Array.from({ length: count }, (_, i) => ({ year: y + Math.floor((m - 1 + i) / 12), month: ((m - 1 + i) % 12) + 1 }));
}

/** Monday-first grid cells for a month; null pads the first week. */
function cells({ year, month }: Month): (IsoDate | null)[] {
  const offset = (new Date(Date.UTC(year, month - 1, 1)).getUTCDay() + 6) % 7;
  const days = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return [...Array<null>(offset).fill(null), ...Array.from({ length: days }, (_, i) => isoDate(year, month, i + 1))];
}

/**
 * Port of AvailabilityCalendar.kt. Read-only, it pages month by month. With [onPick], every month is stacked
 * and each free day is a button named by its full date (e.g. "17 Oct 2026").
 */
export function AvailabilityCalendar({ today, unavailable, monthsAhead = 2, selected = null, onPick }: {
  today: IsoDate;
  unavailable: Set<IsoDate>;
  monthsAhead?: number;
  selected?: DateRange | null;
  onPick?: (date: IsoDate) => void;
}) {
  const months = monthsFrom(today, monthsAhead + 1);
  const [index, setIndex] = useState(0);
  const shown = onPick ? months : [months[index]];

  return (
    <div>
      {shown.map(m => (
        <section key={`${m.year}-${m.month}`} className="mb-4">
          <div className="flex items-center">
            {!onPick && <IconButton label="Previous month" disabled={index === 0} onClick={() => setIndex(index - 1)} className="disabled:opacity-30"><ChevronLeft /></IconButton>}
            <h3 className="flex-1 py-2 text-center font-display font-semibold">{MONTH_NAMES[m.month - 1]} {m.year}</h3>
            {!onPick && <IconButton label="Next month" disabled={index === months.length - 1} onClick={() => setIndex(index + 1)} className="disabled:opacity-30"><ChevronRight /></IconButton>}
          </div>
          <div className="grid grid-cols-7 text-center text-xs text-on-surface-variant" aria-hidden>
            {WEEKDAYS.map((d, i) => <span key={i} className="py-1">{d}</span>)}
          </div>
          <div className="grid grid-cols-7">
            {cells(m).map((date, i) => (
              <div key={date ?? `pad-${i}`} className="aspect-square p-0.5">
                {date && <DayCell date={date} today={today} booked={unavailable.has(date)} selected={selected} onPick={onPick} />}
              </div>
            ))}
          </div>
        </section>
      ))}
      <div className="flex gap-4 text-xs text-on-surface-variant">
        <span className="flex items-center gap-1.5"><span className="size-3 rounded-full bg-primary-container" /> Available</span>
        <span className="flex items-center gap-1.5"><span className="size-3 rounded-full bg-surface-highest" /> Booked</span>
      </div>
    </div>
  );
}

function DayCell({ date, today, booked, selected, onPick }: { date: IsoDate; today: IsoDate; booked: boolean; selected: DateRange | null; onPick?: (d: IsoDate) => void }) {
  const past = date < today;
  const day = Number(date.slice(8));
  const isEdge = selected != null && (date === selected.start || date === selected.end);
  const inRange = selected != null && rangeContains(selected, date);
  const tone = isEdge
    ? 'bg-primary text-on-primary'
    : inRange
      ? 'bg-secondary-container text-on-secondary-container'
      : past
        ? 'text-on-surface/35'
        : booked
          ? 'bg-surface-highest text-on-surface/45 line-through'
          : 'bg-primary-container text-on-primary-container';
  const ring = date === today && !isEdge ? 'ring-[1.5px] ring-primary' : '';
  const cls = `flex size-full items-center justify-center rounded-full text-sm font-medium ${tone} ${ring}`;
  if (!onPick) {
    return <div className={cls} role="img" aria-label={`${formatFull(date)}, ${booked ? 'booked' : past ? 'past' : 'available'}`}>{day}</div>;
  }
  return (
    <button type="button" className={`${cls} disabled:cursor-not-allowed`} disabled={past || booked} aria-pressed={inRange} aria-label={formatFull(date)} onClick={() => onPick(date)}>
      {day}
    </button>
  );
}
