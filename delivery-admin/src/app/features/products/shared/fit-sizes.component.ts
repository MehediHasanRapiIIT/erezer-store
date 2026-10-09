import { Component, computed, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { VariantRequest } from '../../../core/services/variant.service';
import { SizeGridComponent, SizeRow, emptySizeRows, pickedSizes } from './size-grid.component';

/** The cut a garment comes in. A product may have one, both, or neither. */
export type Fit = 'DROP_SHOULDER' | 'REGULAR_FIT';

export const FITS: { value: Fit; label: string }[] = [
  { value: 'DROP_SHOULDER', label: 'Drop Shoulder' },
  { value: 'REGULAR_FIT', label: 'Regular Fit' },
];

export function fitLabel(fit: string | null | undefined): string {
  return FITS.find((f) => f.value === fit)?.label ?? '';
}

/**
 * Sizes grouped for showing a product's stock: under each combination of the
 * product's own options (Black, then White) and, inside it, under each fit,
 * Drop Shoulder first. A product with neither comes back as one group with an
 * empty label. `fit` is what tells the groups apart; it is the fit alone for a
 * product with no options, as it always was.
 */
export function groupByFit<T extends { fit?: string | null; fitLabel?: string | null; optionLabel?: string | null }>(
  sizes: T[] | null | undefined,
): { fit: string; label: string; sizes: T[] }[] {
  const groups: { fit: string; label: string; sizes: T[] }[] = [];
  for (const size of sizes ?? []) {
    const fit = size.fit ?? '';
    const key = size.optionLabel ? `${size.optionLabel}|${fit}` : fit;
    let group = groups.find((g) => g.fit === key);
    if (!group) {
      const fitName = size.fitLabel ?? fitLabel(fit);
      group = { fit: key, label: [size.optionLabel, fitName].filter(Boolean).join(' · '), sizes: [] };
      groups.push(group);
    }
    group.sizes.push(size);
  }
  return groups;
}

/** One fit on the form: offered or not, and its own price if it has one. */
export interface FitChoice {
  picked: boolean;
  /** This fit's price; null to use the product's price. */
  price: number | null;
}

/**
 * The fits a new product comes in and the sizes of each. With no fit ticked the
 * product has plain sizes (or none), as before fits existed.
 */
export interface FitSizes {
  fits: Record<Fit, FitChoice>;
  /** The sizes of each fit, used while that fit is ticked. */
  rows: Record<Fit, SizeRow[]>;
  /** The sizes of a product with no fit. */
  plain: SizeRow[];
}

/** A new product starts as Drop Shoulder; untick it for a cap or a bag. */
export function emptyFitSizes(): FitSizes {
  return {
    fits: { DROP_SHOULDER: { picked: true, price: null }, REGULAR_FIT: { picked: false, price: null } },
    rows: { DROP_SHOULDER: emptySizeRows(), REGULAR_FIT: emptySizeRows() },
    plain: emptySizeRows(),
  };
}

export function pickedFits(value: FitSizes): Fit[] {
  return FITS.map((f) => f.value).filter((f) => value.fits[f].picked);
}

/**
 * The sizes to save. In a fit, each size carries the fit; a size with no price
 * of its own takes the fit's price, and one with neither uses the product's.
 */
export function fitVariants(value: FitSizes): VariantRequest[] {
  const fits = pickedFits(value);
  if (fits.length === 0) return pickedSizes(value.plain);
  return fits.flatMap((fit) => pickedSizes(value.rows[fit]).map((size) => ({
    ...size,
    fit,
    priceOverride: size.priceOverride ?? positive(value.fits[fit].price),
  })));
}

/** How many sizes will be saved, across the fits. */
export function countFitSizes(value: FitSizes): number {
  return fitVariants(value).length;
}

/** What is wrong, in words, or '' when it can be saved. */
export function fitSizesProblem(value: FitSizes): string {
  for (const fit of pickedFits(value)) {
    if (!value.rows[fit].some((r) => r.picked)) {
      return `Tick the sizes ${fitLabel(fit)} comes in, or untick ${fitLabel(fit)}. A fit's stock is kept size by size.`;
    }
    const price = value.fits[fit].price;
    if (price != null && !(price > 0)) return `${fitLabel(fit)}'s price has to be more than 0, or left empty.`;
  }
  return '';
}

function positive(n: number | null): number | null {
  return n != null && n > 0 ? n : null;
}

/**
 * "Fit" and "Sizes" for a product being added: tick Drop Shoulder, Regular Fit,
 * both or neither; give a fit its own price if it has one; then tick the sizes
 * of each fit and type their stock.
 */
@Component({
  selector: 'app-fit-sizes',
  standalone: true,
  imports: [FormsModule, SizeGridComponent],
  template: `
    <div data-testid="fit-sizes">
      <!-- Fit -->
      <fieldset class="mb-4">
        <legend class="text-sm font-semibold text-gray-800">Fit</legend>
        <p class="mb-2 text-xs text-gray-500">
          Tick the fits this product comes in: one, both, or neither. Customers pick the fit, then the size.
          Each fit has its own stock.
        </p>
        <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
          @for (f of fits; track f.value) {
            <div class="rounded-lg border p-3 transition-colors"
              [class.border-blue-500]="value().fits[f.value].picked" [class.bg-blue-50]="value().fits[f.value].picked"
              [class.border-gray-200]="!value().fits[f.value].picked">
              <label class="flex cursor-pointer items-center gap-2 text-sm font-semibold text-gray-800">
                <input type="checkbox" [attr.data-testid]="'fit-' + f.value"
                  [checked]="value().fits[f.value].picked" [disabled]="disabled()"
                  (change)="toggleFit(f.value)"
                  class="h-4 w-4 cursor-pointer rounded border-gray-300 text-blue-600" />
                {{ f.label }}
              </label>
              @if (value().fits[f.value].picked) {
                <label class="mt-2 block text-xs font-medium text-gray-600">
                  {{ f.label }} price <span class="font-normal text-gray-400">(optional)</span>
                  <span class="relative mt-1 block">
                    <span class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-xs text-gray-400">৳</span>
                    <input type="number" min="0" step="any" [attr.data-testid]="'fit-price-' + f.value"
                      [placeholder]="basePrice() ? basePrice() + ' (product price)' : 'same as product price'"
                      [ngModel]="value().fits[f.value].price" (ngModelChange)="setPrice(f.value, $event)"
                      [disabled]="disabled() || !canSetPrice()"
                      class="w-full rounded-lg border border-gray-200 bg-white py-1.5 pl-6 pr-2 text-sm font-normal outline-none focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50 disabled:text-gray-400" />
                  </span>
                </label>
              }
            </div>
          }
        </div>
        @if (picked().length > 0) {
          <p class="mt-2 text-xs text-gray-400">
            Leave a fit's price empty to use the product's price. The product's sale discount comes off a
            fit's own price too: the same percentage, or the same amount.
          </p>
        } @else {
          <p class="mt-2 text-xs text-gray-400">No fit ticked: the product has plain sizes, or none (a cap, a bag).</p>
        }
        @if (!canSetPrice()) {
          <p class="mt-1 text-xs text-gray-400">A fit's own price needs the “Change prices” permission.</p>
        }
      </fieldset>

      <!-- Sizes: one grid per fit, or one plain grid -->
      @if (picked().length === 0) {
        <h3 class="text-sm font-semibold text-gray-800">Sizes</h3>
        <p class="mb-2 text-xs text-gray-500">{{ plainHint() }}</p>
        <app-size-grid [rows]="value().plain" (rowsChange)="setPlain($event)" [disabled]="disabled()"
          [canSetStock]="canSetStock()" [canSetPrice]="canSetPrice()" />
      } @else {
        @for (fit of picked(); track fit; let first = $first) {
          <div class="mt-3 rounded-lg border border-gray-200 p-3" [attr.data-testid]="'sizes-of-' + fit">
            <div class="mb-1 flex flex-wrap items-center justify-between gap-2">
              <h3 class="text-sm font-semibold text-gray-800">{{ label(fit) }} sizes</h3>
              @if (!first) {
                <button type="button" (click)="copyFrom(picked()[0], fit)" [disabled]="disabled()"
                  [attr.data-testid]="'copy-sizes-' + fit"
                  class="rounded-lg border border-gray-200 px-2.5 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-40">
                  Copy sizes and stock from {{ label(picked()[0]) }}
                </button>
              }
            </div>
            <p class="mb-2 text-xs text-gray-500">Tick the sizes {{ label(fit) }} comes in and type the stock of each.</p>
            <app-size-grid [rows]="value().rows[fit]" (rowsChange)="setRows(fit, $event)" [disabled]="disabled()"
              [canSetStock]="canSetStock()" [canSetPrice]="canSetPrice()" />
          </div>
        }
      }
    </div>
  `,
})
export class FitSizesComponent {
  readonly value = model<FitSizes>(emptyFitSizes());
  /** The product's price, shown in a fit's empty price box as what it will use. */
  readonly basePrice = input<number | null>(null);
  readonly canSetStock = input(true);
  readonly canSetPrice = input(true);
  readonly disabled = input(false);
  /** The line under "Sizes" when no fit is ticked. */
  readonly plainHint = input('Tick the sizes this product comes in. Leave them all unticked for a product with no sizes.');

  protected readonly fits = FITS;
  protected readonly picked = computed(() => pickedFits(this.value()));

  protected label(fit: Fit): string {
    return fitLabel(fit);
  }

  protected toggleFit(fit: Fit): void {
    const v = this.value();
    const turningOn = !v.fits[fit].picked;
    const rows = { ...v.rows };
    // A fit being ticked starts with the sizes the other fit already has ticked,
    // without their stock: the two usually come in the same sizes.
    if (turningOn && !v.rows[fit].some((r) => r.picked)) {
      const other = pickedFits(v)[0];
      const from = other ? v.rows[other] : v.plain;
      rows[fit] = v.rows[fit].map((r) => ({ ...r, picked: from.find((o) => o.size === r.size)?.picked ?? false }));
    }
    this.value.set({ ...v, rows, fits: { ...v.fits, [fit]: { ...v.fits[fit], picked: turningOn } } });
  }

  protected setPrice(fit: Fit, raw: unknown): void {
    const v = this.value();
    const price = raw === '' || raw == null ? null : +(raw as number);
    this.value.set({ ...v, fits: { ...v.fits, [fit]: { ...v.fits[fit], price: Number.isFinite(price as number) ? price : null } } });
  }

  protected setRows(fit: Fit, rows: SizeRow[]): void {
    const v = this.value();
    this.value.set({ ...v, rows: { ...v.rows, [fit]: rows } });
  }

  protected setPlain(rows: SizeRow[]): void {
    this.value.set({ ...this.value(), plain: rows });
  }

  protected copyFrom(source: Fit, target: Fit): void {
    const v = this.value();
    this.value.set({ ...v, rows: { ...v.rows, [target]: v.rows[source].map((r) => ({ ...r })) } });
  }
}
