import { AfterViewInit, Component, ElementRef, OnDestroy, inject, viewChild } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { ADMIN_PAGES, AdminPage } from '../../core/access/admin-pages';
import { AuthService } from '../../core/services/auth.service';
import { PermissionService } from '../../core/services/permission.service';
import { ThemeService } from '../../core/services/theme.service';
import { GlobalSearchComponent } from '../global-search/global-search.component';

/**
 * How far the menu is scrolled. Every page mounts its own sidebar, so opening a
 * page built a fresh menu scrolled to the top - clicking an item low in the list
 * threw the admin back up. Kept outside the component so the next page's
 * sidebar starts where the last one was; null until the menu has been used.
 */
let savedMenuScroll: number | null = null;

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, GlobalSearchComponent],
  templateUrl: './sidebar.component.html',
})
export class SidebarComponent implements AfterViewInit, OnDestroy {
  protected auth = inject(AuthService);
  protected perms = inject(PermissionService);
  protected theme = inject(ThemeService);

  private readonly menu = viewChild.required<ElementRef<HTMLElement>>('menu');
  // A plain listener, not a (scroll) binding, so scrolling doesn't run change detection.
  private readonly remember = () => { savedMenuScroll = this.menu().nativeElement.scrollTop; };

  /** Only the pages this person may open; follows permission changes as they happen. */
  get navItems(): AdminPage[] {
    return ADMIN_PAGES.filter((p) => this.perms.allows(p.access));
  }

  ngAfterViewInit(): void {
    const menu = this.menu().nativeElement;
    if (savedMenuScroll !== null) {
      menu.scrollTop = savedMenuScroll;
    } else {
      // First menu of this visit, e.g. a reload on Settings: show the current page's
      // item. Matched by href, not aria-current - routerLinkActive only marks the
      // link after the first navigation ends, which is later than this.
      const path = window.location.pathname;
      Array.from(menu.querySelectorAll('a'))
        .find((a) => a.getAttribute('href') === path)
        ?.scrollIntoView({ block: 'nearest' });
    }
    menu.addEventListener('scroll', this.remember, { passive: true });
  }

  ngOnDestroy(): void {
    this.menu().nativeElement.removeEventListener('scroll', this.remember);
  }
}
