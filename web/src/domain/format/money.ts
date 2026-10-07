/** All money is integer paise. Display is ₹ with Indian grouping (1,23,456). */
export function formatMoney(paise: number): string {
  const sign = paise < 0 ? '-' : '';
  const a = Math.abs(paise);
  const grouped = groupIndian(String(Math.floor(a / 100)));
  const p = a % 100;
  const body = p === 0 ? grouped : `${grouped}.${String(p).padStart(2, '0')}`;
  return `${sign}₹${body}`;
}

/** Short form for chart axes: ₹950, ₹12.5k, ₹1.2L. */
export function compactMoney(paise: number): string {
  const rupees = paise / 100;
  if (rupees < 1_000) return `₹${Math.trunc(rupees)}`;
  if (rupees < 1_00_000) return `₹${trim(rupees / 1_000)}k`;
  return `₹${trim(rupees / 1_00_000)}L`;
}

/** Parses a rupee amount typed by a user ("1,500", "12.5"). Null if invalid or negative. */
export function parseRupees(text: string): number | null {
  const t = text.trim().replaceAll(',', '').replace(/^\+/, '');
  if (!/^(\d+\.?\d*|\.\d+)$/.test(t)) return null;
  const [whole, frac = ''] = t.split('.');
  const digits = (frac + '000').slice(0, 3);
  let paise = Number(whole || '0') * 100 + Number(digits.slice(0, 2));
  if (Number(digits[2]) >= 5) paise += 1; // half-up, like BigDecimal
  return paise;
}

export function moneyToInput(paise: number): string {
  if (paise % 100 === 0) return String(paise / 100);
  return `${Math.trunc(paise / 100)}.${String(Math.abs(paise % 100)).padStart(2, '0')}`;
}

function trim(v: number): string {
  return v.toFixed(1).replace(/\.0$/, '');
}

function groupIndian(digits: string): string {
  if (digits.length <= 3) return digits;
  const last3 = digits.slice(-3);
  const rest = digits.slice(0, -3).replace(/\B(?=(\d{2})+$)/g, ',');
  return `${rest},${last3}`;
}
