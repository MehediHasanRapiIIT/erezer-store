import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { ADMIN_PAGES, AdminPage } from '../../core/access/admin-pages';
import { AuthService } from '../../core/services/auth.service';
import { PermissionService } from '../../core/services/permission.service';
import { ThemeService } from '../../core/services/theme.service';
import { GlobalSearchComponent } from '../global-search/global-search.component';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, GlobalSearchComponent],
  templateUrl: './sidebar.component.html',
})
export class SidebarComponent {
  protected auth = inject(AuthService);
  protected perms = inject(PermissionService);
  protected theme = inject(ThemeService);

  /** Only the pages this person may open; follows permission changes as they happen. */
  get navItems(): AdminPage[] {
    return ADMIN_PAGES.filter((p) => this.perms.allows(p.access));
  }
}
