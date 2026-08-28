import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { CouponValidateResponse } from '../core/api.models';
import { EcommerceStore } from '../core/store/ecommerce.store';
import { AuthService } from '../core/auth.service';
import { RevealDirective } from '../core/reveal.directive';

@Component({
  standalone: true,
  imports: [CurrencyPipe, RouterLink, FormsModule, RevealDirective],
  template: `
    <!-- ── Header ────────────────────────────────────────────────────────── -->
    <div class="mb-8" appReveal>
      <p class="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-500 dark:text-neutral-400">Shopping bag</p>
      <h1 class="app-section-title mt-2 text-3xl md:text-4xl">
        Your cart
        @if (!loading() && itemCount() > 0) {
          <span class="ml-2 align-middle text-base font-medium app-muted">({{ itemCount() }} {{ itemCount() === 1 ? 'item' : 'items' }})</span>
        }
      </h1>
    </div>

    @if (loading()) {
      <!-- Skeleton -->
      <div class="grid gap-8 lg:grid-cols-[2fr_1fr]">
        <div class="space-y-4">
          @for (s of [0,1,2]; track s) {
            <div class="app-card flex gap-4 p-4">
              <div class="h-32 w-28 flex-shrink-0 animate-pulse rounded-xl bg-neutral-200 dark:bg-neutral-800"></div>
              <div class="flex-1 space-y-3 py-1">
                <div class="h-4 w-2/3 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800"></div>
                <div class="h-3 w-20 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800"></div>
                <div class="h-9 w-32 animate-pulse rounded-full bg-neutral-200 dark:bg-neutral-800"></div>
              </div>
            </div>
          }
        </div>
        <div class="app-card h-64 animate-pulse bg-neutral-100 dark:bg-neutral-900"></div>
      </div>
    } @else if (itemCount() === 0) {
      <!-- Empty state -->
      <div class="flex flex-col items-center justify-center gap-4 py-20 text-center" appReveal>
        <span class="inline-flex h-20 w-20 items-center justify-center rounded-full bg-neutral-100 text-neutral-400 dark:bg-neutral-800">
          <svg class="h-9 w-9" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.4">
            <path stroke-linecap="round" stroke-linejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 00-3 3h15.75m-12.75-3h11.218c1.121-2.3 2.1-4.684 2.924-7.138a60.114 60.114 0 00-16.536-1.84M7.5 14.25L5.106 5.272M6 20.25a.75.75 0 11-1.5 0 .75.75 0 011.5 0zm12.75 0a.75.75 0 11-1.5 0 .75.75 0 011.5 0z"/>
          </svg>
        </span>
        <h2 class="app-section-title text-2xl">Your cart is empty</h2>
        <p class="max-w-sm app-muted">Looks like you haven't added anything yet. Explore the collection and find something you love.</p>
        <a routerLink="/shop" class="btn-primary mt-2">Continue shopping</a>
      </div>
    } @else {
      <section class="grid gap-8 lg:grid-cols-[2fr_1fr] lg:items-start">

        <!-- ── Line items ──────────────────────────────────────────────────── -->
        <div class="space-y-4">
          @if (stockWarning()) {
            <p class="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-600 dark:border-red-800 dark:bg-red-950 dark:text-red-400">
              <svg class="h-4 w-4 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/></svg>
              {{ stockWarning() }}
            </p>
          }

          @for (item of store.cartItemsDetailed(); track item.product.id + item.size; let i = $index) {
            <article class="app-card flex gap-4 p-4 transition hover:shadow-md" [appReveal]="i">
              <a [routerLink]="['/product', item.product.id]"
                class="block h-32 w-28 flex-shrink-0 overflow-hidden rounded-xl bg-neutral-100 dark:bg-neutral-800">
                <img [src]="item.product.image" [alt]="item.product.name"
                  class="h-full w-full object-cover transition-transform duration-500 ease-out hover:scale-105" />
              </a>

              <div class="flex flex-1 flex-col">
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <a [routerLink]="['/product', item.product.id]"
                      class="font-medium underline-offset-4 hover:underline">{{ item.product.name }}</a>
                    <p class="mt-1 text-xs uppercase tracking-wider app-muted">Size: {{ item.customMeasurements ? 'Custom' : item.size }}</p>
                    @if (item.customMeasurements) {
                      <p class="mt-1 text-xs text-neutral-500 dark:text-neutral-400">
                        ✂ {{ customText(item.customMeasurements) }}
                        @if (item.customSurcharge) { <span class="text-neutral-400">(+{{ item.customSurcharge | currency:'BDT':'৳' }})</span> }
                      </p>
                    }
                  </div>
                  <p class="shrink-0 font-semibold">{{ item.subtotal | currency:'BDT':'৳' }}</p>
                </div>

                @if (item.quantity > 1) {
                  <p class="mt-1 text-sm app-muted">{{ (item.subtotal / item.quantity) | currency:'BDT':'৳' }} each</p>
                }

                <div class="mt-auto flex items-center justify-between pt-3">
                  <div class="inline-flex items-center rounded-full border border-neutral-300 dark:border-neutral-700">
                    <button (click)="decreaseQty(item.product.id, item.variantId, item.size, item.quantity)"
                      aria-label="Decrease quantity"
                      class="flex h-9 w-9 items-center justify-center rounded-l-full text-lg transition hover:bg-neutral-100 dark:hover:bg-neutral-800">−</button>
                    <span class="min-w-9 text-center text-sm font-medium tabular-nums">{{ item.quantity }}</span>
                    <button (click)="increaseQty(item.product.id, item.variantId, item.size, item.quantity)"
                      aria-label="Increase quantity"
                      class="flex h-9 w-9 items-center justify-center rounded-r-full text-lg transition hover:bg-neutral-100 dark:hover:bg-neutral-800">+</button>
                  </div>
                  <button (click)="removeItem(item.product.id, item.variantId, item.size)"
                    class="inline-flex items-center gap-1.5 text-sm text-neutral-500 transition hover:text-red-600 dark:text-neutral-400">
                    <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                    Remove
                  </button>
                </div>
              </div>
            </article>
          }

          <a routerLink="/shop" class="inline-flex items-center gap-1.5 pt-2 text-sm font-medium text-neutral-600 underline-offset-4 hover:underline dark:text-neutral-300">
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18"/></svg>
            Continue shopping
          </a>
        </div>

        <!-- ── Order summary ───────────────────────────────────────────────── -->
        <aside class="app-card h-fit p-6 lg:sticky lg:top-24" appReveal>
          <h2 class="mb-5 text-lg font-semibold">Order summary</h2>

          <div class="mb-5">
            <p class="mb-2 text-xs font-semibold uppercase tracking-[0.2em] text-neutral-500 dark:text-neutral-400">Promo code</p>
            <div class="flex gap-2">
              <input
                [ngModel]="codeInput()"
                (ngModelChange)="codeInput.set($event)"
                placeholder="e.g. WELCOME10"
                [disabled]="couponLocked()"
                class="w-full rounded-full border border-neutral-300 bg-white px-4 py-2.5 text-sm outline-none transition focus:border-neutral-500 disabled:opacity-50 dark:border-neutral-700 dark:bg-neutral-900"
              />
              @if (couponLocked()) {
                <button type="button" class="btn-secondary shrink-0 !rounded-full !px-4 !py-2 text-xs" (click)="removeCoupon()">Remove</button>
              } @else {
                <button type="button" class="btn-secondary shrink-0 !rounded-full !px-4 !py-2 text-xs"
                  (click)="applyCoupon()" [disabled]="applying() || !codeInput().trim()">
                  {{ applying() ? '…' : 'Apply' }}
                </button>
              }
            </div>
            @if (appliedCoupon(); as c) {
              @if (c.valid) {
                <p class="mt-2 flex items-center gap-1.5 text-xs font-medium text-emerald-600">
                  <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/></svg>
                  Coupon <strong>{{ c.code }}</strong> applied@if (c.removesShipping) { · free shipping }
                </p>
              } @else if (c.reason) {
                <p class="mt-2 text-xs text-red-500">{{ c.reason }}</p>
              }
            }
          </div>

          <div class="space-y-2.5 text-sm">
            <div class="flex justify-between"><span class="app-muted">Subtotal</span><span class="tabular-nums">{{ store.cartSubtotal() | currency:'BDT':'৳' }}</span></div>
            <div class="flex justify-between"><span class="app-muted">Shipping</span><span class="tabular-nums">{{ effectiveShipping() | currency:'BDT':'৳' }}</span></div>
            @if (effectiveDiscount() > 0) {
              <div class="flex justify-between text-emerald-600"><span>Discount</span><span class="tabular-nums">−{{ effectiveDiscount() | currency:'BDT':'৳' }}</span></div>
            }
          </div>
          <div class="my-4 border-t border-neutral-200 dark:border-neutral-800"></div>
          <div class="flex items-baseline justify-between">
            <span class="font-semibold">Estimated total</span>
            <span class="text-xl font-semibold tabular-nums">{{ estimatedTotal() | currency:'BDT':'৳' }}</span>
          </div>
          <p class="mt-1.5 text-xs app-muted">Final shipping is calculated at checkout based on your delivery address.</p>

          @if (auth.isAuthenticated()) {
            <a routerLink="/checkout" class="btn-primary mt-5 w-full !rounded-full">Continue to checkout</a>
          } @else {
            <a routerLink="/account" [queryParams]="{ redirect: '/checkout' }" class="btn-primary mt-5 w-full !rounded-full">Sign in to checkout</a>
          }

          <div class="mt-4 flex items-center justify-center gap-1.5 text-xs app-muted">
            <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z"/></svg>
            Secure checkout · encrypted payments
          </div>
        </aside>
      </section>
    }
  `
})
export class CartPage implements OnInit {
  protected readonly store = inject(EcommerceStore);
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly loading      = signal(false);
  protected readonly stockWarning = signal('');
  protected readonly codeInput    = signal('');
  protected readonly applying     = signal(false);
  protected readonly appliedCoupon = signal<CouponValidateResponse | null>(null);

  protected readonly couponLocked = computed(() => !!this.appliedCoupon()?.valid);

  /** Distinct line items in the cart. */
  protected readonly itemCount = computed(() => this.store.cartItemsDetailed().length);

  /** Render the custom-measurements JSON as readable text for the cart line. */
  protected customText(json: string | null | undefined): string {
    if (!json) return '';
    try {
      const data = JSON.parse(json) as Record<string, unknown>;
      return Object.entries(data)
        .filter(([, v]) => v != null && v !== '')
        .map(([k, v]) => `${k === 'comments' ? 'Notes' : k}: ${v}`)
        .join(' · ');
    } catch {
      return '';
    }
  }

  protected readonly effectiveDiscount = computed(() => {
    const c = this.appliedCoupon();
    return c?.valid ? c.discountAmount : 0;
  });

  protected readonly effectiveShipping = computed(() => {
    const c = this.appliedCoupon();
    if (c?.valid && c.removesShipping) return 0;
    return this.store.shippingFee();
  });

  protected readonly estimatedTotal = computed(() =>
    Math.max(0, this.store.cartSubtotal() - this.effectiveDiscount() + this.effectiveShipping())
  );

  // map productId+'|'+variantId → API cart item id (needed for PATCH/DELETE)
  private readonly apiCartMap = signal<Map<string, string>>(new Map());

  private cartItemId(productId: string, variantId: number | null): string | undefined {
    return this.apiCartMap().get(productId + '|' + (variantId ?? ''));
  }

  ngOnInit(): void {
    // Rehydrate previously-applied promo code so the customer doesn't lose state on refresh.
    const saved = this.store.promoCode();
    if (saved) {
      this.codeInput.set(saved);
      this.applyCoupon();
    }
    const userId = this.auth.userId();
    if (userId) {
      this.loadApiCart(userId);
    }
  }

  protected applyCoupon(): void {
    const code = this.codeInput().trim();
    if (!code) return;
    this.applying.set(true);
    this.api.validateCoupon({
      code,
      cartSubtotal: this.store.cartSubtotal(),
      userId: this.auth.userId() ?? undefined,
    }).pipe(catchError((err) => {
      this.applying.set(false);
      this.appliedCoupon.set({
        valid: false, code, discountType: null, discountAmount: 0,
        removesShipping: false, reason: err?.error?.message ?? 'Could not validate coupon.'
      });
      return of(null);
    })).subscribe((result) => {
      this.applying.set(false);
      if (!result) return;
      this.appliedCoupon.set(result);
      if (result.valid) {
        this.store.setPromoCode(code);
      }
    });
  }

  protected removeCoupon(): void {
    this.appliedCoupon.set(null);
    this.codeInput.set('');
    this.store.clearPromoCode();
  }

  private loadApiCart(userId: string): void {
    this.loading.set(true);
    this.api.getCart(userId).pipe(catchError(() => of([]))).subscribe((items) => {
      this.store.loadApiCart(items);
      const map = new Map<string, string>();
      items.forEach((i) => map.set(String(i.productId) + '|' + (i.variantId ?? ''), i.cartItemId));
      this.apiCartMap.set(map);
      this.loading.set(false);
      this.validateStock(userId);
    });
  }

  private validateStock(userId: string): void {
    this.api.validateCartStock(userId).pipe(catchError(() => of(null))).subscribe((result) => {
      if (result && !result.valid) {
        this.stockWarning.set('Some items in your cart are out of stock or have insufficient quantity.');
      }
    });
  }

  protected increaseQty(productId: string, variantId: number | null, size: string, current: number): void {
    this.store.updateCartQuantity(productId, size, current + 1);
    const userId = this.auth.userId();
    const id = this.cartItemId(productId, variantId);
    if (userId && id) {
      this.api.incrementCartItem(userId, id).pipe(catchError(() => of(null))).subscribe();
    }
  }

  protected decreaseQty(productId: string, variantId: number | null, size: string, current: number): void {
    if (current <= 1) {
      this.removeItem(productId, variantId, size);
      return;
    }
    this.store.updateCartQuantity(productId, size, current - 1);
    const userId = this.auth.userId();
    const id = this.cartItemId(productId, variantId);
    if (userId && id) {
      this.api.decrementCartItem(userId, id).pipe(catchError(() => of(null))).subscribe();
    }
  }

  protected removeItem(productId: string, variantId: number | null, size: string): void {
    this.store.removeFromCart(productId, size);
    const userId = this.auth.userId();
    const id = this.cartItemId(productId, variantId);
    if (userId && id) {
      this.api.removeCartItem(userId, id).pipe(catchError(() => of(null))).subscribe();
    }
  }
}
