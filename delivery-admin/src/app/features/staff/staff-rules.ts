/** Rules the backend applies to staff logins, checked here first so the message is clear. */

export const USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,50}$/;

/** Staff logins need an email; the backend refuses a blank or malformed one. */
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** What is wrong with a temporary password, or null when it is fine. */
export function passwordProblem(password: string): string | null {
  if (password.length < 8) return 'The temporary password must be at least 8 characters.';
  if (password.length > 100) return 'The temporary password must be at most 100 characters.';
  return null;
}
