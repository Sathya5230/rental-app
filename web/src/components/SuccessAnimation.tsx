const COLORS = ['var(--primary)', '#FFB866', '#E53950', '#4F5B92', '#48C9B0'];

/** A seeded spread of confetti, so server and client render the same pieces. */
const PIECES = Array.from({ length: 42 }, (_, i) => {
  const angle = (i * 137.5 * Math.PI) / 180;
  const dist = 70 + ((i * 53) % 60);
  return { x: Math.cos(angle) * dist, y: Math.sin(angle) * dist + 40, rot: (i * 97) % 720, color: COLORS[i % COLORS.length], size: 6 + ((i * 7) % 8) };
});

/** Bouncy check mark with a confetti burst. Port of SuccessAnimation.kt, in CSS. */
export function SuccessAnimation() {
  return (
    <div className="relative size-[260px]" aria-hidden>
      <style>{`
        @keyframes rn-pop { 0% { transform: scale(0) } 60% { transform: scale(1.12) } 100% { transform: scale(1) } }
        @keyframes rn-draw { to { stroke-dashoffset: 0 } }
        @keyframes rn-burst { from { transform: translate(0, 0) rotate(0); opacity: 1 } to { transform: translate(var(--x), var(--y)) rotate(var(--r)); opacity: 0 } }
      `}</style>
      {PIECES.map((p, i) => (
        <span key={i} className="absolute left-1/2 top-1/2"
          style={{ width: p.size, height: p.size / 2, background: p.color, animation: 'rn-burst 1.6s ease-out forwards', ['--x' as string]: `${p.x}px`, ['--y' as string]: `${p.y}px`, ['--r' as string]: `${p.rot}deg` }} />
      ))}
      <svg viewBox="0 0 100 100" className="absolute inset-[24%]" style={{ animation: 'rn-pop 0.6s cubic-bezier(.34,1.56,.64,1) both' }}>
        <circle cx="50" cy="50" r="50" fill="var(--primary)" />
        <path d="M29 51 L45 66 L73 35" fill="none" stroke="var(--on-primary)" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round"
          strokeDasharray="70" strokeDashoffset="70" style={{ animation: 'rn-draw 0.45s 0.25s ease-out forwards' }} />
      </svg>
    </div>
  );
}
