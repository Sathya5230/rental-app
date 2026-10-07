import { Bike, Camera, Dumbbell, MonitorSmartphone, Music, PartyPopper, Shapes, Trees, Wrench, type LucideIcon } from 'lucide-react';

const PALETTES: Record<string, [string, string]> = {
  cameras: ['#355C7D', '#6C5B7B'], tools: ['#D35400', '#F39C12'], camping: ['#134E5E', '#4F9A6E'], party: ['#C0392B', '#8E2D6B'],
  sports: ['#117A65', '#48C9B0'], electronics: ['#2B5876', '#4E4376'], vehicles: ['#1C2833', '#2E6177'], music: ['#6C3483', '#3F2DB5'],
};
const ICONS: Record<string, LucideIcon> = {
  cameras: Camera, tools: Wrench, camping: Trees, party: PartyPopper, sports: Dumbbell, electronics: MonitorSmartphone, vehicles: Bike, music: Music,
};
/** Where the large faded glyph sits, by variant. Mirrors ItemArt.kt's alignment and offset. */
const CORNERS = [
  'bottom-0 right-0 translate-x-[28px] translate-y-[28px]',
  'top-0 left-0 -translate-x-[28px] -translate-y-[28px]',
  'top-0 right-0 translate-x-[24px] translate-y-[24px]',
  'bottom-0 left-0 -translate-x-[24px] -translate-y-[24px]',
];

export function parsePhotoKey(key: string): [category: string, variant: number] {
  const [category, variant] = key.split(':');
  return [category, Number(variant) || 0];
}

export const placeholderVariants = (categoryKey: string) => [0, 1, 2, 3].map(v => `${categoryKey}:${v}`);

/** An item photo: an uploaded data URL, or a category gradient with its glyph as an offline placeholder. */
export function ItemArt({ photoKey, className = '', iconSize = 52 }: { photoKey: string; className?: string; iconSize?: number }) {
  if (photoKey.startsWith('data:')) {
    // eslint-disable-next-line @next/next/no-img-element -- data URLs from IndexedDB, nothing for next/image to optimise
    return <img src={photoKey} alt="" className={`object-cover ${className}`} />;
  }
  const [category, variant] = parsePhotoKey(photoKey);
  const [a, b] = PALETTES[category] ?? ['#00695C', '#26A69A'];
  const [from, to] = variant % 2 === 0 ? [a, b] : [b, a];
  const Icon = ICONS[category] ?? Shapes;
  return (
    <div className={`relative flex items-center justify-center overflow-hidden ${className}`} style={{ backgroundImage: `linear-gradient(135deg, ${from}, ${to})` }}>
      <Icon aria-hidden className={`absolute text-white/[0.13] ${CORNERS[variant % 4]}`} size={iconSize * 2.8} />
      <Icon aria-hidden className="relative text-white" size={iconSize} />
    </div>
  );
}
