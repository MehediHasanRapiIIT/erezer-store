import { HttpErrorResponse } from '@angular/common/http';

/**
 * Extracts a human-readable error message from a backend HttpErrorResponse.
 *
 * Handles three backend error shapes:
 *  - Business error:    { message: string, timestamp: string }
 *  - Validation error: { errors: Record<string, string>, timestamp: string }
 *  - Fallback:         generic status-code message
 */
export function parseApiError(error: HttpErrorResponse): string {
  const body = error.error;

  if (body?.message) {
    return body.message as string;
  }

  if (body?.errors && typeof body.errors === 'object') {
    const entries = Object.entries(body.errors as Record<string, string>);
    if (entries.length > 0) {
      return entries.map(([field, msg]) => `${field}: ${msg}`).join('; ');
    }
  }

  return `Request failed (${error.status})`;
}
