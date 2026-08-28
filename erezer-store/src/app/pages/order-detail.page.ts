import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { ApiOrder, ApiOrderItem, OrderStatus, OrderTracking, ReturnRequestResponse } from '../core/api.models';
import { AuthService } from '../core/auth.service';
import { EcommerceStore } from '../core/store/ecommerce.store';
import { RevealDirective } from '../core/reveal.directive';

/** Display order for the storefront timeline. */
const TIMELINE_STEPS: { status: OrderStatus; label: string }[] = [
  { status: 'PLACED',           label: 'Placed' },
  { status: 'ACCEPTED',         label: 'Accepted' },
  { status: 'IN_PRODUCTION',    label: 'In production' },
  { status: 'PROCESSING',       label: 'Processing' },
  { status: 'SHIPPED',          label: 'Shipped' },
  { status: 'OUT_FOR_DELIVERY', label: 'Out for delivery' },
  { status: 'DELIVERED',        label: 'Delivered' },
];

@Component({
  standalone: true,
  imports: [CurrencyPipe, DatePipe, RouterLink, FormsModule, RevealDirective],
  template: `
    @if (loading()) {
      <p class="app-muted">Loading order…</p>
    } @else if (apiOrder(); as o) {
      <!-- ── API order ──────────────────────────────────────────────────────── -->
      <section class="space-y-6">
        <header class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <a routerLink="/orders" class="text-sm text-neutral-600 underline underline-offset-4 dark:text-neutral-300">
              Back to orders
            </a>
            <h1 class="app-section-title mt-2">{{ o.id }}</h1>
          </div>
          <p class="text-sm text-neutral-500 dark:text-neutral-400">{{ o.createdAt | date: 'medium' }}</p>
        </header>

        <div class="grid gap-4 md:grid-cols-3" appReveal>
          <article class="app-card p-4">
            <p class="text-sm text-neutral-500 dark:text-neutral-400">Payment</p>
            <p class="font-medium">{{ o.paymentMethod }}</p>
            @if (o.paymentId) {
              <p class="text-xs text-neutral-500 dark:text-neutral-400">ID: {{ o.paymentId }}</p>
            }
          </article>
          <article class="app-card p-4">
            <p class="text-sm text-neutral-500 dark:text-neutral-400">Status</p>
            <p class="font-medium">{{ statusLabel(currentStatus()) }}</p>
            @if (o.trackingNumber) {
              <p class="mt-1 text-xs text-neutral-500 dark:text-neutral-400">
                {{ o.courierName }} &middot; {{ o.trackingNumber }}
              </p>
            }
          </article>
          <article class="app-card p-4">
            <div class="flex items-start justify-between gap-2">
              <p class="text-sm text-neutral-500 dark:text-neutral-400">Shipping details</p>
              @if (canEditContact() && !editingContact()) {
                <button type="button" (click)="startEditContact(o)" class="text-xs font-semibold text-neutral-700 underline underline-offset-2 hover:text-black dark:text-neutral-300 dark:hover:text-white">Edit</button>
              }
            </div>
            <p class="font-medium">{{ o.deliveryAddress }}</p>
            <p class="text-sm text-neutral-500 dark:text-neutral-400">{{ o.customerPhone || 'No phone' }}</p>
          </article>
        </div>

        <!-- Edit shipping details (only while Placed) -->
        @if (editingContact()) {
          <article class="app-card p-5" appReveal>
            <h2 class="mb-1 text-lg font-semibold">Edit shipping details</h2>
            <p class="mb-3 text-sm text-neutral-600 dark:text-neutral-300">
              You can change your address and phone while the order is still <strong>Placed</strong>.
            </p>
            <label class="mb-1 block text-xs font-medium text-neutral-500">Shipping address</label>
            <textarea [(ngModel)]="editAddress" rows="2" maxlength="1000"
              class="mb-3 w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"></textarea>
            <label class="mb-1 block text-xs font-medium text-neutral-500">Phone number</label>
            <input [(ngModel)]="editPhone" maxlength="40" placeholder="e.g. 01700000000"
              class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900" />
            @if (contactError()) { <p class="mt-2 text-sm text-red-500">{{ contactError() }}</p> }
            <div class="mt-3 flex gap-2">
              <button (click)="saveContact()" [disabled]="savingContact() || !editAddress.trim()" class="btn-primary text-sm">
                {{ savingContact() ? 'Saving…' : 'Save changes' }}
              </button>
              <button (click)="editingContact.set(false)" class="btn-secondary text-sm">Cancel</button>
            </div>
          </article>
        }

        <!-- Timeline -->
        <article class="app-card p-6" appReveal>
          <h2 class="mb-5 text-lg font-semibold">Order timeline</h2>

          @if (isCancelled()) {
            <div class="flex items-center gap-3 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/30 dark:text-red-300">
              <svg class="h-5 w-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M9.75 9.75l4.5 4.5m0-4.5l-4.5 4.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
              <span>This order was cancelled.@if (o.cancellationReason) { Reason: {{ o.cancellationReason }} }</span>
            </div>
          } @else if (isReturned()) {
            <div class="flex items-center gap-3 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">
              <svg class="h-5 w-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M9 15L3 9m0 0l6-6M3 9h12a6 6 0 010 12h-3"/></svg>
              This order has been returned.
            </div>
          } @else {
            <ol>
              @for (step of steps; track step.status; let i = $index; let last = $last) {
                <li class="relative flex gap-4" [class.pb-8]="!last">
                  <!-- node + connector column -->
                  <div class="relative z-10 flex-shrink-0">
                    <span class="tl-node" [class.tl-done]="reached(i)" [class.tl-current]="isCurrent(i)"
                      [style.animation-delay.ms]="i * 90">
                      @if (reached(i) && !isCurrent(i)) {
                        <svg class="tl-check h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/></svg>
                      } @else {
                        <span class="h-2.5 w-2.5 rounded-full bg-current"></span>
                      }
                      @if (isCurrent(i)) {
                        <span class="tl-ping"></span>
                      }
                    </span>
                    @if (!last) {
                      <span class="tl-conn">
                        <span class="tl-conn-fill" [class.filled]="segFilled(i)" [style.transition-delay.ms]="i * 120"></span>
                      </span>
                    }
                  </div>
                  <!-- content -->
                  <div class="pb-1 pt-1">
                    <p class="font-medium leading-tight transition-colors" [class.text-neutral-400]="!reached(i)" [class.dark:text-neutral-500]="!reached(i)">
                      {{ step.label }}
                    </p>
                    @if (stepDate(step.status); as d) {
                      <p class="mt-0.5 text-xs text-neutral-500 dark:text-neutral-400">{{ d | date: 'medium' }}</p>
                    } @else if (isCurrent(i)) {
                      <p class="mt-0.5 text-xs font-medium text-emerald-600">In progress…</p>
                    }
                  </div>
                </li>
              }
            </ol>
          }

          @if (tracking(); as t) {
            @if (t.history.length > 0) {
              <details class="mt-6 text-sm">
                <summary class="cursor-pointer select-none font-medium text-neutral-600 dark:text-neutral-300">
                  Full history ({{ t.history.length }} updates)
                </summary>
                <ul class="mt-3 space-y-2 border-l border-neutral-200 pl-4 dark:border-neutral-700">
                  @for (h of t.history; track h.id) {
                    <li>
                      <p>
                        <span class="font-medium">{{ statusLabel(h.toStatus) }}</span>
                        <span class="ml-2 text-xs text-neutral-500 dark:text-neutral-400">{{ h.createdAt | date: 'medium' }}</span>
                      </p>
                      @if (h.note) { <p class="text-xs text-neutral-500 dark:text-neutral-400">{{ h.note }}</p> }
                    </li>
                  }
                </ul>
              </details>
            }
          }
        </article>

        <!-- Returns / RMA panel -->
        @if (existingReturn(); as r) {
          <article class="app-card p-5">
            <h2 class="mb-2 text-lg font-semibold">Return request</h2>
            <p class="text-sm">
              <span class="inline-block rounded-full px-2 py-0.5 text-xs font-medium"
                [class]="returnBadgeClass(r.status)">{{ returnStatusLabel(r.status) }}</span>
              <span class="ml-2 text-neutral-500 dark:text-neutral-400">
                Filed {{ r.requestedAt | date: 'medium' }}
              </span>
            </p>
            @if (r.refundAmount !== null) {
              <p class="mt-2 text-sm">Refund: <strong>{{ r.refundAmount | currency:'BDT':'৳' }}</strong></p>
            }
            @if (r.adminNotes) {
              <p class="mt-2 text-sm italic text-neutral-600 dark:text-neutral-300">
                "{{ r.adminNotes }}"
              </p>
            }
          </article>
        } @else if (canRequestReturn()) {
          <article class="app-card p-5">
            <h2 class="mb-2 text-lg font-semibold">Need to return something?</h2>
            <p class="mb-3 text-sm text-neutral-600 dark:text-neutral-300">
              You can request a return within 14 days of delivery.
            </p>
            <a [routerLink]="['/orders', apiOrder()!.id, 'return']" class="btn-secondary text-sm">
              Request a return
            </a>
          </article>
        }

        <!-- Cancel action -->
        @if (canCancel()) {
          <article class="app-card p-5">
            <h2 class="mb-2 text-lg font-semibold">Need to cancel?</h2>
            <p class="mb-3 text-sm text-neutral-600 dark:text-neutral-300">
              You can cancel this order while it's still being prepared. Tell us why (optional):
            </p>
            <textarea
              [(ngModel)]="cancelReason"
              rows="2"
              maxlength="500"
              placeholder="Changed mind, wrong size, etc."
              class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700"></textarea>
            @if (cancelError()) {
              <p class="mt-2 text-sm text-red-500">{{ cancelError() }}</p>
            }
            <button
              (click)="cancelOrder()"
              [disabled]="cancelling()"
              class="btn-secondary mt-3 text-sm">
              {{ cancelling() ? 'Cancelling…' : 'Cancel order' }}
            </button>
          </article>
        }

        <article class="app-card p-5">
          <h2 class="mb-4 text-xl font-semibold">Items & totals</h2>
          <div class="space-y-2">
            @for (item of o.orderItems; track item.id) {
              <div class="flex justify-between text-sm">
                <span>
                  {{ item.productName }}
                  @if (variantText(item)) {
                    <span class="text-neutral-500">({{ variantText(item) }})</span>
                  }
                  × {{ item.quantity }}
                </span>
                <span>{{ (item.priceAtOrder * item.quantity) | currency:'BDT':'৳' }}</span>
              </div>
            }
          </div>
          <div class="mt-4 space-y-1 border-t border-neutral-200 pt-3 text-sm dark:border-neutral-700">
            <div class="flex justify-between"><span>Shipping</span><span>{{ o.deliveryCharge | currency:'BDT':'৳' }}</span></div>
            <div class="flex justify-between font-medium"><span>Total</span><span>{{ o.totalAmount | currency:'BDT':'৳' }}</span></div>
          </div>
        </article>
      </section>

    } @else if (localOrder(); as lo) {
      <!-- ── Local (legacy guest) order ─────────────────────────────────────── -->
      <section class="space-y-6">
        <header class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <a routerLink="/orders" class="text-sm text-neutral-600 underline underline-offset-4 dark:text-neutral-300">
              Back to orders
            </a>
            <h1 class="app-section-title mt-2">{{ lo.id }}</h1>
          </div>
          <p class="text-sm text-neutral-500 dark:text-neutral-400">{{ lo.createdAt | date: 'medium' }}</p>
        </header>

        <div class="grid gap-4 md:grid-cols-3">
          <article class="app-card p-4">
            <p class="text-sm text-neutral-500 dark:text-neutral-400">Payment</p>
            <p class="font-medium">{{ lo.payment.method }}</p>
            <p class="text-sm">Status: {{ lo.payment.status }}</p>
          </article>
          <article class="app-card p-4">
            <p class="text-sm text-neutral-500 dark:text-neutral-400">Delivery</p>
            <p class="font-medium">{{ lo.delivery.status }}</p>
            <p class="text-sm">ETA: {{ lo.delivery.estimatedDate | date: 'mediumDate' }}</p>
          </article>
          <article class="app-card p-4">
            <p class="text-sm text-neutral-500 dark:text-neutral-400">Shipping address</p>
            <p class="font-medium">{{ lo.shippingAddress.name }}</p>
            <p class="text-sm">{{ lo.shippingAddress.address }}</p>
          </article>
        </div>

        <article class="app-card p-5">
          <h2 class="mb-4 text-xl font-semibold">Items & totals</h2>
          <div class="space-y-2">
            @for (item of lo.items; track item.productId + item.size) {
              <div class="flex justify-between text-sm">
                <span>{{ productName(item.productId) }} ({{ item.size }}) × {{ item.quantity }}</span>
              </div>
            }
          </div>
          <div class="mt-4 space-y-1 border-t border-neutral-200 pt-3 text-sm dark:border-neutral-700">
            <div class="flex justify-between"><span>Subtotal</span><span>{{ lo.subtotal | currency:'BDT':'৳' }}</span></div>
            <div class="flex justify-between"><span>Shipping</span><span>{{ lo.shippingFee | currency:'BDT':'৳' }}</span></div>
            <div class="flex justify-between"><span>Discount</span><span>-{{ lo.discount | currency:'BDT':'৳' }}</span></div>
            <div class="flex justify-between font-medium"><span>Total</span><span>{{ lo.total | currency:'BDT':'৳' }}</span></div>
          </div>
        </article>
      </section>

    } @else {
      <section class="app-card p-6">
        <p class="text-neutral-600 dark:text-neutral-300">Order not found.</p>
      </section>
    }
  `,
  styles: [`
    @keyframes nodePop { 0% { transform: scale(.3); opacity: 0; } 60% { transform: scale(1.12); } 100% { transform: scale(1); opacity: 1; } }
    @keyframes checkPop { 0% { transform: scale(0); } 70% { transform: scale(1.2); } 100% { transform: scale(1); } }
    @keyframes tlPing { 0% { transform: scale(1); opacity: .6; } 100% { transform: scale(2.1); opacity: 0; } }

    .tl-node {
      position: relative; display: inline-flex; align-items: center; justify-content: center;
      height: 2.25rem; width: 2.25rem; border-radius: 9999px;
      border: 2px solid rgb(212 212 212); background: white; color: rgb(163 163 163);
      transition: background-color .35s ease, border-color .35s ease, color .35s ease;
      animation: nodePop .45s cubic-bezier(.22,1,.36,1) both;
    }
    :host-context(.dark) .tl-node { background: rgb(23 23 23); border-color: rgb(64 64 64); }
    .tl-done { background: rgb(23 23 23); border-color: rgb(23 23 23); color: white; }
    :host-context(.dark) .tl-done { background: white; border-color: white; color: black; }
    .tl-current { background: rgb(16 185 129); border-color: rgb(16 185 129); color: white; }
    :host-context(.dark) .tl-current { background: rgb(16 185 129); border-color: rgb(16 185 129); color: white; }
    .tl-check { animation: checkPop .4s cubic-bezier(.22,1,.36,1) both; }
    .tl-ping {
      position: absolute; inset: -2px; border-radius: 9999px;
      border: 2px solid rgb(16 185 129); animation: tlPing 1.6s ease-out infinite;
    }

    .tl-conn {
      position: absolute; left: 50%; top: 2.25rem; bottom: -0.25rem; width: 2px;
      transform: translateX(-50%); background: rgb(229 229 229); overflow: hidden;
    }
    :host-context(.dark) .tl-conn { background: rgb(64 64 64); }
    .tl-conn-fill {
      position: absolute; inset: 0 0 auto 0; width: 100%; height: 0;
      background: rgb(23 23 23); transition: height .6s ease;
    }
    :host-context(.dark) .tl-conn-fill { background: white; }
    .tl-conn-fill.filled { height: 100%; }

    @media (prefers-reduced-motion: reduce) {
      .tl-node, .tl-check { animation: none !important; }
      .tl-ping { animation: none !important; opacity: 0; }
      .tl-conn-fill { transition: none !important; }
    }
  `]
})
export class OrderDetailPage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  protected readonly store = inject(EcommerceStore);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly steps = TIMELINE_STEPS;

  /** Live timeline: re-check the order/tracking so admin updates appear without a refresh. */
  private readonly POLL_MS = 60_000;
  private poll?: ReturnType<typeof setInterval>;

  protected readonly loading   = signal(true);
  protected readonly apiOrder  = signal<ApiOrder | null>(null);
  protected readonly tracking  = signal<OrderTracking | null>(null);
  protected readonly cancelError = signal<string | null>(null);
  protected readonly cancelling  = signal(false);
  protected cancelReason = '';

  // Edit shipping details (address + phone) — only while PLACED.
  protected readonly editingContact = signal(false);
  protected readonly savingContact  = signal(false);
  protected readonly contactError   = signal<string | null>(null);
  protected editAddress = '';
  protected editPhone   = '';

  // ── Phase 5: returns ───────────────────────────────────────────────────────
  protected readonly existingReturn = signal<ReturnRequestResponse | null>(null);

  /** True when the order is DELIVERED, no existing return, and within 14 days. */
  protected readonly canRequestReturn = computed(() => {
    if (this.existingReturn()) return false;
    if (this.currentStatus() !== 'DELIVERED') return false;
    return true;
  });

  protected readonly currentStatus = computed<OrderStatus>(() => {
    const t = this.tracking();
    if (t) return t.currentStatus;
    const o = this.apiOrder();
    return (o?.orderStatus as OrderStatus) ?? 'PLACED';
  });

  protected readonly isCancelled = computed(() => this.currentStatus() === 'CANCELLED');
  protected readonly isReturned  = computed(() => this.currentStatus() === 'RETURNED');

  // ── animated timeline state ─────────────────────────────────────────────────
  /** Flipped true shortly after mount so the connector fills animate in. */
  protected readonly animate = signal(false);

  /** Index of the current step within TIMELINE_STEPS (-1 if not on the path). */
  protected readonly currentIndex = computed(() => {
    const order = TIMELINE_STEPS.map((s) => s.status);
    const c = this.currentStatus();
    const norm = c === 'PENDING' ? 'PLACED' : c;
    return order.indexOf(norm as OrderStatus);
  });

  /** Per-status timestamp pulled from the tracking history (+ createdAt for PLACED). */
  protected readonly stepDates = computed<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    const t = this.tracking();
    if (t) for (const h of t.history) { if (h.toStatus) map[h.toStatus] = h.createdAt; }
    return map;
  });

  protected reached(i: number): boolean { return this.currentIndex() >= 0 && this.currentIndex() >= i; }
  protected isCurrent(i: number): boolean { return i === this.currentIndex(); }
  /** Connector below step i is filled once we've progressed past it (animated). */
  protected segFilled(i: number): boolean { return this.animate() && this.currentIndex() > i; }

  protected stepDate(status: string): string | null {
    const d = this.stepDates()[status];
    if (d) return d;
    if (status === 'PLACED') return this.apiOrder()?.createdAt ?? null;
    return null;
  }

  protected readonly canCancel = computed(() => {
    const t = this.tracking();
    return !!t && t.allowedCustomerNextStates.includes('CANCELLED');
  });

  /** Address + phone are editable only while the order is still PLACED. */
  protected readonly canEditContact = computed(() => this.currentStatus() === 'PLACED');

  protected readonly localOrder = computed(() => {
    const id = this.route.snapshot.paramMap.get('id');
    return id ? this.store.getOrderById(id) : undefined;
  });

  ngOnInit(): void {
    const orderId = this.route.snapshot.paramMap.get('id');
    const userId  = this.auth.userId();

    if (!orderId || !userId) {
      this.loading.set(false);
      return;
    }

    // Kick off the connector-fill animation just after the first paint.
    if (typeof window !== 'undefined') {
      setTimeout(() => this.animate.set(true), 120);
    } else {
      this.animate.set(true);
    }

    this.api.getOrderById(userId, orderId)
      .pipe(catchError(() => of(null)))
      .subscribe((o) => {
        this.apiOrder.set(o);
        this.loading.set(false);
      });

    this.api.trackOrder(userId, orderId)
      .pipe(catchError(() => of(null)))
      .subscribe((t) => this.tracking.set(t));

    // Pull any existing return so we can show its status banner.
    this.api.listMyReturns(userId)
      .pipe(catchError(() => of([] as ReturnRequestResponse[])))
      .subscribe((list) => {
        const match = list.find((r) => r.orderId === orderId) ?? null;
        this.existingReturn.set(match);
      });

    this.startPolling(userId, orderId);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  /** Quietly re-fetch order + tracking every POLL_MS so the timeline stays live. */
  private startPolling(userId: string, orderId: string): void {
    if (typeof window === 'undefined') return;
    this.poll = setInterval(() => {
      if (document.visibilityState === 'hidden') return;
      // Nothing more will change once the order is in a terminal state.
      const s = this.currentStatus();
      if (s === 'DELIVERED' || s === 'CANCELLED' || s === 'RETURNED') {
        if (this.poll) { clearInterval(this.poll); this.poll = undefined; }
        return;
      }
      this.api.getOrderById(userId, orderId).pipe(catchError(() => of(null)))
        .subscribe((o) => { if (o) this.apiOrder.set(o); });
      this.api.trackOrder(userId, orderId).pipe(catchError(() => of(null)))
        .subscribe((t) => { if (t) this.tracking.set(t); });
    }, this.POLL_MS);
  }

  // ── return helpers ────────────────────────────────────────────────────────

  protected returnStatusLabel(status: string): string {
    switch (status) {
      case 'REQUESTED': return 'Requested';
      case 'APPROVED':  return 'Approved';
      case 'REJECTED':  return 'Rejected';
      case 'PICKED_UP': return 'Picked up';
      case 'REFUNDED':  return 'Refunded';
      default:          return status;
    }
  }

  protected returnBadgeClass(status: string): string {
    switch (status) {
      case 'REFUNDED':
        return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300';
      case 'REJECTED':
        return 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300';
      case 'APPROVED':
      case 'PICKED_UP':
        return 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300';
      default:
        return 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300';
    }
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  protected statusLabel(status: string): string {
    const found = TIMELINE_STEPS.find((s) => s.status === status);
    if (found) return found.label;
    switch (status) {
      case 'PENDING':   return 'Placed';
      case 'CANCELLED': return 'Cancelled';
      case 'RETURNED':  return 'Returned';
      default:          return status;
    }
  }

  protected isStepReached(target: OrderStatus): boolean {
    const order = TIMELINE_STEPS.map((s) => s.status);
    const current = this.currentStatus();
    const normCurrent = current === 'PENDING' ? 'PLACED' : current;
    const currIdx   = order.indexOf(normCurrent as OrderStatus);
    const targetIdx = order.indexOf(target);
    return currIdx >= 0 && targetIdx >= 0 && currIdx >= targetIdx;
  }

  protected async cancelOrder(): Promise<void> {
    const userId  = this.auth.userId();
    const orderId = this.route.snapshot.paramMap.get('id');
    if (!userId || !orderId) return;
    this.cancelling.set(true);
    this.cancelError.set(null);
    this.api.cancelOrder(userId, orderId, { reason: this.cancelReason || undefined })
      .pipe(catchError((err) => {
        this.cancelError.set(err?.error?.message ?? 'Could not cancel this order.');
        this.cancelling.set(false);
        return of(null);
      }))
      .subscribe((updated) => {
        this.cancelling.set(false);
        if (updated) {
          this.apiOrder.set(updated);
          this.api.trackOrder(userId, orderId)
            .pipe(catchError(() => of(null)))
            .subscribe((t) => this.tracking.set(t));
        }
      });
  }

  protected startEditContact(o: ApiOrder): void {
    this.editAddress = o.deliveryAddress ?? '';
    this.editPhone = o.customerPhone ?? '';
    this.contactError.set(null);
    this.editingContact.set(true);
  }

  protected saveContact(): void {
    const userId  = this.auth.userId();
    const orderId = this.route.snapshot.paramMap.get('id');
    if (!userId || !orderId || !this.editAddress.trim()) return;
    this.savingContact.set(true);
    this.contactError.set(null);
    this.api.updateOrderContact(userId, orderId, {
      deliveryAddress: this.editAddress.trim(),
      phone: this.editPhone.trim() || undefined,
    }).pipe(catchError((err) => {
      this.contactError.set(err?.error?.message ?? 'Could not update your shipping details.');
      this.savingContact.set(false);
      return of(null);
    })).subscribe((updated) => {
      this.savingContact.set(false);
      if (updated) {
        this.apiOrder.set(updated);
        this.editingContact.set(false);
      }
    });
  }

  protected productName(productId: string): string {
    return this.store.products().find((p) => p.id === productId)?.name ?? 'Unknown Product';
  }

  /** Size label from a snapshotted order-line variant; '' if none (size-only). */
  protected variantText(item: ApiOrderItem): string {
    return item.variantSize?.trim() ?? '';
  }
}
