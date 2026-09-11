import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AccessRule, firstAllowedPage } from '../access/admin-pages';
import { PermissionService } from '../services/permission.service';

/**
 * Lets someone open a page only if their permissions allow it, per the
 * page's `data.access` rule. The landing page (`data.landing`) sends a person
 * who may not open it to their first permitted page instead, so a moderator
 * without the dashboard still lands somewhere useful after logging in.
 */
export const accessGuard: CanActivateFn = async (route, state) => {
  const perms = inject(PermissionService);
  const router = inject(Router);
  await perms.ensureLoaded();

  const rule = route.data['access'] as AccessRule | undefined;
  const noAccess = () =>
    router.createUrlTree(['/no-access'], {
      queryParams: { from: state.url, page: route.data['title'] ?? null },
    });

  if (!perms.me()) return noAccess();
  if (perms.allows(rule)) return true;
  if (route.data['landing']) {
    const home = firstAllowedPage(perms);
    if (home) return router.createUrlTree([home]);
  }
  return noAccess();
};
