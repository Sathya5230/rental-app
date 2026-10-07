import { CategoryIcon, categoryGradient } from './CategoryVisuals';

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
  const [a, b] = categoryGradient(category);
  const [from, to] = variant % 2 === 0 ? [a, b] : [b, a];
  return (
    <div className={`relative flex items-center justify-center overflow-hidden ${className}`} style={{ backgroundImage: `linear-gradient(135deg, ${from}, ${to})` }}>
      <CategoryIcon iconKey={category} aria-hidden className={`absolute text-white/[0.13] ${CORNERS[variant % 4]}`} size={iconSize * 2.8} />
      <CategoryIcon iconKey={category} aria-hidden className="relative text-white" size={iconSize} />
    </div>
  );
}
