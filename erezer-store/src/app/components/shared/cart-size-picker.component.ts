import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { ApiVariant } from '../../core/api.models';
import { CartActionsService } from '../../core/cart-actions.service';

/** A cart line, as far as choosing its size goes. */
export interface SizeLessLine {
  productId: string;
  variantId: number | null;
  size: string;
  quantity: number;
  customMeasurements?: string | null;
}

const SIZE_ORDER = ['XXS', 'XS', 'S', 'M', 'L', 'XL', 'XXL', '2XL', 'XXXL', '3XL', '4XL', '5XL'];

/**
 * "Choose your size" on a cart line that has none yet: a product added from a
 * list (the card's "Add to cart") that comes in sizes. The fits (or colours)
 * first when there are several, then the sizes; tapping one gives the line that
 * size. Shows nothing for a line that already has its size.
 */
@Component({
  selector: 'app-cart-size-picker',
  standalone: true,
  template: `
    @if (cart.needsSize(line())) {
      <div class="mt-2 rounded-lg border border-amber-300 bg-amber-50 p-2 dark:border-amber-700 dark:bg-amber-900/25" data-testid="cart-size-picker">
        <p class="text-[11px] font-semibold uppercase tracking-wide text-amber-900 dark:text-amber-200">
          Choose your {{ groups().length > 1 ? 'fit and size' : 'size' }}
        </p>
        @if (groups().length > 1) {
          <div class="mt-1.5 flex flex-wrap gap-1" role="group" aria-label="Choose fit">
            @for (g of groups(); track g.key) {
              <button type="button" (click)="group.set(g.key)" [attr.aria-pressed]="currentGroup() === g.key"
                [class]="currentGroup() === g.key
                  ? 'rounded-full border border-transparent bg-neutral-900 px-2.5 py-1 text-[11px] font-semibold text-white dark:bg-white dark:text-black'
                  : 'rounded-full border border-neutral-300 bg-white px-2.5 py-1 text-[11px] font-semibold text-neutral-700 hover:border-neutral-900 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-200'">{{ g.label }}</button>
            }
          </div>
        }
        <div class="mt-1.5 flex flex-wrap gap-1" role="group" aria-label="Choose size">
          @for (v of sizes(); track v.id) {
            <button type="button" (click)="choose(v)" [disabled]="busy() || soldOut(v)" data-testid="cart-size-option"
              class="min-w-8 rounded-md border border-neutral-300 bg-white px-2 py-1 text-xs font-semibold transition hover:border-neutral-900 hover:bg-neutral-900 hover:text-white disabled:cursor-not-allowed disabled:opacity-40 disabled:line-through disabled:hover:border-neutral-300 disabled:hover:bg-white disabled:hover:text-inherit dark:border-neutral-700 dark:bg-neutral-900 dark:hover:border-white dark:hover:bg-white dark:hover:text-black">{{ v.size || v.optionLabel }}</button>
          }
        </div>
      </div>
    }
  `,
})
export class CartSizePickerComponent {
  protected readonly cart = inject(CartActionsService);

  readonly line = input.required<SizeLessLine>();

  protected readonly busy = signal(false);
  /** The fit (or colour) picked; null until the shopper picks one. */
  protected readonly group = signal<string | null>(null);

  private readonly variants = computed(() =>
    (this.cart.variantsByProduct()[this.line().productId] ?? []).filter((v) => !!v.size || !!v.optionLabel));

  /** One button per combination of the product's own options and fit: "Drop Shoulder", "Black · Regular Fit". */
  protected readonly groups = computed(() => {
    const seen = new Map<string, string>();
    for (const v of this.variants()) {
      const key = groupOf(v);
      if (key && !seen.has(key)) seen.set(key, [v.optionLabel, v.fit ? v.fitLabel ?? v.fit : ''].filter(Boolean).join(' · '));
    }
    return [...seen].map(([key, label]) => ({ key, label }));
  });

  /** The fit shown: the one picked, else the first with something in stock. */
  protected readonly currentGroup = computed(() => {
    const picked = this.group();
    if (picked) return picked;
    const groups = this.groups();
    return groups.find((g) => this.variants().some((v) => groupOf(v) === g.key && !this.soldOut(v)))?.key ?? groups[0]?.key ?? null;
  });

  /** The sizes of that fit, S before M before L. */
  protected readonly sizes = computed(() => {
    const g = this.currentGroup();
    const rank = (s: string | null) => { const i = SIZE_ORDER.indexOf((s ?? '').toUpperCase()); return i < 0 ? SIZE_ORDER.length : i; };
    return this.variants().filter((v) => (this.groups().length > 1 ? groupOf(v) === g : true))
      .sort((a, b) => rank(a.size) - rank(b.size));
  });

  constructor() {
    effect(() => {
      const l = this.line();
      if (l.variantId == null && !l.customMeasurements) this.cart.ensureVariants(l.productId);
    });
  }

  protected soldOut(v: ApiVariant): boolean {
    return v.stockQuantity != null && v.stockQuantity <= 0;
  }

  protected choose(v: ApiVariant): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.cart.chooseSize(this.line(), v).subscribe(() => this.busy.set(false));
  }
}

function groupOf(v: ApiVariant): string {
  return v.optionKey || v.fit ? `${v.optionKey ?? ''}|${v.fit ?? ''}` : '';
}
