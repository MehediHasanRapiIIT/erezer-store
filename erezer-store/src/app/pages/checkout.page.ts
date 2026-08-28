import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { EcommerceStore } from '../core/store/ecommerce.store';
import { SettingsStore } from '../core/store/settings.store';
import { BundleCheckoutStore } from '../core/store/bundle-checkout.store';
import { AuthService } from '../core/auth.service';
import { ApiService } from '../core/api.service';
import { PixelService } from '../core/pixel.service';
import {
  ApiAddress,
  CheckoutQuoteResponse,
  ShippingZone,
} from '../core/api.models';
import { RevealDirective } from '../core/reveal.directive';

type PaymentMethod = 'CASH' | 'BKASH' | 'CARD';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, CurrencyPipe, RouterLink, RevealDirective],
  template: `
    <div class="mb-8" appReveal>
      <p class="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-500 dark:text-neutral-400">Secure checkout</p>
      <h1 class="app-section-title mt-2 text-3xl md:text-4xl">Checkout</h1>
    </div>

    <section class="grid gap-8 lg:grid-cols-[2fr_1fr] lg:items-start">

      <form [formGroup]="checkoutForm" (ngSubmit)="placeOrder()" class="space-y-6" novalidate>

        <!-- Account banners -->
        @if (!auth.isAuthenticated()) {
          <div class="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-300">
            You are checking out as a guest. <a routerLink="/account" class="underline underline-offset-4">Sign in</a> to save your order history.
          </div>
        } @else if (!auth.emailVerified()) {
          <div class="space-y-2 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-300">
            <p class="font-medium">Verify your email to place an order</p>
            <p>We sent a verification link to <strong>{{ auth.email() }}</strong>. Please confirm your address before checking out.</p>
            <div class="flex items-center gap-3 pt-1">
              <button type="button" (click)="resendVerification()" [disabled]="resending()"
                class="rounded-full border border-amber-400 px-3 py-1.5 text-xs font-medium hover:bg-amber-100 disabled:opacity-50 dark:hover:bg-amber-900">
                {{ resending() ? 'Sending…' : 'Resend verification email' }}
              </button>
              @if (resendMessage()) { <span class="text-xs text-green-700 dark:text-green-400">{{ resendMessage() }}</span> }
            </div>
          </div>
        }

        <!-- 1 · Contact -->
        <div class="app-card space-y-4 p-6" appReveal>
          <div class="flex items-center gap-2">
            <span class="step">1</span>
            <h2 class="text-base font-semibold">Contact details</h2>
          </div>
          <div class="grid gap-4 md:grid-cols-2">
            <div>
              <div class="relative">
                <svg class="field-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.5 20.25a7.5 7.5 0 0115 0v.5H4.5v-.5z"/></svg>
                <input formControlName="firstName" placeholder="First name" autocomplete="given-name"
                  class="field !pl-10" [class.field-error]="invalidCtrl('firstName')" />
              </div>
              @if (invalidCtrl('firstName')) { <p class="err">First name is required.</p> }
            </div>
            <div>
              <div class="relative">
                <svg class="field-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.5 20.25a7.5 7.5 0 0115 0v.5H4.5v-.5z"/></svg>
                <input formControlName="lastName" placeholder="Last name" autocomplete="family-name"
                  class="field !pl-10" [class.field-error]="invalidCtrl('lastName')" />
              </div>
              @if (invalidCtrl('lastName')) { <p class="err">Last name is required.</p> }
            </div>
            <div>
              <div class="relative">
                <svg class="field-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75"/></svg>
                <input formControlName="email" type="email" placeholder="Email address" autocomplete="email"
                  class="field !pl-10" [class.field-error]="invalidCtrl('email')" />
              </div>
              @if (invalidCtrl('email')) {
                <p class="err">{{ checkoutForm.get('email')?.errors?.['required'] ? 'Email is required.' : 'Enter a valid email address.' }}</p>
              }
            </div>
            <div>
              <div class="relative">
                <svg class="field-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M2.25 6.75c0 8.284 6.716 15 15 15h2.25a2.25 2.25 0 002.25-2.25v-1.372c0-.516-.351-.966-.852-1.091l-4.423-1.106c-.44-.11-.902.055-1.173.417l-.97 1.293c-.282.376-.769.542-1.21.38a12.035 12.035 0 01-7.143-7.143c-.162-.441.004-.928.38-1.21l1.293-.97c.363-.271.527-.734.417-1.173L6.963 3.102a1.125 1.125 0 00-1.091-.852H4.5A2.25 2.25 0 002.25 4.5v2.25z"/></svg>
                <input formControlName="phone" type="tel" placeholder="Phone number" autocomplete="tel"
                  class="field !pl-10" [class.field-error]="invalidCtrl('phone')" />
              </div>
              @if (invalidCtrl('phone')) {
                <p class="err">{{ checkoutForm.get('phone')?.errors?.['required'] ? 'Phone number is required.' : 'Enter a valid phone number.' }}</p>
              }
            </div>
          </div>
        </div>

        <!-- 2 · Delivery -->
        <div class="app-card space-y-4 p-6" appReveal>
          <div class="flex items-center gap-2">
            <span class="step">2</span>
            <h2 class="text-base font-semibold">Delivery address</h2>
          </div>

          @if (savedAddresses().length > 0) {
            <div class="grid gap-2 sm:grid-cols-2">
              @for (addr of savedAddresses(); track addr.id) {
                <button type="button" (click)="selectAddress(addr)"
                  class="relative rounded-2xl border p-3 text-left text-sm transition"
                  [class.border-neutral-900]="selectedAddressId() === addr.id"
                  [class.ring-1]="selectedAddressId() === addr.id"
                  [class.ring-neutral-900]="selectedAddressId() === addr.id"
                  [class.dark:border-white]="selectedAddressId() === addr.id"
                  [class.border-neutral-200]="selectedAddressId() !== addr.id"
                  [class.dark:border-neutral-700]="selectedAddressId() !== addr.id">
                  @if (selectedAddressId() === addr.id) {
                    <span class="absolute right-2 top-2 inline-flex h-5 w-5 items-center justify-center rounded-full bg-neutral-900 text-white dark:bg-white dark:text-black">
                      <svg class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/></svg>
                    </span>
                  }
                  <span class="block font-medium">{{ addr.name }}
                    <span class="ml-1 rounded bg-neutral-100 px-1.5 py-0.5 text-[10px] uppercase text-neutral-500 dark:bg-neutral-800">{{ addr.addressType }}</span>
                  </span>
                  <span class="mt-1 block text-neutral-600 dark:text-neutral-300">{{ addr.address }}</span>
                </button>
              }
              <button type="button" (click)="useNewAddress()"
                class="rounded-2xl border border-dashed p-3 text-left text-sm transition hover:border-neutral-400"
                [class.border-neutral-900]="selectedAddressId() === null"
                [class.dark:border-white]="selectedAddressId() === null"
                [class.border-neutral-300]="selectedAddressId() !== null"
                [class.dark:border-neutral-600]="selectedAddressId() !== null">
                + Use a new address
              </button>
            </div>
          }

          <div>
            <div class="relative">
              <svg class="field-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M15 10.5a3 3 0 11-6 0 3 3 0 016 0z"/><path stroke-linecap="round" stroke-linejoin="round" d="M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1115 0z"/></svg>
              <input formControlName="address" placeholder="Delivery address"
                class="field !pl-10" [class.field-error]="invalidCtrl('address')" />
            </div>
            @if (invalidCtrl('address')) { <p class="err">Delivery address is required.</p> }
          </div>

          <!-- Shipping zone -->
          <div class="space-y-2">
            <p class="text-xs font-semibold uppercase tracking-wider text-neutral-500 dark:text-neutral-400">Shipping zone</p>
            <div class="flex flex-wrap gap-2">
              @for (zone of shippingZones(); track zone.id) {
                <button type="button" (click)="selectZone(zone.id)"
                  class="rounded-full border px-3.5 py-2 text-sm transition active:scale-95"
                  [class.border-neutral-900]="selectedZoneId() === zone.id"
                  [class.bg-neutral-900]="selectedZoneId() === zone.id"
                  [class.text-white]="selectedZoneId() === zone.id"
                  [class.dark:border-white]="selectedZoneId() === zone.id"
                  [class.dark:bg-white]="selectedZoneId() === zone.id"
                  [class.dark:text-black]="selectedZoneId() === zone.id"
                  [class.border-neutral-300]="selectedZoneId() !== zone.id"
                  [class.dark:border-neutral-700]="selectedZoneId() !== zone.id">
                  {{ zone.displayName }}
                  <span class="ml-1 text-xs opacity-70">{{ zone.flatFee | currency:'BDT':'৳' }}</span>
                </button>
              }
            </div>
          </div>
        </div>

        @if (errorMessage()) {
          <p class="rounded-2xl border border-red-200 bg-red-50 p-3 text-sm text-red-600 dark:border-red-800 dark:bg-red-950 dark:text-red-400">
            {{ errorMessage() }}
          </p>
        }

        @if (confirmation()) {
          <p class="rounded-2xl border border-green-200 bg-green-50 p-3 text-sm text-green-700 dark:border-green-800 dark:bg-green-950 dark:text-green-400">
            Order placed! Confirmation: <strong>{{ confirmation() }}</strong>
          </p>
        }
      </form>

      <!-- ── Right column ───────────────────────────────────────────────── -->
      <aside class="space-y-4 lg:sticky lg:top-24" appReveal>

        <!-- Payment -->
        <div class="app-card p-5">
          <h2 class="mb-3 text-base font-semibold">Payment method</h2>
          <div class="space-y-2">
            @for (method of paymentMethods(); track method) {
              <button type="button" (click)="paymentMethod.set(method)"
                class="flex w-full items-center gap-3 rounded-2xl border p-3 text-left text-sm transition"
                [class.border-neutral-900]="paymentMethod() === method"
                [class.ring-1]="paymentMethod() === method"
                [class.ring-neutral-900]="paymentMethod() === method"
                [class.dark:border-white]="paymentMethod() === method"
                [class.border-neutral-200]="paymentMethod() !== method"
                [class.dark:border-neutral-700]="paymentMethod() !== method">
                <span class="inline-flex h-9 w-9 items-center justify-center rounded-full bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-200">
                  @switch (method) {
                    @case ('CASH') { <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M2.25 8.25h19.5M2.25 9h19.5m-16.5 5.25h6m-6 2.25h3m-3.75 3h15a2.25 2.25 0 002.25-2.25V6.75A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25v10.5A2.25 2.25 0 004.5 19.5z"/></svg> }
                    @case ('BKASH') { <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M10.5 1.5H8.25A2.25 2.25 0 006 3.75v16.5a2.25 2.25 0 002.25 2.25h7.5A2.25 2.25 0 0018 20.25V3.75a2.25 2.25 0 00-2.25-2.25H13.5m-3 0V3h3V1.5m-3 0h3m-3 18.75h3"/></svg> }
                    @default { <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M2.25 8.25h19.5M2.25 9h19.5m-16.5 5.25h6m-6 2.25h3m-3.75 3h15a2.25 2.25 0 002.25-2.25V6.75A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25v10.5A2.25 2.25 0 004.5 19.5z"/></svg> }
                  }
                </span>
                <span class="flex-1 font-medium">{{ paymentLabel(method) }}</span>
                <span class="inline-flex h-5 w-5 items-center justify-center rounded-full border transition"
                  [class.border-neutral-900]="paymentMethod() === method"
                  [class.bg-neutral-900]="paymentMethod() === method"
                  [class.dark:border-white]="paymentMethod() === method"
                  [class.dark:bg-white]="paymentMethod() === method"
                  [class.border-neutral-300]="paymentMethod() !== method"
                  [class.dark:border-neutral-600]="paymentMethod() !== method">
                  @if (paymentMethod() === method) {
                    <svg class="h-3 w-3 text-white dark:text-black" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/></svg>
                  }
                </span>
              </button>
            }
          </div>
        </div>

        <!-- Order summary -->
        <div class="app-card p-5">
          <h2 class="mb-3 text-base font-semibold">Order summary</h2>

          @if (bundleMode()) {
            <p class="mb-2 rounded-lg bg-amber-100 px-3 py-1.5 text-xs font-medium text-amber-800 dark:bg-amber-950/40 dark:text-amber-300">
              Bundle: {{ bundleCheckout.pending()?.bundleName }}
            </p>
          }
          <div class="mb-3 space-y-2">
            @for (item of effItems(); track $index) {
              <div class="flex justify-between gap-3 text-sm">
                <span class="min-w-0 truncate text-neutral-600 dark:text-neutral-300">{{ item.product.name }} × {{ item.quantity }}</span>
                <span class="shrink-0 tabular-nums">{{ item.subtotal | currency:'BDT':'৳' }}</span>
              </div>
            }
          </div>

          <div class="space-y-2 border-t border-neutral-200 pt-3 text-sm dark:border-neutral-800">
            <div class="flex justify-between"><span class="app-muted">Subtotal</span><span class="tabular-nums">{{ summarySubtotal() | currency:'BDT':'৳' }}</span></div>
            <div class="flex justify-between">
              <span class="app-muted">Shipping@if (quote(); as q) { @if (q.shippingZoneName) { · {{ q.shippingZoneName }} } }</span>
              <span class="tabular-nums">{{ summaryShipping() | currency:'BDT':'৳' }}</span>
            </div>
            @if (summaryTax() > 0) {
              <div class="flex justify-between"><span class="app-muted">Tax</span><span class="tabular-nums">{{ summaryTax() | currency:'BDT':'৳' }}</span></div>
            }
            @if (summaryDiscount() > 0) {
              <div class="flex justify-between text-emerald-600">
                <span>Discount@if (quote(); as q) { @if (q.couponCode) { · {{ q.couponCode }} } }</span>
                <span class="tabular-nums">−{{ summaryDiscount() | currency:'BDT':'৳' }}</span>
              </div>
            }
            @if (summarySurcharge() > 0) {
              <div class="flex justify-between">
                <span class="app-muted">Custom tailoring</span>
                <span class="tabular-nums">+{{ summarySurcharge() | currency:'BDT':'৳' }}</span>
              </div>
            }
          </div>
          <div class="my-3 border-t border-neutral-200 dark:border-neutral-800"></div>
          <div class="flex items-baseline justify-between">
            <span class="font-semibold">Total</span>
            <span class="text-xl font-semibold tabular-nums">{{ summaryTotal() | currency:'BDT':'৳' }}</span>
          </div>
          @if (quote()?.couponMessage && !quote()?.couponApplied) {
            <p class="mt-2 text-xs text-amber-600">{{ quote()?.couponMessage }}</p>
          }

          <button type="button" (click)="placeOrder()"
            class="btn-primary mt-4 w-full !rounded-full"
            [disabled]="effCount() === 0 || submitting() || !auth.emailVerified()">
            {{ submitting() ? 'Placing order…' : (!auth.emailVerified() ? 'Verify your email to order' : payButtonLabel()) }}
          </button>

          <div class="mt-3 flex items-center justify-center gap-1.5 text-xs app-muted">
            <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z"/></svg>
            Encrypted &amp; secure checkout
          </div>
        </div>
      </aside>
    </section>
  `,
  styles: [`
    .step {
      display: inline-flex; align-items: center; justify-content: center;
      height: 1.5rem; width: 1.5rem; border-radius: 9999px;
      background: rgb(23 23 23); color: white; font-size: 0.75rem; font-weight: 600;
    }
    :host-context(.dark) .step { background: white; color: black; }
    .field {
      width: 100%; border-radius: 0.75rem;
      border: 1px solid rgb(212 212 212); background: white;
      padding: 0.6rem 0.9rem; font-size: 0.875rem; color: rgb(23 23 23);
      outline: none; transition: border-color .2s ease, box-shadow .2s ease;
    }
    .field::placeholder { color: rgb(163 163 163); }
    .field:focus { border-color: rgb(82 82 82); box-shadow: 0 0 0 3px rgba(0,0,0,.06); }
    :host-context(.dark) .field { border-color: rgb(64 64 64); background: rgb(23 23 23); color: rgb(229 229 229); }
    :host-context(.dark) .field:focus { border-color: rgb(163 163 163); box-shadow: 0 0 0 3px rgba(255,255,255,.10); }
    .field-error, .field-error:focus {
      border-color: rgb(248 113 113) !important;
      box-shadow: 0 0 0 3px rgba(248,113,113,.14) !important;
    }
    .field-icon {
      position: absolute; left: 0.85rem; top: 50%; transform: translateY(-50%);
      height: 1.05rem; width: 1.05rem; color: rgb(163 163 163); pointer-events: none;
    }
    .err { margin-top: 0.3rem; font-size: 0.75rem; color: rgb(239 68 68); }
  `]
})
export class CheckoutPage implements OnInit, OnDestroy {
  private readonly fb     = inject(FormBuilder);
  protected readonly store = inject(EcommerceStore);
  private readonly settingsStore = inject(SettingsStore);
  protected readonly bundleCheckout = inject(BundleCheckoutStore);
  protected readonly auth  = inject(AuthService);
  private readonly api    = inject(ApiService);
  private readonly router = inject(Router);
  private readonly pixel  = inject(PixelService);

  /** True when completing a Buy-X-Get-Y bundle (items come from the bundle, not the cart). */
  protected readonly bundleMode = computed(() => !!this.bundleCheckout.pending());
  /** The items being checked out — bundle lines in bundle mode, else the cart. */
  protected readonly effItems = computed(() =>
    this.bundleMode() ? this.bundleCheckout.detailedItems() : this.store.cartItemsDetailed());
  protected readonly effCount = computed(() => this.effItems().length);

  protected readonly submitting    = signal(false);
  protected readonly confirmation  = signal('');
  protected readonly errorMessage  = signal('');
  protected readonly paymentMethod = signal<PaymentMethod>('CASH');
  protected readonly resending     = signal(false);
  protected readonly resendMessage = signal('');
  protected readonly savedAddresses   = signal<ApiAddress[]>([]);
  protected readonly selectedAddressId = signal<number | null>(null);
  private selectedLat: number | null = null;
  private selectedLng: number | null = null;

  /** Payment methods the admin has enabled (in store settings). Null flags (settings
   *  not yet loaded) are treated as enabled so nothing flickers off on first paint. */
  protected readonly paymentMethods = computed<PaymentMethod[]>(() => {
    const s = this.settingsStore.settings();
    const methods: PaymentMethod[] = [];
    if (!s || s.paymentCodEnabled !== false) methods.push('CASH');
    if (!s || s.paymentBkashEnabled !== false) methods.push('BKASH');
    if (!s || s.paymentCardEnabled !== false) methods.push('CARD');
    return methods.length ? methods : ['CASH'];
  });

  protected readonly shippingZones   = signal<ShippingZone[]>([]);
  protected readonly selectedZoneId  = signal<number | null>(null);
  protected readonly quote           = signal<CheckoutQuoteResponse | null>(null);

  private readonly effSubtotal = computed(() => this.effItems().reduce((acc, i) => acc + i.subtotal, 0));
  protected readonly summarySubtotal = computed(() => this.quote()?.subtotal ?? this.effSubtotal());
  protected readonly summaryShipping = computed(() => this.quote()?.shippingFee ?? (this.bundleMode() ? 0 : this.store.shippingFee()));
  protected readonly summaryTax      = computed(() => this.quote()?.taxAmount ?? 0);
  protected readonly summaryDiscount = computed(() =>
    this.quote()?.discountAmount
    ?? (this.bundleMode()
        ? Math.max(0, this.effSubtotal() - (this.bundleCheckout.pending()?.bundlePrice ?? 0))
        : this.store.discountAmount()));
  /** Flat custom-size surcharge total — from the server quote, else summed from the local cart (once per line). */
  protected readonly summarySurcharge = computed(() =>
    this.quote()?.customSurcharge
    ?? (this.bundleMode() ? 0
        : this.store.cartItemsDetailed().reduce((acc, i) => acc + (i.customMeasurements ? (i.customSurcharge ?? 0) : 0), 0)));
  protected readonly summaryTotal    = computed(() =>
    this.quote()?.total
    ?? (this.bundleMode()
        ? (this.bundleCheckout.pending()?.bundlePrice ?? 0)
        : this.store.cartTotal() + this.summarySurcharge()));

  protected readonly payButtonLabel = computed(() => {
    if (this.paymentMethod() === 'BKASH') return 'Pay with bKash';
    return 'Place order';
  });

  /** Flipped true on a submit attempt so all field errors surface at once. */
  protected readonly submitted = signal(false);

  protected readonly checkoutForm = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName:  ['', Validators.required],
    email:     ['', [Validators.required, Validators.email]],
    phone:     ['', [Validators.required, Validators.pattern(/^[0-9+\-\s]{7,15}$/)]],
    address:   ['', Validators.required],
  });

  /** True once a control is invalid AND touched/dirty or a submit was attempted. */
  protected invalidCtrl(name: string): boolean {
    const c = this.checkoutForm.get(name);
    return !!c && c.invalid && (c.touched || c.dirty || this.submitted());
  }

  constructor() {
    // Re-fetch the quote whenever the inputs that affect it change.
    effect(() => {
      const items = this.effItems();
      const zoneId = this.selectedZoneId();
      const address = this.checkoutForm.controls.address.value;
      const code = this.bundleMode() ? null : this.store.promoCode();
      if (items.length === 0) {
        this.quote.set(null);
        return;
      }
      this.fetchQuote(zoneId, address, code);
    });

    // Keep the selected method valid when the admin-configured list changes
    // (e.g. the default CASH is disabled): fall back to the first enabled one.
    effect(() => {
      const methods = this.paymentMethods();
      if (!methods.includes(this.paymentMethod())) {
        this.paymentMethod.set(methods[0]);
      }
    });
  }

  ngOnInit(): void {
    // Checkout requires an account — send guests to sign in, then back here.
    if (!this.auth.isAuthenticated()) {
      void this.router.navigate(['/account'], { queryParams: { redirect: '/checkout' } });
      return;
    }

    // While the page is open, quietly re-check verification so the customer
    // doesn't have to refresh after confirming their email on another device.
    this.startVerificationPolling();

    // Load shipping zones first so the picker is populated.
    this.api.getShippingZones().pipe(catchError(() => of([] as ShippingZone[])))
      .subscribe((zones) => {
        this.shippingZones.set(zones);
        if (this.selectedZoneId() == null) {
          const def = zones.find((z) => z.isDefault) ?? zones[0];
          if (def) this.selectedZoneId.set(def.id);
        }
      });

    // pre-fill from profile if authenticated
    const userId = this.auth.userId();
    if (userId) {
      this.api.getProfile(userId).pipe(catchError(() => of(null))).subscribe((p) => {
        if (!p) return;
        this.checkoutForm.patchValue({
          firstName: p.firstName,
          lastName:  p.lastName,
          email:     p.email,
          phone:     p.phoneNumber,
        });
        this.savedAddresses.set(p.addresses ?? []);
        // Default to the first saved address, if any.
        const first = p.addresses?.[0];
        if (first) {
          this.selectAddress(first);
        }
      });
    }
  }

  // ── email-verification polling ──────────────────────────────────────────────
  private verifyPoll?: ReturnType<typeof setInterval>;
  /** Stop after ~10 min of polling so an abandoned tab doesn't poll forever. */
  private verifyPollsLeft = 100;

  /**
   * Polls a fresh token every 6s (only while unverified and the tab is visible)
   * so the "verify your email" gate lifts automatically once the customer
   * confirms — even if they verified on a different device. Stops on verify,
   * after the attempt cap, or on destroy.
   */
  private startVerificationPolling(): void {
    if (typeof window === 'undefined') return;
    if (this.auth.emailVerified()) return;

    this.verifyPoll = setInterval(() => {
      if (this.auth.emailVerified() || this.verifyPollsLeft-- <= 0) {
        this.stopVerificationPolling();
        return;
      }
      if (document.visibilityState === 'hidden') return; // skip while backgrounded
      if (!this.auth.refreshToken()) return;
      this.auth.refresh()
        .then(() => { if (this.auth.emailVerified()) this.stopVerificationPolling(); })
        .catch(() => { /* keep polling; transient */ });
    }, 6000);
  }

  private stopVerificationPolling(): void {
    if (this.verifyPoll) {
      clearInterval(this.verifyPoll);
      this.verifyPoll = undefined;
    }
  }

  ngOnDestroy(): void {
    this.stopVerificationPolling();
  }

  protected selectZone(zoneId: number): void {
    this.selectedZoneId.set(zoneId);
  }

  protected selectAddress(addr: ApiAddress): void {
    this.selectedAddressId.set(addr.id);
    this.checkoutForm.patchValue({ address: addr.address });
    this.selectedLat = addr.latitude ?? null;
    this.selectedLng = addr.longitude ?? null;
  }

  protected useNewAddress(): void {
    this.selectedAddressId.set(null);
    this.checkoutForm.patchValue({ address: '' });
    this.selectedLat = null;
    this.selectedLng = null;
  }

  protected resendVerification(): void {
    const email = this.auth.email();
    if (!email) return;
    this.resending.set(true);
    this.resendMessage.set('');
    this.auth.resendVerification(email)
      .then(() => this.resendMessage.set('Verification email sent — check your inbox.'))
      .catch(() => this.resendMessage.set(''))
      .finally(() => this.resending.set(false));
  }

  protected paymentLabel(m: PaymentMethod): string {
    return m === 'CASH' ? 'Cash on Delivery'
         : m === 'BKASH' ? 'bKash'
         : 'Card';
  }

  private fetchQuote(zoneId: number | null, address: string | null, code: string | null): void {
    const items = this.effItems();
    if (items.length === 0) return;
    this.api.getCheckoutQuote({
      items: items.map((i) => ({
        productId: Number(i.product.id),
        quantity:  i.quantity,
        variantId: i.variantId,
        customMeasurements: i.customMeasurements ?? undefined,
      })),
      shippingZoneId: zoneId ?? undefined,
      deliveryAddress: address ?? undefined,
      couponCode: this.bundleMode() ? undefined : (code || undefined),
      userId: this.auth.userId() ?? undefined,
      bundleId: this.bundleCheckout.pending()?.bundleId,
    }).pipe(catchError(() => of(null))).subscribe((q) => {
      if (q) this.quote.set(q);
    });
  }

  protected placeOrder(): void {
    this.submitted.set(true);
    if (this.checkoutForm.invalid) {
      this.checkoutForm.markAllAsTouched();
      return;
    }
    if (this.effCount() === 0) return;

    // Server also enforces this, but block early for a clearer message.
    if (!this.auth.emailVerified()) {
      this.errorMessage.set('Please verify your email address before placing an order.');
      return;
    }

    const userId = this.auth.userId();
    const form   = this.checkoutForm.getRawValue();
    const items  = this.effItems();

    // Checkout requires an account (the page guard also redirects guests).
    if (!userId) {
      void this.router.navigate(['/account'], { queryParams: { redirect: '/checkout' } });
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set('');

    const payload = {
      deliveryAddress: form.address,
      phone:           form.phone,
      paymentMethod:   this.paymentMethod(),
      shopId:          1,
      deliveryCharge:  this.summaryShipping(),
      latitude:        this.selectedLat,
      longitude:       this.selectedLng,
      items:           items.map((i) => ({
        productId: Number(i.product.id),
        quantity:  i.quantity,
        variantId: i.variantId,
        customMeasurements: i.customMeasurements ?? undefined,
      })),
      couponCode:      this.bundleMode() ? undefined : (this.store.promoCode() || undefined),
      shippingZoneId:  this.selectedZoneId() ?? undefined,
      bundleId:        this.bundleCheckout.pending()?.bundleId,
    };

    this.api.createOrder(userId, payload)
      .pipe(catchError((err) => {
        this.errorMessage.set(err?.error?.message ?? 'Failed to place order. Please try again.');
        this.submitting.set(false);
        return of(null);
      }))
      .subscribe((order) => {
        if (!order) return;

        // Clear the source after the order is persisted — the bundle in bundle
        // mode (leaving the real cart untouched), otherwise the cart.
        this.pixel.purchase(order.id, this.summaryTotal());
        if (this.bundleMode()) {
          this.bundleCheckout.clear();
        } else {
          this.store.cart.set([]);
          this.store.clearPromoCode();
        }
        this.confirmation.set(order.id);

        if (this.paymentMethod() === 'BKASH') {
          // Initiate bKash payment, then redirect to the gateway's URL.
          this.api.bkashInit(order.id)
            .pipe(catchError((err) => {
              this.errorMessage.set(err?.error?.message ?? 'Could not start bKash payment.');
              this.submitting.set(false);
              return of(null);
            }))
            .subscribe((init) => {
              this.submitting.set(false);
              if (init?.bkashURL) {
                window.location.href = init.bkashURL;
              } else {
                this.router.navigateByUrl(`/orders/${order.id}`);
              }
            });
        } else {
          this.submitting.set(false);
          setTimeout(() => void this.router.navigateByUrl(`/orders/${order.id}`), 900);
        }
      });
  }
}
