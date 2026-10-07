/** Indian mobile numbers. Stored for display as "+91 98450 12001". */

/** The 10-digit national number, or null if [input] isn't a valid Indian mobile. */
export function nationalDigits(input: string): string | null {
  let d = input.replace(/\D/g, '');
  if (d.length === 12 && d.startsWith('91')) d = d.slice(2);
  if (d.length === 11 && d.startsWith('0')) d = d.slice(1);
  return d.length === 10 && /^[6-9]/.test(d) ? d : null;
}

export function displayPhone(input: string): string | null {
  const d = nationalDigits(input);
  return d ? `+91 ${d.slice(0, 5)} ${d.slice(5)}` : null;
}

/** E.164 form for an SMS link, e.g. "+919845012001". */
export function e164Phone(input: string): string | null {
  const d = nationalDigits(input);
  return d ? `+91${d}` : null;
}
