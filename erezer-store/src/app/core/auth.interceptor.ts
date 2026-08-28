import { HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const AUTH_FREE_SUFFIXES = [
  '/app/auth/login',
  '/app/auth/register',
  '/app/auth/refresh',
  '/app/auth/forgot-password',
  '/app/auth/reset-password',
  '/app/auth/verify-email',
  '/app/auth/resend-verification',
];

function isAuthFree(url: string): boolean {
  return AUTH_FREE_SUFFIXES.some((s) => url.endsWith(s));
}

function withBearer(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // Don't attach a token to the auth endpoints themselves.
  if (isAuthFree(req.url)) {
    return next(req);
  }

  const access = auth.accessToken();
  const authed = access ? withBearer(req, access) : req;

  return next(authed).pipe(
    catchError((err) => {
      const refresh = auth.refreshToken();
      const canRetry = err?.status === 401 && !!access && !!refresh && !isAuthFree(req.url);
      if (!canRetry) {
        return throwError(() => err);
      }

      // Try a one-shot refresh, then replay the original request.
      return from(auth.refresh()).pipe(
        switchMap((tokens) => next(withBearer(req, tokens.accessToken))),
        catchError((refreshErr) => {
          auth.signOut();
          router.navigate(['/account']);
          return throwError(() => refreshErr);
        }),
      );
    }),
  );
};
