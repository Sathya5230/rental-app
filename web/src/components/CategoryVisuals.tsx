import { createElement, type ComponentProps } from 'react';
import { Bike, Camera, Dumbbell, MonitorSmartphone, Music, PartyPopper, Shapes, Trees, Wrench, type LucideIcon } from 'lucide-react';

const PALETTES: Record<string, [string, string]> = {
  cameras: ['#355C7D', '#6C5B7B'], tools: ['#D35400', '#F39C12'], camping: ['#134E5E', '#4F9A6E'], party: ['#C0392B', '#8E2D6B'],
  sports: ['#117A65', '#48C9B0'], electronics: ['#2B5876', '#4E4376'], vehicles: ['#1C2833', '#2E6177'], music: ['#6C3483', '#3F2DB5'],
};
const ICONS: Record<string, LucideIcon> = {
  cameras: Camera, tools: Wrench, camping: Trees, party: PartyPopper, sports: Dumbbell, electronics: MonitorSmartphone, vehicles: Bike, music: Music,
};

/** Category gradient and glyph, shared by item placeholders and category tiles. Port of CategoryVisuals. */
export const categoryGradient = (key: string): [string, string] => PALETTES[key] ?? ['#00695C', '#26A69A'];
/** The category's glyph. A component, so callers never pick a component type during render. */
export function CategoryIcon({ iconKey, ...props }: { iconKey: string } & ComponentProps<LucideIcon>) {
  return createElement(ICONS[iconKey] ?? Shapes, props);
}
