/**
 * Calendar helpers for the admin panel that agree with the backend's
 * BusinessCalendar: dates are Asia/Dhaka, not the browser's zone.
 *
 * `new Date().toISOString().slice(0, 10)` is the classic bug here: it yields
 * the UTC date, which after 18:00 UTC is already "tomorrow" in Dhaka, so a
 * manager checking at 11 pm would request the wrong day's report.
 */

export const BUSINESS_ZONE = 'Asia/Dhaka';
export const BUSINESS_CURRENCY = 'BDT';

/** Today's date in the business zone as yyyy-MM-dd. */
export function businessToday(): string {
  // en-CA formats as yyyy-MM-dd.
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: BUSINESS_ZONE, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}

/** Parse yyyy-MM-dd into a local Date at noon (safe from DST/UTC shifts). */
export function parseIsoDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d, 12, 0, 0, 0);
}

export function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function addDays(iso: string, days: number): string {
  const d = parseIsoDate(iso);
  d.setDate(d.getDate() + days);
  return toIsoDate(d);
}

export function addMonths(iso: string, months: number): string {
  const d = parseIsoDate(iso);
  const day = d.getDate();
  d.setDate(1);
  d.setMonth(d.getMonth() + months);
  // Clamp to the target month's length (31 Jan + 1 month → 28/29 Feb).
  const last = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  d.setDate(Math.min(day, last));
  return toIsoDate(d);
}

export function addYears(iso: string, years: number): string {
  return addMonths(iso, years * 12);
}

/** Weekday name for a yyyy-MM-dd date, e.g. "Sun". */
export function weekdayShort(iso: string): string {
  return ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][parseIsoDate(iso).getDay()];
}

/** "4 Sep 2026" */
export function formatDate(iso: string): string {
  return parseIsoDate(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

/** "4 Sep" */
export function formatDayMonth(iso: string): string {
  return parseIsoDate(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
}

/**
 * Bangladeshi taka with the Indian-style lakh/crore grouping used on every
 * invoice and bank statement in the country: ৳12,34,567.
 */
export function formatTaka(amount: number | null | undefined, fractionDigits = 0): string {
  const value = Number(amount ?? 0);
  const formatted = new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(Math.abs(value));
  return (value < 0 ? '-' : '') + '৳' + formatted;
}

/** Compact form for tiles: ৳1.2L, ৳3.4Cr. */
export function formatTakaCompact(amount: number | null | undefined): string {
  const value = Number(amount ?? 0);
  const abs = Math.abs(value);
  const sign = value < 0 ? '-' : '';
  if (abs >= 1e7) return `${sign}৳${(abs / 1e7).toFixed(2)}Cr`;
  if (abs >= 1e5) return `${sign}৳${(abs / 1e5).toFixed(2)}L`;
  return formatTaka(value);
}

/** Percentage change from `previous` to `current`; null when there is no baseline. */
export function percentChange(current: number, previous: number): number | null {
  if (!previous) return null;
  return Math.round(((current - previous) / Math.abs(previous)) * 1000) / 10;
}
