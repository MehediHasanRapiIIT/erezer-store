import { Component, computed, input, output } from '@angular/core';

/**
 * "Showing 21–40 of 312" with Previous / Next, for any list the server pages.
 * Pages are zero-based, like the backend's. Shows nothing for an empty list,
 * and no buttons when everything fits on one page.
 */
@Component({
  selector: 'app-pager',
  standalone: true,
  template: `
    @if (total() > 0) {
      <div class="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-xs text-gray-500">
        <span class="tabular-nums">Showing {{ from() }}–{{ to() }} of {{ total() }}</span>
        @if (pages() > 1) {
          <div class="flex items-center gap-2">
            <button type="button" class="act-btn" (click)="go(page() - 1)" [disabled]="page() === 0 || disabled()">
              Previous
            </button>
            <span class="tabular-nums">Page {{ page() + 1 }} of {{ pages() }}</span>
            <button type="button" class="act-btn" (click)="go(page() + 1)" [disabled]="page() >= pages() - 1 || disabled()">
              Next
            </button>
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

  protected readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / Math.max(this.size(), 1))));
  protected readonly from = computed(() => this.page() * this.size() + 1);
  protected readonly to = computed(() => Math.min((this.page() + 1) * this.size(), this.total()));

  protected go(page: number): void {
    if (page >= 0 && page < this.pages()) this.pageChange.emit(page);
  }
}
