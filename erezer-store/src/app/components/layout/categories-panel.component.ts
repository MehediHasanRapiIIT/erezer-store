import { Component, computed, effect, ElementRef, HostListener, inject, input, output, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ApiCategory } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { EcommerceStore } from '../../core/store/ecommerce.store';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

/**
 * The "Categories" side panel: every active category as a photo tile, with
 * shortcuts to the whole shop, the wishlist and the account underneath.
 * Opened from the header; unmounted while closed, like the mobile menu.
 */
@Component({
  selector: 'app-categories-panel',
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  template: `
    @if (open()) {
      <div class="animate-overlay-in fixed inset-0 z-50 bg-black/60 backdrop-blur-sm" (click)="close()" aria-hidden="true"></div>

      <aside
        class="animate-drawer-in-left fixed left-0 top-0 z-50 flex h-full w-[88%] max-w-md flex-col bg-white shadow-2xl dark:bg-neutral-950"
        role="dialog" aria-modal="true" aria-labelledby="categories-panel-title">

        <div class="flex items-center justify-between border-b border-neutral-200 px-6 py-5 dark:border-neutral-800">
          <h2 id="categories-panel-title" class="flex items-center gap-3 text-base font-semibold uppercase tracking-[0.12em]">
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5M3.75 17.25h16.5" />
            </svg>
            {{ 'header.categories' | t }}
          </h2>
          <button #closeButton type="button" (click)="close()"
            class="inline-flex h-10 w-10 items-center justify-center rounded-full text-neutral-700 transition hover:bg-neutral-100 dark:text-neutral-200 dark:hover:bg-neutral-800"
            [attr.aria-label]="'categories_panel.close' | t">
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
          </button>
        </div>

        <div class="flex-1 overflow-y-auto px-6 py-5">
          @if (loading()) {
            <div class="grid grid-cols-2 gap-3">
              @for (n of skeleton; track n) {
                <div class="aspect-square animate-pulse rounded-2xl bg-neutral-100 dark:bg-neutral-900"></div>
              }
            </div>
          } @else if (categories().length === 0) {
            <p class="py-10 text-center text-sm text-neutral-500 dark:text-neutral-400">{{ 'categories_panel.empty' | t }}</p>
          } @else {
            <ul class="grid grid-cols-2 gap-3">
              @for (cat of categories(); track cat.id; let i = $index) {
                <li>
                  <a [routerLink]="cat.slug ? ['/', cat.slug] : ['/shop']"
                    [queryParams]="cat.slug ? {} : { category: cat.id }"
                    (click)="close()"
                    class="group relative block aspect-square overflow-hidden rounded-2xl bg-neutral-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-neutral-950">
                    @if (cat.imageUrl) {
                      <img [src]="cat.imageUrl" [alt]="" loading="lazy"
                        class="absolute inset-0 h-full w-full object-cover transition duration-500 group-hover:scale-105" />
                    } @else {
                      <div class="absolute inset-0 bg-gradient-to-br from-neutral-700 to-neutral-900"></div>
                    }
                    <div class="absolute inset-0 bg-black/40 transition group-hover:bg-black/50"></div>
                    <div class="absolute inset-0 flex flex-col items-center justify-center px-2 text-center">
                      <span class="text-[10px] font-semibold tracking-widest text-emerald-400">{{ number(i) }}</span>
                      <span class="mt-0.5 text-sm font-bold uppercase leading-tight tracking-wide text-white drop-shadow">{{ cat.name }}</span>
                    </div>
                  </a>
                </li>
              }
            </ul>
          }
        </div>

        <div class="space-y-2 border-t border-neutral-200 bg-neutral-50 px-6 py-5 dark:border-neutral-800 dark:bg-neutral-900/60">
          <a routerLink="/shop" (click)="close()"
            class="flex w-full items-center justify-center bg-black px-4 py-3 text-xs font-semibold uppercase tracking-[0.15em] text-white transition hover:opacity-90 dark:bg-white dark:text-black">
            {{ 'categories_panel.shop_all' | t }}
          </a>
          <a routerLink="/wishlist" (click)="close()"
            class="flex w-full items-center justify-center gap-2 border border-neutral-300 bg-white px-4 py-3 text-xs font-semibold uppercase tracking-[0.15em] transition hover:border-neutral-900 dark:border-neutral-700 dark:bg-neutral-950 dark:hover:border-white">
            <svg class="h-4 w-4 text-emerald-500" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path d="M11.645 20.91l-.007-.003-.022-.012a15.247 15.247 0 01-.383-.218 25.18 25.18 0 01-4.244-3.17C4.688 15.36 2.25 12.174 2.25 8.25 2.25 5.322 4.714 3 7.688 3A5.5 5.5 0 0112 5.052 5.5 5.5 0 0116.313 3c2.973 0 5.437 2.322 5.437 5.25 0 3.925-2.438 7.111-4.739 9.256a25.175 25.175 0 01-4.244 3.17 15.247 15.247 0 01-.383.219l-.022.012-.007.004-.003.001a.752.752 0 01-.704 0l-.003-.001z"/></svg>
            {{ 'header.wishlist' | t }} ({{ wishCount() }})
          </a>
          <a routerLink="/account" (click)="close()"
            class="flex w-full items-center justify-center gap-2 border border-neutral-300 bg-white px-4 py-3 text-xs font-semibold uppercase tracking-[0.15em] transition hover:border-neutral-900 dark:border-neutral-700 dark:bg-neutral-950 dark:hover:border-white">
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z"/></svg>
            {{ (auth.isAuthenticated() ? 'categories_panel.my_account' : 'categories_panel.sign_in') | t }}
          </a>
        </div>
      </aside>
    }
  `,
})
export class CategoriesPanelComponent {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly store = inject(EcommerceStore);

  readonly open = input(false);
  readonly closed = output<void>();

  protected readonly categories = signal<ApiCategory[]>([]);
  protected readonly loading = signal(false);
  protected readonly wishCount = computed(() => this.store.wishlist().length);
  protected readonly skeleton = [1, 2, 3, 4, 5, 6];

  private readonly closeButton = viewChild<ElementRef<HTMLButtonElement>>('closeButton');
  private loaded = false;

  constructor() {
    effect(() => {
      if (!this.open()) return;
      // Categories rarely change during a visit: load them the first time the panel opens.
      if (!this.loaded) {
        this.loaded = true;
        this.loading.set(true);
        this.api.getCategories().pipe(catchError(() => of([] as ApiCategory[]))).subscribe((cats) => {
          this.categories.set(cats.filter((c) => c.isActive !== false));
          this.loading.set(false);
        });
      }
      // Put keyboard focus inside the panel once it is on screen.
      queueMicrotask(() => this.closeButton()?.nativeElement.focus());
    });
  }

  /** "01", "02", … as on the tiles. */
  protected number(index: number): string {
    return String(index + 1).padStart(2, '0');
  }

  protected close(): void {
    this.closed.emit();
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.open()) this.close();
  }
}
