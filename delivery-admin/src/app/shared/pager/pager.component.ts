import { Component, computed, input, output, signal } from '@angular/core';

/**
 * "Showing 21–40 of 312", the page numbers around the one on screen (with the
 * first and last always there), ‹ ›, and a box to jump to any page. For every
 * list the server pages. Pages are zero-based, like the backend's. Shows nothing
 * for an empty list, and no buttons when everything fits on one page.
 */
@Component({
  selector: 'app-pager',
  standalone: true,
  template: `
    @if (total() > 0) {
      <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3 text-sm text-gray-500" data-testid="pager">
        <span class="tabular-nums">Showing <span class="font-semibold text-gray-700">{{ from() }}–{{ to() }}</span> of <span class="font-semibold text-gray-700">{{ total() }}</span></span>
        @if (pages() > 1) {
          <div class="flex flex-wrap items-center gap-1">
            <button type="button" (click)="go(page() - 1)" [disabled]="page() === 0 || disabled()" aria-label="Previous page"
              class="w-8 h-8 flex items-center justify-center border border-gray-200 rounded-lg text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">‹</button>
            @for (p of numbers(); track $index) {
              @if (p === -1) {
                <span class="px-1 text-gray-400">…</span>
              } @else {
                <button type="button" (click)="go(p)" [disabled]="disabled()" [attr.aria-current]="p === page() ? 'page' : null"
                  data-testid="pager-number"
                  class="min-w-8 h-8 px-2 rounded-lg font-medium tabular-nums transition-colors disabled:cursor-not-allowed"
                  [class.bg-blue-600]="p === page()" [class.text-white]="p === page()"
                  [class.border]="p !== page()" [class.border-gray-200]="p !== page()"
                  [class.text-gray-600]="p !== page()" [class.hover:bg-gray-50]="p !== page()">{{ p + 1 }}</button>
              }
            }
            <button type="button" (click)="go(page() + 1)" [disabled]="page() >= pages() - 1 || disabled()" aria-label="Next page"
              class="w-8 h-8 flex items-center justify-center border border-gray-200 rounded-lg text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">›</button>
            @if (pages() > numbers().length) {
              <form class="ml-2 flex items-center gap-1" novalidate (submit)="jump($event)">
                <label class="text-xs text-gray-500" for="pager-jump">Go to</label>
                <input id="pager-jump" type="number" min="1" [max]="pages()" inputmode="numeric" [value]="typed()"
                  (input)="typed.set($any($event.target).value)" [placeholder]="'1–' + pages()" data-testid="pager-jump"
                  class="w-16 h-8 rounded-lg border border-gray-200 bg-white px-2 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-300" />
                <button type="submit" [disabled]="disabled()" data-testid="pager-go"
                  class="h-8 px-2.5 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 disabled:opacity-40">Go</button>
              </form>
            }
          </div>
        }
      </div>
    }
  `,
})
export class PagerComponent {
  /** Zero-based page on screen. */
  readonly page = input.required<number>();
  /** Rows per page. */
  readonly size = input.required<number>();
  /** Rows in the whole list, as counted by the server. */
  readonly total = input.required<number>();
  /** Disable the buttons, e.g. while a page is loading. */
  readonly disabled = input(false);
  /** The zero-based page the person asked for. */
  readonly pageChange = output<number>();

  protected readonly typed = signal('');
  protected readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / Math.max(this.size(), 1))));
  protected readonly from = computed(() => this.page() * this.size() + 1);
  protected readonly to = computed(() => Math.min((this.page() + 1) * this.size(), this.total()));
  /** The buttons to show, -1 for a gap: see {@link pageNumbers}. */
  protected readonly numbers = computed(() => pageNumbers(this.page(), this.pages()));

  protected go(page: number): void {
    if (page >= 0 && page < this.pages() && page !== this.page()) this.pageChange.emit(page);
  }

  /** "Go to": the typed page (counted from 1), kept within the list. */
  protected jump(event: Event): void {
    event.preventDefault();
    const wanted = Math.round(Number(this.typed()));
    if (!Number.isFinite(wanted) || wanted < 1) return;
    this.go(Math.min(wanted, this.pages()) - 1);
    this.typed.set('');
  }
}

/**
 * Zero-based page numbers to show, -1 for a gap: two either side of the current
 * page, plus the first and last. Up to 9 pages: all of them. Near either end the
 * run of numbers stays the same length, so the buttons don't jump about.
 */
export function pageNumbers(current: number, total: number, around = 2): number[] {
  if (total <= around * 2 + 5) return Array.from({ length: total }, (_, i) => i);
  const span = around * 2 + 1;
  let start = Math.max(1, current - around);
  let end = Math.min(total - 2, current + around);
  if (current - around <= 1) end = Math.min(total - 2, span);
  if (current + around >= total - 2) start = Math.max(1, total - 1 - span);
  const out = [0];
  if (start > 1) out.push(-1);
  for (let i = start; i <= end; i++) out.push(i);
  if (end < total - 2) out.push(-1);
  out.push(total - 1);
  return out;
}
