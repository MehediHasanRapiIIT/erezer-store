import { Component, computed, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { salePercent } from '../../../core/utils/price.util';

/** How a sale discount is typed: a percentage off, or a fixed amount off in taka. */
export type DiscountMode = 'PERCENT' | 'AMOUNT';

/** The two fields the server takes; only the one in use is sent. */
export function discountFields(mode: DiscountMode, value: number | null):
  { discountPercentage?: number; discountAmount?: number } {
  if (value == null || !(value > 0)) return {};
  return mode === 'AMOUNT' ? { discountAmount: value } : { discountPercentage: value };
}

/**
 * Turns a stored sale price back into something to type. Only the sale price
 * is stored, so the form picks whichever reads naturally: a whole percentage
 * stays a percentage (15% off ৳1,000), anything else is shown as the amount off
 * (৳150 off ৳999, which is 15.015%).
 */
export function discountFromProduct(price: number, discountPrice: number | null | undefined):
  { mode: DiscountMode; value: number | null } {
  const percent = salePercent(price, discountPrice);
  if (percent == null) return { mode: 'PERCENT', value: null };
  if (Number.isInteger(Math.round(percent * 1e6) / 1e6)) return { mode: 'PERCENT', value: percent };
  return { mode: 'AMOUNT', value: Math.round((price - (discountPrice as number)) * 100) / 100 };
}

/** Why a discount can't be saved, in words, or null when it can. */
export function discountProblem(price: number | null, mode: DiscountMode, value: number | null): string | null {
  if (value == null || value === 0) return null;
  if (value < 0) return "A discount can't be negative.";
  if (mode === 'PERCENT' && value >= 100) return 'A percentage has to be less than 100.';
  if (mode === 'AMOUNT' && price != null && price > 0 && value >= price) {
    return 'The discount has to be less than the price.';
  }
  return null;
}

/**
 * The sale discount box on the product forms: a % / ৳ switch, the amount, and
 * one line saying what customers will pay — so nobody has to work out what
 * "15%" of ৳999 comes to.
 */
@Component({
  selector: 'app-discount-input',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div>
      <div class="mb-1.5 flex items-center justify-between">
        <label for="discount-value" class="block text-sm font-medium text-gray-700">Discount</label>
        <div class="flex rounded-lg border border-gray-200 bg-white p-0.5 text-xs font-semibold"
          role="radiogroup" aria-label="Give the discount as">
          <button type="button" role="radio" data-testid="discount-percent"
            [attr.aria-checked]="mode() === 'PERCENT'" [disabled]="disabled()"
            (click)="switchTo('PERCENT')"
            class="rounded-md px-3 py-1 transition-colors disabled:cursor-not-allowed"
            [class.bg-blue-600]="mode() === 'PERCENT'" [class.text-white]="mode() === 'PERCENT'"
            [class.text-gray-600]="mode() !== 'PERCENT'">%</button>
          <button type="button" role="radio" data-testid="discount-amount"
            [attr.aria-checked]="mode() === 'AMOUNT'" [disabled]="disabled()"
            (click)="switchTo('AMOUNT')"
            class="rounded-md px-3 py-1 transition-colors disabled:cursor-not-allowed"
            [class.bg-blue-600]="mode() === 'AMOUNT'" [class.text-white]="mode() === 'AMOUNT'"
            [class.text-gray-600]="mode() !== 'AMOUNT'">৳</button>
        </div>
      </div>

      <div class="relative">
        @if (mode() === 'AMOUNT') {
          <span class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm font-medium text-gray-400">৳</span>
        }
        <input id="discount-value" type="number" min="0" step="any" inputmode="decimal"
          [attr.max]="mode() === 'PERCENT' ? 99 : null"
          [ngModel]="value()" (ngModelChange)="value.set($event === '' || $event == null ? null : +$event)"
          [disabled]="disabled()" placeholder="0"
          class="w-full rounded-lg border py-2.5 text-sm outline-none placeholder-gray-300 focus:border-blue-400 focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50 disabled:text-gray-500"
          [class.pl-7]="mode() === 'AMOUNT'" [class.pl-3.5]="mode() === 'PERCENT'"
          [class.pr-8]="mode() === 'PERCENT'" [class.pr-3.5]="mode() === 'AMOUNT'"
          [class.border-red-400]="!!problem()" [class.border-gray-200]="!problem()" />
        @if (mode() === 'PERCENT') {
          <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm font-medium text-gray-400">%</span>
        }
      </div>

      @if (problem()) {
        <p class="mt-1 text-xs text-red-500" data-testid="discount-problem">{{ problem() }}</p>
      } @else {
        <p class="mt-1 text-xs text-gray-500" data-testid="discount-summary">{{ summary() }}</p>
      }
    </div>
  `,
})
export class DiscountInputComponent {
  /** The price the discount comes off; null while it hasn't been typed. */
  readonly price = input<number | null>(null);
  readonly disabled = input(false);
  readonly mode = model<DiscountMode>('PERCENT');
  readonly value = model<number | null>(null);

  readonly problem = computed(() => discountProblem(this.price(), this.mode(), this.value()));

  /** What customers pay, in words. */
  readonly summary = computed(() => {
    const price = this.price();
    const value = this.value();
    if (price == null || !(price > 0)) return 'Type the price to see what customers pay.';
    if (value == null || value === 0) return `No discount — customers pay ${taka(price)}.`;
    const off = this.mode() === 'AMOUNT' ? value : (price * value) / 100;
    const pays = Math.round((price - off) * 100) / 100;
    const how = this.mode() === 'AMOUNT' ? `${taka(value)} off` : `${trim(value)}% off`;
    return `Customers pay ${taka(pays)} (${how}).`;
  });

  /**
   * Switching keeps what customers pay the same: 15% of ৳1,000 becomes ৳150,
   * and ৳150 off ৳1,000 becomes 15%.
   */
  switchTo(next: DiscountMode): void {
    if (next === this.mode()) return;
    const price = this.price();
    const value = this.value();
    if (value != null && value > 0 && price != null && price > 0) {
      this.value.set(next === 'AMOUNT'
        ? Math.round(((price * value) / 100) * 100) / 100
        : Math.round((value / price) * 100 * 100) / 100);
    }
    this.mode.set(next);
  }
}

function taka(amount: number): string {
  return `৳${amount.toLocaleString(undefined, { maximumFractionDigits: 2 })}`;
}

function trim(n: number): string {
  return String(Math.round(n * 100) / 100);
}
