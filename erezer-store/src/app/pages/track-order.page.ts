import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { PublicOrderTracking } from '../core/api.models';
import { TranslatePipe } from '../core/i18n/translate.pipe';

/** Progress order shown on the timeline, the same as a signed-in customer's order page. */
const STEPS: { status: string; label: string }[] = [
  { status: 'PLACED', label: 'Placed' },
  { status: 'ACCEPTED', label: 'Accepted' },
  { status: 'IN_PRODUCTION', label: 'In production' },
  { status: 'PROCESSING', label: 'Processing' },
  { status: 'SHIPPED', label: 'Shipped' },
  { status: 'OUT_FOR_DELIVERY', label: 'Out for delivery' },
  { status: 'DELIVERED', label: 'Delivered' },
];

/**
 * Track Order: anyone with an order number sees its progress and items.
 * The server sends nothing about the person, so there is nothing to hide here.
 */
@Component({
  standalone: true,
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, TranslatePipe],
  template: `
    <section class="mx-auto max-w-5xl px-1 py-10">
      <div class="flex flex-col gap-6 md:flex-row md:items-end md:justify-between">
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.2em] text-emerald-600">● {{ 'track.eyebrow' | t }}</p>
          <h1 class="app-section-title mt-2">{{ 'track.title' | t }}</h1>
          <p class="app-muted mt-1 text-sm">{{ 'track.subtitle' | t }}</p>
        </div>
        <form (ngSubmit)="search()" class="flex w-full gap-2 md:w-auto" role="search">
          <input [(ngModel)]="typed" name="number" [attr.aria-label]="'track.placeholder' | t"
            [placeholder]="'track.placeholder' | t" autocomplete="off" autocapitalize="characters"
            class="min-w-0 flex-1 rounded-xl border border-neutral-300 bg-white px-4 py-2.5 text-sm uppercase placeholder:normal-case dark:border-neutral-700 dark:bg-neutral-900 md:w-64" />
          <button type="submit" class="btn-primary shrink-0 !rounded-xl" [disabled]="loading() || !typed.trim()">
            {{ loading() ? '…' : ('track.search' | t) }}
          </button>
        </form>
      </div>

      <div class="mt-10">
        @if (state() === 'not-found') {
          <div class="app-card mx-auto max-w-lg px-8 py-12 text-center">
            <div class="text-4xl" aria-hidden="true">📦</div>
            <h2 class="mt-4 text-xl font-semibold">{{ 'track.not_found_title' | t }}</h2>
            <p class="app-muted mt-2 text-sm">{{ 'track.not_found_text' | t }}</p>
            <a routerLink="/shop" class="btn-primary mt-6 inline-flex !rounded-full">{{ 'track.back_to_shopping' | t }}</a>
          </div>
        } @else if (state() === 'error') {
          <p class="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-300">{{ error() }}</p>
        } @else if (order(); as o) {
          <div class="grid gap-6 lg:grid-cols-[3fr_2fr]">
            <article class="app-card p-6">
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p class="text-xs text-neutral-500 dark:text-neutral-400">{{ 'track.order' | t }}</p>
                  <p class="text-xl font-semibold tracking-wide" data-testid="tracked-number">{{ o.orderNumber }}</p>
                  @if (o.placedAt) {
                    <p class="text-xs text-neutral-500 dark:text-neutral-400">{{ 'track.placed_on' | t }} {{ o.placedAt | date: 'medium' }}</p>
                  }
                </div>
                <span class="rounded-full px-3 py-1 text-xs font-semibold" data-testid="tracked-status"
                  [class.bg-emerald-100]="!isCancelled()" [class.text-emerald-800]="!isCancelled()"
                  [class.bg-red-100]="isCancelled()" [class.text-red-700]="isCancelled()">{{ label(o.status) }}</span>
              </div>

              @if (isCancelled()) {
                <p class="mt-6 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/30 dark:text-red-300">{{ 'track.cancelled' | t }}</p>
              } @else {
                <ol class="mt-6 space-y-4" [attr.aria-label]="'track.progress' | t">
                  @for (step of steps; track step.status; let last = $last) {
                    <li class="relative flex gap-3">
                      @if (!last) {
                        <span class="absolute left-[11px] top-6 h-full w-px" [class.bg-emerald-500]="reached(step.status)" [class.bg-neutral-200]="!reached(step.status)" [class.dark:bg-neutral-800]="!reached(step.status)"></span>
                      }
                      <span class="relative z-10 inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 text-[10px] text-white"
                        [class.border-emerald-500]="reached(step.status)" [class.bg-emerald-500]="reached(step.status)"
                        [class.border-neutral-300]="!reached(step.status)" [class.dark:border-neutral-700]="!reached(step.status)">
                        @if (reached(step.status)) { ✓ }
                      </span>
                      <div>
                        <p class="text-sm" [class.font-semibold]="step.status === current()" [class.app-muted]="!reached(step.status)">{{ step.label }}</p>
                        @if (reachedAt(step.status); as at) {
                          <p class="text-xs text-neutral-500 dark:text-neutral-400">{{ at | date: 'medium' }}</p>
                        }
                      </div>
                    </li>
                  }
                </ol>
              }

              @if (o.courierName || o.courierTrackingNumber) {
                <p class="mt-6 rounded-xl bg-neutral-50 px-4 py-3 text-sm dark:bg-neutral-900">
                  {{ 'track.courier' | t }}: <strong>{{ o.courierName }}</strong>
                  @if (o.courierTrackingNumber) { · {{ o.courierTrackingNumber }} }
                </p>
              }
            </article>

            <article class="app-card h-fit p-6">
              <h2 class="text-base font-semibold">{{ 'track.items' | t }}</h2>
              <ul class="mt-4 space-y-3">
                @for (item of o.items; track $index) {
                  <li class="flex items-center gap-3">
                    @if (item.imageUrl) {
                      <img [src]="item.imageUrl" [alt]="item.name" class="h-14 w-14 rounded-lg object-cover" />
                    } @else {
                      <div class="h-14 w-14 rounded-lg bg-neutral-100 dark:bg-neutral-900"></div>
                    }
                    <div class="min-w-0 flex-1">
                      <p class="truncate text-sm font-medium">{{ item.name }}</p>
                      <p class="text-xs text-neutral-500 dark:text-neutral-400">
                        @if (item.size && item.size !== 'One Size') { {{ item.size }} · }{{ item.quantity }} × {{ item.unitPrice | currency:'BDT':'৳' }}
                      </p>
                    </div>
                    <span class="text-sm font-medium tabular-nums">{{ item.lineTotal | currency:'BDT':'৳' }}</span>
                  </li>
                }
              </ul>
              <div class="mt-4 space-y-1.5 border-t border-neutral-200 pt-4 text-sm dark:border-neutral-800">
                @if (o.subtotal != null) {
                  <div class="flex justify-between"><span class="app-muted">{{ 'track.subtotal' | t }}</span><span class="tabular-nums">{{ o.subtotal | currency:'BDT':'৳' }}</span></div>
                }
                @if (o.discount) {
                  <div class="flex justify-between text-emerald-600"><span>{{ 'track.discount' | t }}</span><span class="tabular-nums">−{{ o.discount | currency:'BDT':'৳' }}</span></div>
                }
                @if (o.shipping != null) {
                  <div class="flex justify-between"><span class="app-muted">{{ 'track.shipping' | t }}</span><span class="tabular-nums">{{ o.shipping | currency:'BDT':'৳' }}</span></div>
                }
                <div class="flex justify-between pt-1 text-base font-semibold"><span>{{ 'track.total' | t }}</span><span class="tabular-nums">{{ o.total | currency:'BDT':'৳' }}</span></div>
                @if (o.paymentMethod) {
                  <p class="pt-1 text-xs text-neutral-500 dark:text-neutral-400">{{ 'track.payment' | t }}: {{ paymentLabel(o.paymentMethod) }}</p>
                }
              </div>
            </article>
          </div>
        } @else if (state() === 'idle') {
          <p class="app-muted text-center text-sm">{{ 'track.hint' | t }}</p>
        }
      </div>
    </section>
  `,
})
export class TrackOrderPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly steps = STEPS;
  protected typed = '';
  protected readonly loading = signal(false);
  protected readonly state = signal<'idle' | 'found' | 'not-found' | 'error'>('idle');
  protected readonly order = signal<PublicOrderTracking | null>(null);
  protected readonly error = signal('');

  protected readonly current = computed(() => this.order()?.status ?? '');
  protected readonly isCancelled = computed(() => ['CANCELLED', 'RETURNED'].includes(this.current()));

  ngOnInit(): void {
    // A link from the order email or the thank-you page carries the number.
    const fromLink = this.route.snapshot.queryParamMap.get('number');
    if (fromLink) {
      this.typed = fromLink;
      this.search();
    }
  }

  protected search(): void {
    const number = this.typed.trim();
    if (!number) return;
    this.loading.set(true);
    void this.router.navigate([], { queryParams: { number }, replaceUrl: true });
    this.api.trackOrderByNumber(number).subscribe({
      next: (o) => {
        this.order.set(o);
        this.state.set('found');
        this.loading.set(false);
      },
      error: (err) => {
        this.order.set(null);
        this.loading.set(false);
        if (err?.status === 404) {
          this.state.set('not-found');
        } else {
          this.error.set(err?.status === 429
            ? 'Too many searches. Please wait a minute and try again.'
            : 'Could not look up the order right now. Please try again.');
          this.state.set('error');
        }
      },
    });
  }

  protected reached(status: string): boolean {
    const idx = STEPS.findIndex((s) => s.status === status);
    const cur = STEPS.findIndex((s) => s.status === (this.current() === 'PENDING' ? 'PLACED' : this.current()));
    return idx >= 0 && cur >= 0 && idx <= cur;
  }

  protected reachedAt(status: string): string | null {
    const hits = (this.order()?.steps ?? []).filter((s) => s.status === status);
    return hits.length ? hits[hits.length - 1].at : null;
  }

  protected label(status: string): string {
    return STEPS.find((s) => s.status === status)?.label
      ?? ({ PENDING: 'Placed', CANCELLED: 'Cancelled', RETURNED: 'Returned' } as Record<string, string>)[status]
      ?? status;
  }

  protected paymentLabel(method: string): string {
    return ({ CASH: 'Cash on delivery', COD: 'Cash on delivery', BKASH: 'bKash', CARD: 'Card' } as Record<string, string>)[method] ?? method;
  }
}
