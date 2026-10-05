import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { HomeLayoutSection, HomeLayoutService } from '../../core/services/home-layout.service';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

/** What a section is called, and when it actually appears on the page. */
const SECTIONS: Record<string, { name: string; note: string }> = {
  SPLIT_BAND: {
    name: 'Two-panel band',
    note: 'Two large pictures side by side. Appears once both have a banner (Banners).',
  },
  TILE_GRID: {
    name: 'Category tile grid',
    note: 'Four picture tiles. Appears once the grid has banners (Banners).',
  },
  CUSTOM_DESIGN_PROMO: {
    name: 'Custom design promotion',
    note: 'The “design your own” band. Appears once it has a banner (Banners).',
  },
  FLASH_SALE: {
    name: 'Flash sale',
    note: 'Only appears while a flash sale is running (Flash Sales).',
  },
  FEATURED_BUNDLE: {
    name: 'Featured bundle',
    note: 'Only appears while a bundle is featured (Bundles).',
  },
  SHOP_BY_CATEGORY: {
    name: 'Shop by category',
    note: 'A picture for each category.',
  },
  HIGHLIGHTS: {
    name: 'Highlights',
    note: 'The row of icons — delivery, quality, support. Edited in Settings.',
  },
  NEW_ARRIVALS: {
    name: 'New arrivals',
    note: 'Products marked “New arrival”. Appears when at least one is.',
  },
  FEATURED_PRODUCTS: {
    name: 'Featured products',
    note: 'Products marked “Featured”.',
  },
  CATEGORY_COLLECTIONS: {
    name: 'Category collections',
    note: 'A row of products for each category marked “Show on home”. They stay together, in the order set on each category.',
  },
  MARQUEE: {
    name: 'Scrolling text strip',
    note: 'Short phrases scrolling across the page. The phrases are written in Settings.',
  },
  RECENTLY_VIEWED: {
    name: 'Recently viewed',
    note: 'What each customer looked at lately. Only appears for someone who has looked at products.',
  },
  OUR_STORY: {
    name: 'Our story',
    note: 'Your story and its gallery. Written in Settings.',
  },
  NEWSLETTER: {
    name: 'Newsletter sign-up',
    note: 'The box where customers leave their email.',
  },
};

/**
 * Home page layout: which sections of the shop's home page are shown, and in
 * what order. The big top banner is always first and always shown, so it is
 * drawn here as a fixed row; everything under it can be switched and moved.
 *
 * Changes are made on this page first and reach the shop only on "Save layout",
 * so a half-finished rearrangement is never live.
 */
@Component({
  selector: 'app-home-layout',
  standalone: true,
  imports: [SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <h1 class="text-lg font-bold text-gray-900">Home page layout</h1>
          @if (layout().length) {
            <p class="text-xs text-gray-500" data-testid="shown-count">{{ shownCount() }} of {{ layout().length }} sections shown</p>
          }
        </header>

        <main class="flex-1 overflow-y-auto p-6 pb-24">
          <div class="max-w-3xl mx-auto space-y-4">

            <p class="text-sm text-gray-600">
              Choose which sections the shop's home page shows, and in what order. Switch one off to hide it; use the
              arrows to move it up or down. Nothing changes in the shop until you press <strong>Save layout</strong>.
            </p>

            @if (errorMessage()) {
              <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ errorMessage() }}</p>
            }
            @if (!canEdit()) {
              <p class="rounded-md bg-gray-100 px-3 py-2 text-xs text-gray-500">
                You can see the layout. Changing it needs the “Edit home page content” permission.
              </p>
            }

            @if (loading()) {
              <p class="text-sm text-gray-400">Loading…</p>
            } @else {
              <ol class="rounded-xl border border-gray-200 bg-white divide-y divide-gray-100" data-testid="layout-list">
                <!-- Always first, always shown: the page is built around it. -->
                <li class="flex items-center gap-4 px-4 py-3 bg-gray-50">
                  <span class="w-6 text-center text-sm font-semibold text-gray-400">1</span>
                  <div class="min-w-0 flex-1">
                    <p class="text-sm font-semibold text-gray-700">Big top banner</p>
                    <p class="text-xs text-gray-500">The slideshow at the top. Always first and always shown. Its pictures are set in Banners.</p>
                  </div>
                  <span class="rounded-full bg-gray-200 px-2.5 py-1 text-[11px] font-semibold text-gray-600">Fixed</span>
                </li>

                @for (row of layout(); track row.key; let i = $index; let first = $first; let last = $last) {
                  <li class="flex items-center gap-4 px-4 py-3 transition-colors" [class.opacity-60]="!row.enabled"
                    [attr.data-testid]="'row-' + row.key">
                    <span class="w-6 text-center text-sm font-semibold text-gray-400">{{ i + 2 }}</span>
                    <div class="min-w-0 flex-1">
                      <p class="text-sm font-semibold" [class.text-gray-900]="row.enabled" [class.text-gray-500]="!row.enabled">
                        {{ nameOf(row.key) }}
                        @if (!row.enabled) {
                          <span class="ml-1 rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-semibold text-gray-500">Hidden</span>
                        }
                      </p>
                      <p class="text-xs text-gray-500">{{ noteOf(row.key) }}</p>
                    </div>

                    <div class="flex items-center gap-1">
                      <button type="button" (click)="move(i, -1)" [disabled]="first || !canEdit() || saving()"
                        [attr.aria-label]="'Move ' + nameOf(row.key) + ' up'" title="Move up"
                        class="rounded-lg border border-gray-200 px-2 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-30 disabled:cursor-not-allowed">↑</button>
                      <button type="button" (click)="move(i, 1)" [disabled]="last || !canEdit() || saving()"
                        [attr.aria-label]="'Move ' + nameOf(row.key) + ' down'" title="Move down"
                        class="rounded-lg border border-gray-200 px-2 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-30 disabled:cursor-not-allowed">↓</button>
                    </div>

                    <button type="button" role="switch" [attr.aria-checked]="row.enabled"
                      [attr.aria-label]="'Show ' + nameOf(row.key)" (click)="toggle(i)"
                      [disabled]="!canEdit() || saving()"
                      class="relative inline-flex h-7 flex-shrink-0 rounded-full transition-colors disabled:opacity-50"
                      [class.bg-emerald-500]="row.enabled" [class.bg-gray-300]="!row.enabled" style="width:3.25rem">
                      <span class="absolute top-1 h-5 w-5 rounded-full bg-white shadow transition-all"
                        [style.left]="row.enabled ? '1.75rem' : '0.25rem'"></span>
                    </button>
                  </li>
                }
              </ol>

              @if (canEdit()) {
                <button type="button" (click)="reset()" [disabled]="saving() || isOriginal()"
                  class="text-xs font-medium text-gray-500 hover:text-gray-800 hover:underline disabled:opacity-40 disabled:no-underline">
                  Put everything back to the original order, all switched on
                </button>
              }
            }
          </div>
        </main>

        @if (canEdit() && !loading()) {
          <div class="fixed bottom-0 left-52 right-0 bg-white border-t border-gray-200 px-6 py-3 flex items-center justify-between z-10">
            <p class="text-sm" [class.text-blue-600]="dirty()" [class.text-gray-400]="!dirty()" data-testid="layout-status">
              {{ dirty() ? 'Unsaved changes — the shop still shows the old layout.' : 'The shop shows this layout.' }}
            </p>
            <div class="flex items-center gap-3">
              <button type="button" (click)="undo()" [disabled]="!dirty() || saving()"
                class="px-4 py-2 text-sm font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-40">
                Undo changes
              </button>
              <button type="button" (click)="save()" [disabled]="!dirty() || saving()" data-testid="layout-save"
                class="px-5 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed">
                {{ saving() ? 'Saving…' : 'Save layout' }}
              </button>
            </div>
          </div>
        }
      </div>
    </div>
  `,
})
export class HomeLayoutComponent implements OnInit {
  private readonly api = inject(HomeLayoutService);
  protected readonly perms = inject(PermissionService);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  /** The layout as it is being arranged on this page. */
  readonly layout = signal<HomeLayoutSection[]>([]);
  /** The layout the shop is showing, to tell whether anything has changed. */
  private readonly saved = signal<HomeLayoutSection[]>([]);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal('');

  protected readonly canEdit = computed(() => this.perms.can('settings.homepage'));
  protected readonly shownCount = computed(() => this.layout().filter((s) => s.enabled).length);
  protected readonly dirty = computed(() => !sameLayout(this.layout(), this.saved()));
  /** True when the page is exactly as it was built: original order, everything on. */
  protected readonly isOriginal = computed(() => {
    const original = Object.keys(SECTIONS);
    const now = this.layout();
    return now.length === original.length && now.every((s, i) => s.enabled && s.key === original[i]);
  });

  ngOnInit(): void {
    this.api.get().subscribe({
      next: (layout) => { this.apply(layout); this.loading.set(false); },
      error: (err) => { this.errorMessage.set(parseApiError(err)); this.loading.set(false); },
    });
  }

  protected nameOf(key: string): string {
    return SECTIONS[key]?.name ?? key;
  }

  protected noteOf(key: string): string {
    return SECTIONS[key]?.note ?? '';
  }

  protected toggle(index: number): void {
    this.layout.update((rows) => rows.map((r, i) => (i === index ? { ...r, enabled: !r.enabled } : r)));
  }

  protected move(index: number, by: -1 | 1): void {
    const target = index + by;
    this.layout.update((rows) => {
      if (target < 0 || target >= rows.length) return rows;
      const next = [...rows];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  protected undo(): void {
    this.layout.set(this.saved().map((s) => ({ ...s })));
    this.errorMessage.set('');
  }

  protected save(): void {
    if (!this.dirty() || this.saving()) return;
    this.send(this.layout(), 'Home page layout saved');
  }

  /** Back to how the page was built. Asked first, because it replaces the shop's own arrangement. */
  protected async reset(): Promise<void> {
    const ok = await this.confirmer.ask({
      title: 'Put the home page back to its original layout?',
      message: 'Every section is switched on and returns to its original place. The shop changes straight away.',
      confirmLabel: 'Reset the layout',
      danger: true,
    });
    if (!ok) return;
    this.send(Object.keys(SECTIONS).map((key) => ({ key, enabled: true })), 'Home page layout reset');
  }

  private send(layout: HomeLayoutSection[], title: string): void {
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.save(layout).subscribe({
      next: (stored) => {
        this.apply(stored);
        this.saving.set(false);
        const shown = stored.filter((s) => s.enabled).length;
        this.notices.success(title, `${shown} of ${stored.length} sections shown on the shop's home page.`);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(parseApiError(err));
      },
    });
  }

  private apply(layout: HomeLayoutSection[]): void {
    this.saved.set(layout.map((s) => ({ ...s })));
    this.layout.set(layout.map((s) => ({ ...s })));
  }
}

function sameLayout(a: HomeLayoutSection[], b: HomeLayoutSection[]): boolean {
  return a.length === b.length && a.every((s, i) => s.key === b[i].key && s.enabled === b[i].enabled);
}
