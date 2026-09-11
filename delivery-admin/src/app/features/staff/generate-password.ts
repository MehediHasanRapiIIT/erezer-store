/** Letters and digits that can't be mistaken for each other (no I, O, l, o, 0, 1). */
const UPPER = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
const LOWER = 'abcdefghijkmnpqrstuvwxyz';
const DIGITS = '23456789';
const CHARS = UPPER + LOWER + DIGITS;

/**
 * A random temporary password, easy to read out or type. Uses the browser's
 * cryptographic random source and always mixes upper case, lower case and
 * digits.
 */
export function generatePassword(length = 14): string {
  // Bytes at or above `limit` are thrown away so every character is equally likely.
  const limit = 256 - (256 % CHARS.length);
  for (;;) {
    let out = '';
    while (out.length < length) {
      for (const b of crypto.getRandomValues(new Uint8Array(length * 2))) {
        if (b < limit && out.length < length) out += CHARS[b % CHARS.length];
      }
    }
    if (/[A-Z]/.test(out) && /[a-z]/.test(out) && /[2-9]/.test(out)) return out;
  }
}
