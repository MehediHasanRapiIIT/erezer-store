import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { BundleCheckoutStore } from '../core/store/bundle-checkout.store';
import { variantLabel } from '../core/cart-actions.service';
import { SettingsStore } from '../core/store/settings.store';
import type { ApiBundleOffer, ApiVariant, BundleProduct } from '../core/api.models';

interface SlotSelection {
  productId: number;
  variantId: number | null;
  size: string;
  unitPrice: number;
  name: string;
  image: string | null;
}

@Component({
  standalone: true,
  imports: [CurrencyPipe],
  template: `
    <section class="mx-auto max-w-6xl px-4 py-8">
      @if (loading()) {
        <p class="app-muted py-20 text-center text-sm">Loading bundle…</p>
      } @else if (!bundle()) {
        <p class="app-muted py-20 text-center text-sm">{{ error() || 'Bundle not found.' }}</p>
      } @else if (bundle(); as b) {
        <div class="grid gap-8 lg:grid-cols-2">

          <!-- Gallery -->
          <div class="space-y-3">
            <div class="relative overflow-hidden rounded-2xl bg-neutral-100 dark:bg-neutral-900">
              @if (mainImage()) {
                <img [src]="mainImage()" [alt]="b.name" class="aspect-square w-full object-cover" />
              } @else {
                <div class="flex aspect-square w-full items-center justify-center text-5xl">🎁</div>
              }
              @if (b.compareAtPrice && b.savings && b.savings > 0) {
                <span class="absolute left-3 top-3 rounded bg-red-500 px-2 py-1 text-xs font-bold text-white">
                  {{ discountPct(b) }}% OFF
                </span>
              }
            </div>
            @if (b.images.length > 1) {
              <div class="flex gap-2 overflow-x-auto">
                @for (img of b.images; track img; let i = $index) {
                  <button type="button" (click)="activeImage.set(i)"
                    class="h-16 w-16 shrink-0 overflow-hidden rounded-lg border-2"
                    [class.border-neutral-900]="activeImage() === i"
                    [class.dark:border-white]="activeImage() === i"
                    [class.border-transparent]="activeImage() !== i">
                    <img [src]="img" alt="" class="h-full w-full object-cover" />
                  </button>
                }
              </div>
            }
          </div>

          <!-- Detail -->
          <div class="space-y-5">
            @if (b.label) { <span class="text-xs font-semibold uppercase tracking-wide text-amber-600">{{ b.label }}</span> }
            <h1 class="text-2xl font-bold">{{ b.name }}</h1>

            <div class="flex items-center gap-3">
              @if (byQuantity()) {
                <span class="text-2xl font-bold" data-testid="bundle-price">Up to {{ topPercent() }}% off</span>
              } @else {
                @if (b.compareAtPrice) {
                  <span class="text-lg text-neutral-400 line-through">{{ b.compareAtPrice | currency:'BDT':'৳' }}</span>
                }
                <span class="text-2xl font-bold" data-testid="bundle-price">{{ b.bundlePrice | currency:'BDT':'৳' }}</span>
              }
              @if (b.savings && b.savings > 0) {
                <span class="rounded bg-emerald-600 px-2 py-1 text-sm font-semibold text-white">Save {{ b.savings | currency:'BDT':'৳' }}</span>
              }
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <p class="app-muted text-sm" data-testid="bundle-headline">
                {{ b.headline || ('Buy ' + b.buyCount + ' Get ' + b.getCount) }} —
                {{ byQuantity() ? 'pick ' + minItems() + ' or more below' : 'pick ' + b.slots + ' item' + (b.slots > 1 ? 's' : '') + ' below' }}.
              </p>
              @if (hasSizeChart()) {
                <button type="button" (click)="sizeGuideOpen.set(true)"
                  class="inline-flex items-center gap-1 text-xs font-semibold text-neutral-500 underline underline-offset-4 hover:text-neutral-900 dark:hover:text-neutral-100">
                  📏 Size chart
                </button>
              }
            </div>
            @if (b.description) { <p class="text-sm text-neutral-600 dark:text-neutral-300">{{ b.description }}</p> }

            @if (byQuantity()) {
              <!-- The steps of the discount, with the one reached so far marked. -->
              <ul class="flex flex-wrap gap-2" data-testid="bundle-steps">
                @for (t of b.tiers ?? []; track t.quantity) {
                  <li class="rounded-full border px-3 py-1 text-xs font-semibold"
                    [class.border-emerald-600]="reachedPercent() === t.percentOff" [class.bg-emerald-600]="reachedPercent() === t.percentOff"
                    [class.text-white]="reachedPercent() === t.percentOff"
                    [class.border-neutral-300]="reachedPercent() !== t.percentOff" [class.dark:border-neutral-700]="reachedPercent() !== t.percentOff">
                    {{ t.quantity }}+ items · {{ t.percentOff }}% off
                  </li>
                }
              </ul>
            }

            <!-- Slots: a set number of them, or for a quantity discount as many as the customer adds. -->
            <div class="grid gap-3" [style.grid-template-columns]="'repeat(' + slotColumns() + ', minmax(0,1fr))'" data-testid="bundle-slots">
              @for (sel of selections(); track $index) {
                <button type="button" (click)="openSlot($index)"
                  class="flex aspect-[3/4] flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed p-2 text-center transition"
                  [class.border-neutral-900]="sel"
                  [class.dark:border-white]="sel"
                  [class.border-neutral-300]="!sel"
                  [class.dark:border-neutral-700]="!sel">
                  @if (sel) {
                    @if (sel.image) { <img [src]="sel.image" [alt]="sel.name" class="h-20 w-20 rounded-lg object-cover" /> }
                    <span class="line-clamp-2 text-xs font-medium">{{ sel.name }}</span>
                    <span class="text-[11px] text-neutral-500">Size: {{ sel.size }}</span>
                  } @else {
                    <span class="text-2xl text-neutral-400">＋</span>
                    <span class="text-xs font-semibold">Select a product</span>
                  }
                </button>
              }
            </div>
            @if (byQuantity() && selections().length < maxItems()) {
              <button type="button" (click)="addSlot()" data-testid="bundle-add-item"
                class="w-full rounded-xl border border-dashed border-neutral-300 py-2.5 text-sm font-semibold text-neutral-600 transition hover:border-neutral-900 hover:text-neutral-900 dark:border-neutral-700 dark:text-neutral-300 dark:hover:border-white dark:hover:text-white">
                ＋ Add another item{{ nextStep() ? ' — ' + nextStep() : '' }}
              </button>
            }

            <!-- Summary rows -->
            @if (filledCount() > 0) {
              <div class="space-y-1.5 rounded-xl border border-neutral-200 p-3 text-sm dark:border-neutral-800">
                @for (sel of selections(); track $index) {
                  @if (sel) {
                    <div class="flex items-center justify-between gap-2">
                      <span class="min-w-0 truncate">{{ sel.name }} <span class="text-neutral-400">· {{ sel.size }}</span></span>
                      <button type="button" (click)="removeSlot($index)"
                        class="inline-flex shrink-0 items-center gap-1 rounded-md border border-red-200 px-2.5 py-1 text-xs font-medium text-red-600 transition hover:bg-red-50 dark:border-red-900/60 dark:text-red-400 dark:hover:bg-red-950/40">
                        <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/></svg>
                        Remove
                      </button>
                    </div>
                  }
                }
              </div>
            }

            <!-- Buy all -->
            <div class="flex items-center justify-between rounded-xl bg-neutral-900 px-5 py-4 text-white dark:bg-white dark:text-neutral-900">
              <div>
                @if (byQuantity()) {
                  <p class="text-xs opacity-70" data-testid="bundle-saving">
                    {{ reachedPercent() > 0 ? reachedPercent() + '% off — you save ' : 'Pick ' + minItems() + ' or more to save' }}
                    @if (reachedPercent() > 0) { {{ saving() | currency:'BDT':'৳' }} }
                  </p>
                  <p class="text-xl font-bold" data-testid="bundle-pay">
                    @if (reachedPercent() > 0) { <span class="mr-1.5 text-sm font-medium line-through opacity-60">{{ pickedTotal() | currency:'BDT':'৳' }}</span> }
                    {{ payable() | currency:'BDT':'৳' }}
                  </p>
                } @else {
                  <p class="text-xs opacity-70">Buy all for</p>
                  <p class="text-xl font-bold" data-testid="bundle-pay">{{ b.bundlePrice | currency:'BDT':'৳' }}</p>
                }
              </div>
              <button type="button" (click)="proceed()" [disabled]="!allFilled()" data-testid="bundle-checkout"
                class="rounded-lg bg-white px-5 py-2.5 text-sm font-bold text-neutral-900 disabled:opacity-40 dark:bg-neutral-900 dark:text-white">
                {{ allFilled() ? 'Checkout' : (filledCount() + ' / ' + selections().length + ' selected') }}
              </button>
            </div>
          </div>
        </div>
      }
    </section>

    <!-- ── Product picker modal ─────────────────────────────────────────────── -->
    @if (modalOpen()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" (click)="closeModal()">
        <div class="max-h-[85vh] w-full max-w-lg overflow-auto rounded-2xl bg-white p-5 dark:bg-neutral-900" (click)="$event.stopPropagation()">
          <div class="mb-4 flex items-center justify-between">
            <h2 class="text-lg font-bold">{{ modalProduct() ? 'Choose a size' : 'Select a product' }}</h2>
            <button type="button" (click)="closeModal()" class="text-xl text-neutral-400 hover:text-neutral-700">&times;</button>
          </div>

          @if (!modalProduct()) {
            <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
              @for (p of bundle()?.products ?? []; track p.id) {
                <button type="button" (click)="pickProduct(p)"
                  class="rounded-xl border border-neutral-200 p-2 text-left transition hover:border-neutral-900 dark:border-neutral-700">
                  @if (p.imageUrl) { <img [src]="p.imageUrl" [alt]="p.name" class="aspect-square w-full rounded-lg object-cover" /> }
                  <p class="mt-1 line-clamp-2 text-xs font-medium">{{ p.name }}</p>
                  <p class="text-xs text-neutral-500">{{ effectivePrice(p) | currency:'BDT':'৳' }}</p>
                </button>
              }
            </div>
          } @else {
            <div class="mb-3 flex items-center gap-3">
              @if (modalProduct()!.imageUrl) { <img [src]="modalProduct()!.imageUrl" alt="" class="h-14 w-14 rounded-lg object-cover" /> }
              <div class="flex-1">
                <p class="text-sm font-medium">{{ modalProduct()!.name }}</p>
                <button type="button" (click)="modalProduct.set(null)" class="text-xs text-neutral-500 hover:underline">← choose another</button>
              </div>
              @if (hasSizeChart()) {
                <button type="button" (click)="sizeGuideOpen.set(true)"
                  class="inline-flex items-center gap-1 text-xs font-semibold text-neutral-500 underline underline-offset-4 hover:text-neutral-900 dark:hover:text-neutral-100">
                  📏 Size chart
                </button>
              }
            </div>
            @if (loadingVariants()) {
              <p class="app-muted py-4 text-center text-sm">Loading sizes…</p>
            } @else if (modalVariants().length === 0) {
              <button type="button" (click)="chooseSize(null, 'One Size')" class="btn-primary w-full">Add (One Size)</button>
            } @else {
              <div class="flex flex-wrap gap-2">
                @for (v of modalVariants(); track v.id) {
                  <button type="button" (click)="chooseSize(v.id, labelOf(v))" [disabled]="outOfStock(v)" data-testid="bundle-variant"
                    class="rounded-lg border px-4 py-2 text-sm font-medium transition disabled:opacity-40"
                    [class.border-neutral-300]="true" [class.dark:border-neutral-700]="true"
                    [class.hover:border-neutral-900]="!outOfStock(v)">
                    {{ labelOf(v) }}
                    @if (outOfStock(v)) { <span class="ml-1 text-[10px] text-red-500">out</span> }
                  </button>
                }
              </div>
            }
          }
        </div>
      </div>
    }

    <!-- ── Size guide modal (reuses admin-managed size chart) ───────────────── -->
    @if (sizeGuideOpen() && settings()?.sizeChart; as sc) {
      <div class="fixed inset-0 z-[60] flex items-center justify-center p-4" (click)="sizeGuideOpen.set(false)">
        <div class="absolute inset-0 bg-black/50 backdrop-blur-sm" aria-hidden="true"></div>
        <div class="relative z-10 flex max-h-[88vh] w-full max-w-lg flex-col overflow-hidden rounded-3xl bg-white shadow-2xl dark:bg-neutral-900" (click)="$event.stopPropagation()">
          <div class="flex items-center justify-between border-b border-neutral-200 px-6 py-4 dark:border-neutral-800">
            <h3 class="text-lg font-bold tracking-tight">Size Guide &amp; Fit</h3>
            <button type="button" (click)="sizeGuideOpen.set(false)" aria-label="Close" class="inline-flex h-9 w-9 items-center justify-center rounded-full text-neutral-500 transition hover:bg-neutral-100 dark:hover:bg-neutral-800">
              <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/></svg>
            </button>
          </div>
          <div class="overflow-y-auto px-6 py-5">
            <table class="w-full border-collapse overflow-hidden rounded-xl text-sm">
              <thead>
                <tr class="bg-neutral-100 text-left dark:bg-neutral-800">
                  <th class="px-4 py-2.5 font-semibold">Size</th>
                  @for (col of sc.columns; track $index) { <th class="px-4 py-2.5 font-semibold">{{ col }} (in)</th> }
                </tr>
              </thead>
              <tbody>
                @for (row of sc.rows; track $index) {
                  <tr class="border-b border-neutral-100 odd:bg-white even:bg-neutral-50 dark:border-neutral-800 dark:odd:bg-neutral-900 dark:even:bg-neutral-800/40">
                    <td class="px-4 py-2.5 font-semibold">{{ row.size }}</td>
                    @for (cell of row.cells; track $index) { <td class="px-4 py-2.5 text-neutral-600 dark:text-neutral-300">{{ (cell.inch ?? cell.cm) ?? '—' }}</td> }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      </div>
    }
  `,
})
export class BundleDetailPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly bundleCheckout = inject(BundleCheckoutStore);
  private readonly settingsStore = inject(SettingsStore);

  protected readonly settings = this.settingsStore.settings;
  protected readonly sizeGuideOpen = signal(false);
  protected readonly hasSizeChart = computed(() => {
    const c = this.settings()?.sizeChart;
    return !!c && c.rows.length > 0;
  });

  protected readonly bundle = signal<ApiBundleOffer | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly activeImage = signal(0);
  protected readonly selections = signal<(SlotSelection | null)[]>([]);

  // Modal
  protected readonly modalOpen = signal(false);
  protected readonly modalSlot = signal(0);
  protected readonly modalProduct = signal<BundleProduct | null>(null);
  protected readonly modalVariants = signal<ApiVariant[]>([]);
  protected readonly loadingVariants = signal(false);

  protected readonly mainImage = computed(() => this.bundle()?.images[this.activeImage()] ?? this.bundle()?.images[0] ?? null);
  protected readonly filledCount = computed(() => this.selections().filter(Boolean).length);
  protected readonly allFilled = computed(() => {
    const s = this.selections();
    return s.length > 0 && s.length >= this.minItems() && s.every(Boolean);
  });

  // ── the kinds of offer ──────────────────────────────────────────────────
  /** A quantity discount: any number of items from the smallest step up, a percentage off. */
  protected readonly byQuantity = computed(() => this.bundle()?.offerType === 'QUANTITY_DISCOUNT');
  protected readonly minItems = computed(() => this.bundle()?.minItems ?? this.bundle()?.slots ?? 0);
  protected readonly maxItems = computed(() => this.bundle()?.maxItems ?? this.bundle()?.slots ?? 0);
  protected readonly topPercent = computed(() => Math.max(0, ...(this.bundle()?.tiers ?? []).map((t) => t.percentOff)));
  /** No more than four boxes to a row, however many items are being picked. */
  protected readonly slotColumns = computed(() => Math.max(1, Math.min(4, this.selections().length)));
  /** The normal prices of what has been picked so far. */
  protected readonly pickedTotal = computed(() =>
    this.selections().reduce((sum, s) => sum + (s?.unitPrice ?? 0), 0));
  /** The percentage earned by the number of items picked: that of the highest step reached. */
  protected readonly reachedPercent = computed(() => {
    let percent = 0;
    for (const t of this.bundle()?.tiers ?? []) if (this.filledCount() >= t.quantity) percent = t.percentOff;
    return percent;
  });
  /** What the percentage takes off, to the paisa - the same sum the server does at checkout. */
  protected readonly saving = computed(() => Math.round(this.pickedTotal() * this.reachedPercent()) / 100);
  /** What the customer pays for the items: the bundle's one price, or for a quantity discount the total less the saving. */
  protected readonly payable = computed(() => {
    const b = this.bundle();
    if (!b) return 0;
    return this.byQuantity() ? Math.round((this.pickedTotal() - this.saving()) * 100) / 100 : b.bundlePrice;
  });
  /** "1 more for 15% off": how far the next step is, or '' on the last one. */
  protected readonly nextStep = computed(() => {
    const next = (this.bundle()?.tiers ?? []).find((t) => t.quantity > this.filledCount());
    if (!next) return '';
    const more = next.quantity - this.filledCount();
    return `${more} more for ${next.percentOff}% off`;
  });

  /** One more box to fill, for a quantity discount. */
  protected addSlot(): void {
    if (this.selections().length >= this.maxItems()) return;
    this.selections.update((list) => [...list, null]);
    this.openSlot(this.selections().length - 1);
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) { this.loading.set(false); this.error.set('Bundle not found.'); return; }
    this.api.getBundle(id).pipe(catchError((err) => {
      this.error.set(err?.error?.message ?? 'Bundle not found.');
      this.loading.set(false);
      return of(null);
    })).subscribe((b) => {
      this.loading.set(false);
      if (b) {
        this.bundle.set(b);
        this.selections.set(Array.from({ length: b.slots }, () => null));
      }
    });
  }

  protected effectivePrice(p: BundleProduct): number {
    return p.discountPrice && p.discountPrice < p.price ? p.discountPrice : p.price;
  }

  protected discountPct(b: ApiBundleOffer): number {
    if (!b.compareAtPrice || b.compareAtPrice <= 0) return 0;
    return Math.round(((b.compareAtPrice - b.bundlePrice) / b.compareAtPrice) * 100);
  }

  /** A variant in full: "Black / Drop Shoulder / M", from whichever of those it has. */
  protected labelOf(v: ApiVariant): string {
    return variantLabel(v);
  }

  protected outOfStock(v: ApiVariant): boolean {
    return v.stockQuantity != null && v.stockQuantity <= 0;
  }

  // ── Modal flow ──────────────────────────────────────────────────────────
  protected openSlot(index: number): void {
    this.modalSlot.set(index);
    this.modalProduct.set(null);
    this.modalVariants.set([]);
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    this.modalOpen.set(false);
  }

  protected pickProduct(p: BundleProduct): void {
    this.modalProduct.set(p);
    this.loadingVariants.set(true);
    this.modalVariants.set([]);
    this.api.getProductVariants(p.id).pipe(catchError(() => of([] as ApiVariant[])))
      .subscribe((vs) => {
        this.loadingVariants.set(false);
        this.modalVariants.set(vs);
      });
  }

  protected chooseSize(variantId: number | null, size: string): void {
    const p = this.modalProduct();
    if (!p) return;
    const variant = this.modalVariants().find((v) => v.id === variantId) ?? null;
    const unitPrice = variant?.priceOverride ?? this.effectivePrice(p);
    const selection: SlotSelection = {
      productId: p.id, variantId, size, unitPrice, name: p.name, image: p.imageUrl,
    };
    this.selections.update((list) => {
      const next = [...list];
      next[this.modalSlot()] = selection;
      return next;
    });
    this.modalOpen.set(false);
  }

  protected removeSlot(index: number): void {
    this.selections.update((list) => {
      const next = [...list];
      // A box added beyond the fewest needed goes away; the others are emptied.
      if (this.byQuantity() && next.length > this.minItems()) next.splice(index, 1);
      else next[index] = null;
      return next;
    });
  }

  protected proceed(): void {
    const b = this.bundle();
    if (!b || !this.allFilled()) return;
    const lines = this.selections()
      .filter((s): s is SlotSelection => !!s)
      .map((s) => ({
        productId: s.productId,
        variantId: s.variantId,
        size: s.size,
        unitPrice: s.unitPrice,
        name: s.name,
        image: s.image,
      }));
    this.bundleCheckout.start({
      bundleId: b.id,
      bundleName: b.name,
      // For a quantity discount, what these items come to; the server works it out again at checkout.
      bundlePrice: this.payable(),
      lines,
    });
    void this.router.navigate(['/checkout']);
  }
}
