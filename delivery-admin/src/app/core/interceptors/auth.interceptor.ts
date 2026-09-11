import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';
import { NoticeService } from '../services/notice.service';
import { PermissionService } from '../services/permission.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const permissions = inject(PermissionService);
  const notices = inject(NoticeService);

  return from(auth.getToken()).pipe(
    switchMap((token) => {
      const authReq = token
        ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
        : req;
      return next(authReq);
    }),
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        auth.login();
      } else if (error instanceof HttpErrorResponse && error.status === 403 && isRefusal(req.url)) {
        // Refused by the permission check. Say so plainly, and re-read
        // permissions in case they just changed, so the panel stops offering it.
        notices.show('error', 'Not allowed', error.error?.message ?? "You don't have permission for this.");
        permissions.refresh();
      }
      return throwError(() => error);
    })
  );
};

/**
 * A 403 from our backend for an action. /admin/me and /admin/permissions
 * answer 403 for logins that aren't staff; the "No access" page explains those.
 */
function isRefusal(url: string): boolean {
  return url.startsWith(environment.apiBaseUrl) && !/\/admin\/(me|permissions)(\?|$)/.test(url);
}
