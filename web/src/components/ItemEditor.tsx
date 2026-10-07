'use client';

import { Camera, CalendarCheck, ImageIcon, Minus, Plus, Shapes, Trash2, X } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useLive, useServices } from '@/app/providers';
import { ADMIN_USER_ID, UNIT_CONDITIONS, UNIT_STATUSES, type Category, type ItemField, type ItemUnit } from '@/domain/models';
import { errorMessage } from '@/domain/errors';
import { formatFull } from '@/domain/format/dates';
import { fileToPhotoKey } from '@/lib/photos';
import { formToDraft, type ItemForm } from '@/lib/itemForm';
import { paths } from '@/lib/routes';
import { exclusive } from '@/lib/singleFlight';
import { Button, IconButton } from './Button';
import { Dialog } from './Dialog';
import { Field, inputClass, TextInput } from './Field';
import { ItemArt, placeholderVariants } from './ItemArt';
import { useToast } from './Toast';

const MAX_PHOTOS = 6;
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

/** Port of ItemEditorScreen: a Details tab for the listing and a Units tab for each physical piece. */
export function ItemEditor({ itemId, initial }: { itemId: number; initial: ItemForm }) {
  const [tab, setTab] = useState<'DETAILS' | 'UNITS'>('DETAILS');
  const isNew = itemId === 0;
  const units = useLive(async s => {
    if (isNew) return [];
    const [list, bookings] = await Promise.all([s.inventory.unitsForItem(itemId), s.bookings.bookingsForItem(itemId)]);
    const today = s.time.today();
    return list.map(unit => ({
      unit, busy: bookings.some(b => b.unitId === unit.id && (b.status === 'ACTIVE' || (b.status === 'ACCEPTED' && b.endDate >= today))),
    }));
  }, [itemId]);

  return (
    <div>
      <div role="tablist" aria-label="Item" className="-mx-4 mb-4 grid grid-cols-2 border-b border-outline-variant">
        <button type="button" role="tab" aria-selected={tab === 'DETAILS'} onClick={() => setTab('DETAILS')}
          className={`border-b-[3px] py-3 text-sm font-semibold ${tab === 'DETAILS' ? 'border-primary text-primary' : 'border-transparent text-on-surface-variant'}`}>Details</button>
        <button type="button" role="tab" aria-selected={tab === 'UNITS'} disabled={isNew} onClick={() => setTab('UNITS')}
          className={`border-b-[3px] py-3 text-sm font-semibold disabled:opacity-40 ${tab === 'UNITS' ? 'border-primary text-primary' : 'border-transparent text-on-surface-variant'}`}>
          {isNew ? 'Units (save first)' : `Units (${units?.length ?? 0})`}
        </button>
      </div>
      {/* Both panels stay mounted, so switching tabs keeps unsaved edits. */}
      <div hidden={tab !== 'DETAILS'}><DetailsForm itemId={itemId} initial={initial} /></div>
      {!isNew && <div hidden={tab !== 'UNITS'}><UnitsTab itemId={itemId} units={units ?? []} /></div>}
    </div>
  );
}

function Chip({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" aria-pressed={selected} onClick={onClick}
      className={`h-8 rounded-xs border px-3 text-sm font-medium ${selected ? 'border-transparent bg-secondary-container text-on-secondary-container' : 'border-outline text-on-surface-variant'}`}>
      {children}
    </button>
  );
}

function DetailsForm({ itemId, initial }: { itemId: number; initial: ItemForm }) {
  const { catalog } = useServices();
  const router = useRouter();
  const toast = useToast();
  const lists = useLive(async s => ({ categories: await s.catalog.categories(), vendors: await s.catalog.vendors(), store: await s.catalog.providerForUser(ADMIN_USER_ID) }));
  const [f, setF] = useState<ItemForm>(initial);
  const [errors, setErrors] = useState<Set<ItemField>>(new Set());
  const [saving, setSaving] = useState(false);
  const [importing, setImporting] = useState(false);
  const [newCategory, setNewCategory] = useState(false);
  const [newVendor, setNewVendor] = useState(false);
  // Exclusive, not single-flight: the form stays open, so every later save must run too.
  const [save] = useState(() => exclusive((form: ItemForm, providerId: number) => catalog.saveItem(formToDraft(form, itemId, providerId))));
  const update = (patch: Partial<ItemForm>) => setF(prev => ({ ...prev, ...patch }));
  const bad = (field: ItemField) => errors.has(field);

  const addFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setImporting(true);
    const keys = (await Promise.all([...files].map(fileToPhotoKey))).filter((k): k is string => k != null);
    setImporting(false);
    setF(prev => ({ ...prev, photos: [...new Set([...prev.photos, ...keys])].slice(0, MAX_PHOTOS) }));
    if (keys.length > 0) setErrors(prev => { const next = new Set(prev); next.delete('PHOTOS'); return next; });
    if (keys.length < files.length) toast(`Couldn't read ${files.length - keys.length} photo(s). Try another.`);
  };

  const addPlaceholder = (categories: Category[]) => {
    const key = categories.find(c => c.id === f.categoryId)?.iconKey ?? 'other';
    const next = placeholderVariants(key).find(v => !f.photos.includes(v));
    if (next) { update({ photos: [...f.photos, next].slice(0, MAX_PHOTOS) }); setErrors(prev => { const n = new Set(prev); n.delete('PHOTOS'); return n; }); }
  };

  const submit = async () => {
    if (!lists?.store) return;
    setSaving(true);
    const r = await save(f, lists.store.id);
    setSaving(false);
    if (r.ok) {
      setErrors(new Set());
      if (itemId === 0) {
        toast('Item added with 1 unit. Add more units in the Units tab.');
        router.replace(paths.itemEditor(r.value));
      } else toast('Changes saved');
    } else {
      setErrors(new Set(r.error.kind === 'ValidationFailed' ? r.error.fields : []));
      toast(errorMessage(r.error));
    }
  };

  if (!lists) return null;
  const vendor = lists.vendors.find(v => v.id === f.vendorId);

  return (
    <div className="flex flex-col gap-4 lg:grid lg:grid-cols-2 lg:gap-x-8">
      <section className="flex flex-col gap-2 lg:col-span-2">
        <h2 className={`font-display text-sm font-semibold ${bad('PHOTOS') ? 'text-error' : ''}`}>Photos</h2>
        <p className={`text-xs ${bad('PHOTOS') ? 'text-error' : 'text-on-surface-variant'}`}>
          {bad('PHOTOS') ? 'Add at least one photo of the product.' : 'The first photo is the cover customers see. Tap a photo to make it the cover.'}
        </p>
        <div className="no-scrollbar flex gap-2 overflow-x-auto">
          {f.photos.map((key, i) => (
            <div key={key} className="relative size-24 shrink-0 overflow-hidden rounded-sm">
              <button type="button" aria-label={i === 0 ? 'Cover photo' : `Make photo ${i + 1} the cover`} className="size-full"
                onClick={() => update({ photos: [key, ...f.photos.filter(p => p !== key)] })}>
                <ItemArt photoKey={key} iconSize={28} className="size-full" />
              </button>
              {i === 0 && <span className="absolute bottom-1 left-1 rounded-xs bg-primary px-1.5 py-0.5 text-[10px] text-on-primary">Cover</span>}
              <button type="button" aria-label="Remove photo" onClick={() => update({ photos: f.photos.filter(p => p !== key) })}
                className="absolute right-1 top-1 flex size-7 items-center justify-center rounded-full bg-black/50 text-white"><X size={16} /></button>
            </div>
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          <label className={`inline-flex h-10 cursor-pointer items-center gap-1.5 rounded-sm border border-outline px-4 text-sm font-semibold text-primary ${f.photos.length >= MAX_PHOTOS ? 'pointer-events-none opacity-40' : ''}`}>
            <ImageIcon size={18} aria-hidden /> {importing ? 'Adding…' : 'Gallery'}
            <input type="file" accept="image/*" multiple className="sr-only" onChange={e => { addFiles(e.target.files); e.target.value = ''; }} />
          </label>
          <label className={`inline-flex h-10 cursor-pointer items-center gap-1.5 rounded-sm border border-outline px-4 text-sm font-semibold text-primary ${f.photos.length >= MAX_PHOTOS ? 'pointer-events-none opacity-40' : ''}`}>
            <Camera size={18} aria-hidden /> Camera
            <input type="file" accept="image/*" capture="environment" className="sr-only" onChange={e => { addFiles(e.target.files); e.target.value = ''; }} />
          </label>
          <Button variant="text" className="!min-h-10" icon={<Shapes size={18} aria-hidden />} disabled={f.photos.length >= MAX_PHOTOS} onClick={() => addPlaceholder(lists.categories)}>Use placeholder</Button>
        </div>
      </section>

      <div className="flex flex-col gap-4">
        <Field label="Product name" hint="What customers will search for" error={bad('TITLE') ? 'What customers will search for' : null}>
          <TextInput value={f.title} onChange={e => update({ title: e.target.value })} />
        </Field>
        <fieldset>
          <legend className={`mb-2 font-display text-sm font-semibold ${bad('CATEGORY') ? 'text-error' : ''}`}>Category</legend>
          <div className="flex flex-wrap gap-2">
            {lists.categories.map(c => <Chip key={c.id} selected={f.categoryId === c.id} onClick={() => update({ categoryId: c.id })}>{c.name}</Chip>)}
            <Chip selected={false} onClick={() => setNewCategory(true)}><span className="inline-flex items-center gap-1"><Plus size={16} aria-hidden /> New category</span></Chip>
          </div>
        </fieldset>
        <label className="block text-sm text-on-surface-variant">Description
          <textarea rows={3} value={f.description} onChange={e => update({ description: e.target.value })} className={`${inputClass} mt-1.5 h-auto py-3`} />
        </label>

        <h2 className="font-display font-semibold">Rental pricing</h2>
        <div className="grid grid-cols-2 gap-2.5">
          <Field label="Per day ₹" hint="Also the late fee per day" error={bad('DAILY_RATE') ? 'Also the late fee per day' : null}>
            <TextInput inputMode="decimal" value={f.daily} onChange={e => update({ daily: e.target.value })} />
          </Field>
          <Field label="Per week ₹" hint="≤ 7 × daily" error={bad('WEEKLY_RATE') ? '≤ 7 × daily' : null}>
            <TextInput inputMode="decimal" value={f.weekly} onChange={e => update({ weekly: e.target.value })} />
          </Field>
        </div>
        <Field label="Advance ₹" hint="Collected at pickup, refunded on return" error={bad('DEPOSIT') ? 'Collected at pickup, refunded on return' : null}>
          <TextInput inputMode="decimal" value={f.deposit} onChange={e => update({ deposit: e.target.value })} />
        </Field>
      </div>

      <div className="flex flex-col gap-4">
        <h2 className="font-display font-semibold">Stock &amp; value</h2>
        <Field label="Value per unit ₹" hint="Purchase or replacement cost, for inventory worth" error={bad('UNIT_VALUE') ? 'Purchase or replacement cost, for inventory worth' : null}>
          <TextInput inputMode="decimal" value={f.unitValue} onChange={e => update({ unitValue: e.target.value })} />
        </Field>
        <fieldset>
          <legend className="mb-2 font-display text-sm font-semibold">Source</legend>
          <div className="flex gap-2">
            <Chip selected={f.ownership === 'OWNED'} onClick={() => update({ ownership: 'OWNED' })}>Owned</Chip>
            <Chip selected={f.ownership === 'BORROWED'} onClick={() => update({ ownership: 'BORROWED' })}>Borrowed from vendor</Chip>
          </div>
        </fieldset>
        {f.ownership === 'BORROWED' && (
          <div className="flex flex-col gap-4 rounded-md bg-surface-low p-3">
            <fieldset>
              <legend className={`mb-2 font-display text-sm font-semibold ${bad('VENDOR') ? 'text-error' : ''}`}>Vendor</legend>
              <div className="flex flex-wrap gap-2">
                {lists.vendors.map(v => <Chip key={v.id} selected={f.vendorId === v.id} onClick={() => update({ vendorId: v.id })}>{v.name}</Chip>)}
                <Chip selected={false} onClick={() => setNewVendor(true)}><span className="inline-flex items-center gap-1"><Plus size={16} aria-hidden /> New vendor</span></Chip>
              </div>
              {vendor?.phone && <p className="mt-1 text-xs text-on-surface-variant">Contact: {vendor.phone}</p>}
            </fieldset>
            <Field label="Vendor cost per unit per day ₹" hint="What you pay the vendor" error={bad('VENDOR_COST') ? 'What you pay the vendor' : null}>
              <TextInput inputMode="decimal" value={f.vendorCost} onChange={e => update({ vendorCost: e.target.value })} />
            </Field>
            <Field label="Return to vendor by" hint={f.vendorReturnBy ? formatFull(f.vendorReturnBy) : 'Not set'}>
              <TextInput type="date" value={f.vendorReturnBy ?? ''} onChange={e => update({ vendorReturnBy: e.target.value || null })} />
            </Field>
          </div>
        )}
        <div className="flex items-center gap-2">
          <div className="flex-1">
            <p className="font-display text-sm font-semibold">Low-stock alert</p>
            <p className="text-xs text-on-surface-variant">Warn when free units drop to this</p>
          </div>
          <IconButton label="Decrease" onClick={() => update({ threshold: Math.max(0, f.threshold - 1) })}><Minus /></IconButton>
          <span className="w-6 text-center font-display text-xl font-bold" aria-live="polite">{f.threshold}</span>
          <IconButton label="Increase" onClick={() => update({ threshold: f.threshold + 1 })}><Plus /></IconButton>
        </div>
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 font-display text-sm font-semibold">Specifications</legend>
          {f.specs.map(([k, v], i) => (
            <div key={i} className="flex items-center gap-2">
              <input aria-label={`Spec ${i + 1} name`} placeholder="Name" value={k} className={inputClass}
                onChange={e => update({ specs: f.specs.map((s, j) => (j === i ? [e.target.value, s[1]] : s)) })} />
              <input aria-label={`Spec ${i + 1} value`} placeholder="Value" value={v} className={inputClass}
                onChange={e => update({ specs: f.specs.map((s, j) => (j === i ? [s[0], e.target.value] : s)) })} />
              <IconButton label="Remove spec" onClick={() => update({ specs: f.specs.filter((_, j) => j !== i) })}><Trash2 size={20} /></IconButton>
            </div>
          ))}
          <Button variant="text" className="self-start" icon={<Plus size={18} aria-hidden />} onClick={() => update({ specs: [...f.specs, ['', '']] })}>Add specification</Button>
        </fieldset>
        <label className="flex cursor-pointer items-center gap-3">
          <span className="flex-1">
            <span className="block font-display text-sm font-semibold">Listed</span>
            <span className="block text-xs text-on-surface-variant">Inactive items are hidden from customers</span>
          </span>
          <input type="checkbox" role="switch" checked={f.isActive} onChange={e => update({ isActive: e.target.checked })} className="size-6 accent-[var(--primary)]" />
        </label>
      </div>

      <Button className="w-full lg:col-span-2" loading={saving} disabled={importing} onClick={submit}>{itemId === 0 ? 'Create item' : 'Save changes'}</Button>

      <NameDialog open={newCategory} title="New category" nameLabel="Category name" onClose={() => setNewCategory(false)}
        onSave={async name => {
          setNewCategory(false);
          const r = await catalog.addCategory(name);
          if (r.ok) update({ categoryId: r.value.id }); else toast(errorMessage(r.error));
        }} />
      <NameDialog open={newVendor} title="New vendor" nameLabel="Vendor name" phoneLabel="Phone (optional)" onClose={() => setNewVendor(false)}
        onSave={async (name, phone) => {
          setNewVendor(false);
          const r = await catalog.addVendor(name, phone);
          if (r.ok) update({ vendorId: r.value.id }); else toast(errorMessage(r.error));
        }} />
    </div>
  );
}

function NameDialog({ open, title, nameLabel, phoneLabel, onClose, onSave }: {
  open: boolean; title: string; nameLabel: string; phoneLabel?: string; onClose: () => void; onSave: (name: string, phone: string) => void;
}) {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [prev, setPrev] = useState(open);
  if (open !== prev) { setPrev(open); setName(''); setPhone(''); }
  return (
    <Dialog open={open} title={title} onClose={onClose}>
      <form className="flex flex-col gap-3" onSubmit={e => { e.preventDefault(); if (name.trim()) onSave(name, phone); }}>
        <Field label={nameLabel}><TextInput autoFocus value={name} onChange={e => setName(e.target.value)} /></Field>
        {phoneLabel && <Field label={phoneLabel}><TextInput inputMode="tel" value={phone} onChange={e => setPhone(e.target.value.replace(/[^\d+ ]/g, '').slice(0, 16))} /></Field>}
        <div className="mt-2 flex justify-end gap-2">
          <Button variant="text" onClick={onClose}>Cancel</Button>
          <Button variant="text" type="submit" disabled={!name.trim()}>Add</Button>
        </div>
      </form>
    </Dialog>
  );
}

function UnitsTab({ itemId, units }: { itemId: number; units: { unit: ItemUnit; busy: boolean }[] }) {
  const { inventory } = useServices();
  const toast = useToast();
  const change = async (unit: ItemUnit) => {
    const r = await inventory.updateUnit(unit);
    if (!r.ok) toast(errorMessage(r.error));
  };
  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-on-surface-variant">Each unit is one physical piece you can rent out. Track its condition and take it out of service when needed.</p>
      <div className="grid gap-3 lg:grid-cols-2">
        {units.map(({ unit, busy }) => (
          <article key={unit.id} className="flex flex-col gap-2 rounded-md bg-surface-low p-3.5">
            <div className="flex items-center">
              <h3 className="flex-1 font-display font-semibold">{unit.tag}</h3>
              {busy && <span className="inline-flex items-center gap-1 rounded-xs border border-outline px-2 py-1 text-xs"><CalendarCheck size={14} aria-hidden /> Booked</span>}
            </div>
            <p className="text-xs font-medium">Condition</p>
            <div className="flex flex-wrap gap-2">
              {UNIT_CONDITIONS.map(c => <Chip key={c} selected={unit.condition === c} onClick={() => change({ ...unit, condition: c })}>{titleCase(c)}</Chip>)}
            </div>
            <p className="text-xs font-medium">Status</p>
            <div className="flex flex-wrap gap-2">
              {UNIT_STATUSES.map(s => <Chip key={s} selected={unit.status === s} onClick={() => change({ ...unit, status: s })}>{titleCase(s)}</Chip>)}
            </div>
          </article>
        ))}
      </div>
      <Button variant="tonal" icon={<Plus size={18} aria-hidden />} onClick={async () => { const u = await inventory.addUnit(itemId); toast(`Added unit ${u.tag}`); }}>Add unit</Button>
    </div>
  );
}
