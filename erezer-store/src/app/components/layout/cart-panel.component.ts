import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, ElementRef, HostListener, inject, input, output, signal, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ApiProduct } from '../../core/api.models';
import { CartActionsService } from '../../core/cart-actions.service';
import { baseProductPrice, effectiveUnitPrice, isDiscountExcluded } from '../../core/discount-pricing';
import { EcommerceStore } from '../../core/store/ecommerce.store';
import { DiscountsStore } from '../../core/store/discounts.store';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

/**
 * The cart as a side panel, opened from the header's cart icon: lines with
 * quantity and remove, suggestions, the total and the way to checkout.
 * Unmounted while closed, like the other panels.
 */
@Component({
  selector: 'app-cart-panel',
  standalone: true,
  imports: [CurrencyPipe, RouterLink, TranslatePipe],
  template: `
    @if (open()) {
      <div class="animate-overlay-in fixed inset-0 z-50 bg-black/50 backdrop-blur-[2px]" (click)="close()" aria-hidden="true"></div>

      <aside class="animate-drawer-in fixed right-0 top-0 z-50 flex h-full w-full max-w-md flex-col bg-white shadow-2xl dark:bg-neutral-950"
        role="dialog" aria-modal="true" aria-labelledby="cart-panel-title">

        <div class="flex items-center justify-between border-b border-neutral-200 px-5 py-4 dark:border-neutral-800">
          <h2 id="cart-panel-title" class="text-sm font-semibold uppercase tracking-[0.14em]">
            {{ 'cart_panel.title' | t }} <span class="text-neutral-400">({{ store.cartCount() }})</span>
          </h2>
          <button #closeButton type="button" (click)="close()"
            class="inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm text-neutral-600 transition hover:bg-neutral-100 hover:text-black dark:text-neutral-300 dark:hover:bg-neutral-800 dark:hover:text-white"
            [attr.aria-label]="'cart_panel.close' | t">
            {{ 'cart_panel.close_label' | t }}
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" /></svg>
          </button>
        </div>

        <div class="flex-1 overflow-y-auto">
          @if (lines().length === 0) {
            <div class="flex flex-col items-center px-8 py-16 text-center">
              <svg class="h-12 w-12 text-neutral-300 dark:text-neutral-700" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.2" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 00-3 3h15.75m-12.75-3h11.218c1.121-2.3 2.1-4.684 2.924-7.138a60.114 60.114 0 00-16.536-1.84M7.5 14.25L5.106 5.272M6 20.25a.75.75 0 11-1.5 0 .75.75 0 011.5 0zm12.75 0a.75.75 0 11-1.5 0 .75.75 0 011.5 0z" /></svg>
              <p class="mt-4 font-medium">{{ 'cart_panel.empty' | t }}</p>
              <a routerLink="/shop" (click)="close()" class="btn-primary mt-5 !rounded-full">{{ 'cart_panel.start_shopping' | t }}</a>
            </div>
          } @else {
            <ul class="space-y-3 px-5 py-4">
              @for (line of lines(); track line.productId + '|' + line.size) {
                <li class="flex items-center gap-3 rounded-xl border border-neutral-200 p-3 dark:border-neutral-800">
                  <a [routerLink]="['/product', line.productId]" (click)="close()" class="shrink-0">
                    @if (line.product.image) {
                      <img [src]="line.product.image" [alt]="line.product.name" class="h-16 w-16 rounded-lg object-cover" />
                    } @else {
                      <div class="h-16 w-16 rounded-lg bg-neutral-100 dark:bg-neutral-900"></div>
                    }
                  </a>
                  <div class="min-w-0 flex-1">
                    <p class="truncate text-sm font-medium">{{ line.product.name }}</p>
                    @if (sizeLabel(line.size); as size) {
                      <p class="text-xs text-neutral-500 dark:text-neutral-400">{{ size }}</p>
                    }
                    <div class="mt-1.5 flex flex-wrap items-center gap-2 text-xs">
                      <div class="inline-flex items-center rounded-full border border-neutral-300 dark:border-neutral-700">
                        <button type="button" (click)="cart.decrease(line.productId, line.variantId, line.size, line.quantity)"
                          class="h-7 w-7 rounded-full text-base leading-none hover:bg-neutral-100 dark:hover:bg-neutral-800"
                          [attr.aria-label]="('cart_panel.decrease' | t) + ' ' + line.product.name">−</button>
                        <span class="w-6 text-center tabular-nums" aria-live="polite">{{ line.quantity }}</span>
                        <button type="button" (click)="cart.increase(line.productId, line.variantId, line.size, line.quantity)"
                          [disabled]="!!line.customMeasurements"
                          class="h-7 w-7 rounded-full text-base leading-none hover:bg-neutral-100 disabled:opacity-40 dark:hover:bg-neutral-800"
                          [attr.aria-label]="('cart_panel.increase' | t) + ' ' + line.product.name">+</button>
                      </div>
                      <span class="text-neutral-500 dark:text-neutral-400">× {{ line.unitPrice | currency:'BDT':'৳' }}</span>
                      <span class="font-semibold tabular-nums">= {{ line.subtotal | currency:'BDT':'৳' }}</span>
                    </div>
                  </div>
                  <button type="button" (click)="cart.remove(line.productId, line.variantId, line.size)"
                    class="self-start rounded-full p-1.5 text-neutral-400 transition hover:bg-neutral-100 hover:text-neutral-900 dark:hover:bg-neutral-800 dark:hover:text-white"
                    [attr.aria-label]="('cart_panel.remove' | t) + ' ' + line.product.name">
                    <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
                  </button>
                </li>
              }
            </ul>
          }
        </div>

        @if (lines().length > 0) {
          <!-- You may also like -->
          @if (suggestions().length > 0) {
            <div class="border-t border-neutral-200 bg-neutral-50 px-5 py-4 dark:border-neutral-800 dark:bg-neutral-900/60">
              <div class="mb-3 flex items-center justify-between">
                <h3 class="text-sm font-semibold">{{ 'cart_panel.also_like' | t }}</h3>
                <div class="flex gap-1.5">
                  <button type="button" (click)="scrollSuggestions(-1)" class="inline-flex h-7 w-7 items-center justify-center rounded-full border border-neutral-300 hover:bg-white dark:border-neutral-700 dark:hover:bg-neutral-800" [attr.aria-label]="'cart_panel.previous' | t">‹</button>
                  <button type="button" (click)="scrollSuggestions(1)" class="inline-flex h-7 w-7 items-center justify-center rounded-full border border-neutral-300 hover:bg-white dark:border-neutral-700 dark:hover:bg-neutral-800" [attr.aria-label]="'cart_panel.next' | t">›</button>
                </div>
              </div>
              <div #suggestionRow class="no-scrollbar flex snap-x gap-3 overflow-x-auto scroll-smooth">
                @for (p of suggestions(); track p.id) {
                  <div class="flex w-64 shrink-0 snap-start items-center gap-3 rounded-xl border border-neutral-200 bg-white p-2.5 dark:border-neutral-800 dark:bg-neutral-950">
                    <a [routerLink]="['/product', p.id]" (click)="close()" class="shrink-0">
                      <img [src]="p.imageUrl" [alt]="p.name" loading="lazy" class="h-14 w-14 rounded-lg object-cover" />
                    </a>
                    <div class="min-w-0 flex-1">
                      <p class="truncate text-xs font-medium">{{ p.name }}</p>
                      <p class="text-xs text-neutral-500 dark:text-neutral-400 tabular-nums">{{ priceOf(p) | currency:'BDT':'৳' }}</p>
                      <button type="button" (click)="add(p)" [disabled]="adding() === p.id"
                        class="mt-1 inline-flex items-center rounded-full bg-black px-2.5 py-0.5 text-[11px] font-semibold text-white disabled:opacity-50 dark:bg-white dark:text-black"
                        [attr.aria-label]="('cart_panel.add' | t) + ' ' + p.name">
                        {{ adding() === p.id ? '…' : ('+ ' + ('cart_panel.add' | t)) }}
                      </button>
                    </div>
                  </div>
                }
              </div>
            </div>
          }

          <div class="space-y-3 border-t border-neutral-200 px-5 py-4 dark:border-neutral-800">
            <div class="flex items-baseline justify-between">
              <span class="text-sm font-medium">{{ 'cart_panel.total' | t }}</span>
              <span class="text-lg font-semibold tabular-nums" data-testid="cart-panel-total">{{ store.cartSubtotal() | currency:'BDT':'৳' }}</span>
            </div>
            <p class="text-xs text-neutral-500 dark:text-neutral-400">{{ 'cart_panel.shipping_note' | t }}</p>
            <a routerLink="/checkout" (click)="close()" class="btn-primary flex w-full justify-center !rounded-full uppercase tracking-[0.12em]">{{ 'cart_panel.checkout' | t }}</a>
            <a routerLink="/cart" (click)="close()" class="block text-center text-xs text-neutral-600 underline underline-offset-4 hover:text-black dark:text-neutral-300 dark:hover:text-white">{{ 'cart_panel.view_cart' | t }}</a>
          </div>
        }
      </aside>
    }
  `,
})
export class CartPanelComponent {
  protected readonly store = inject(EcommerceStore);
  protected readonly cart = inject(CartActionsService);
  private readonly api = inject(ApiService);
  private readonly discounts = inject(DiscountsStore);
  private readonly router = inject(Router);

  readonly open = input(false);
  readonly closed = output<void>();

  protected readonly lines = computed(() => this.store.cartItemsDetailed());
  protected readonly suggestions = signal<ApiProduct[]>([]);
  protected readonly adding = signal<number | null>(null);

  private readonly closeButton = viewChild<ElementRef<HTMLButtonElement>>('closeButton');
  private readonly suggestionRow = viewChild<ElementRef<HTMLDivElement>>('suggestionRow');
  private suggestionsFor = '';

  constructor() {
    effect(() => {
      if (!this.open()) return;
      // A signed-in customer's cart lives on the server: show exactly what it holds.
      this.cart.refresh().subscribe();
      queueMicrotask(() => this.closeButton()?.nativeElement.focus());
    });
    // Suggestions follow the first product in the cart.
    effect(() => {
      if (!this.open()) return;
      const first = this.lines()[0]?.productId ?? '';
      if (!first || first === this.suggestionsFor) return;
      this.suggestionsFor = first;
      this.api.getRelatedProducts(Number(first), 8).pipe(catchError(() => of([] as ApiProduct[]))).subscribe((list) => {
        const inCart = new Set(this.lines().map((l) => l.productId));
        this.suggestions.set(list.filter((p) => p.isAvailable && !inCart.has(String(p.id))));
      });
    });
  }

  /** "One Size" and custom-measurement keys say nothing useful to the shopper. */
  protected sizeLabel(size: string): string {
    if (!size || size === 'One Size') return '';
    return size.startsWith('Custom-') ? 'Custom size' : size;
  }

  protected priceOf(p: ApiProduct): number {
    return effectiveUnitPrice(baseProductPrice(p.price, p.discountPrice), p.id, p.categoryId,
      this.discounts.discounts(), isDiscountExcluded(p));
  }

  protected add(p: ApiProduct): void {
    this.adding.set(p.id);
    this.cart.quickAdd(p).subscribe((result) => {
      this.adding.set(null);
      if (result === 'added') {
        this.suggestions.update((list) => list.filter((x) => x.id !== p.id));
      } else if (result === 'choose-size') {
        this.close();
        void this.router.navigate(['/product', p.id]);
      }
    });
  }

  protected scrollSuggestions(direction: -1 | 1): void {
    this.suggestionRow()?.nativeElement.scrollBy({ left: direction * 272, behavior: 'smooth' });
  }

  protected close(): void {
    this.closed.emit();
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.open()) this.close();
  }
}
