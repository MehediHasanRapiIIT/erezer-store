import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SizeChartEntry, SizeChartService } from '../../../core/services/size-chart.service';
import { FITS } from '../shared/fit-sizes.component';

/** "Leave as it is": the ticked products keep what they have for this line. */
export const LEAVE = -1;

/** What the panel asks for: the general chart and each fit's chart (LEAVE, 0 for "follow the category", or a chart id). */
export interface BulkChartChoice {
  general: number;
  byFit: Record<string, number>;
}

/**
 * Size charts for the ticked products, from the Products page's blue bar: a
 * chart for products with no fit, and one for each fit. Every line starts on
 * "Leave as it is", so only what the admin changes is changed.
 */
@Component({
  selector: 'app-bulk-charts',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="mt-3 rounded-lg border border-blue-200 bg-white p-4" data-testid="bulk-charts-panel">
      <p class="text-sm font-semibold text-gray-800">Size charts for the {{ count() === 1 ? 'ticked product' : count() + ' ticked products' }}</p>
      <p class="mb-3 text-xs text-gray-500">Each fit shows its chart to customers who pick that fit. Lines left on “Leave as it is” are not changed.</p>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <label class="block text-xs font-medium text-gray-600">Products with no fit (caps, bags)
          <select [(ngModel)]="general" data-testid="bulk-chart-general"
            class="mt-1 block w-full rounded-lg border border-gray-200 bg-white px-2 py-1.5 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-300">
            <option [ngValue]="LEAVE">Leave as it is</option>
            <option [ngValue]="0">Same as their category (or the default)</option>
            @for (c of charts(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
          </select>
        </label>
        @for (fit of fits; track fit.value) {
          <label class="block text-xs font-medium text-gray-600">Chart for {{ fit.label }}
            <select [ngModel]="byFit[fit.value] ?? LEAVE" (ngModelChange)="byFit[fit.value] = $event" [attr.data-testid]="'bulk-chart-' + fit.value"
              class="mt-1 block w-full rounded-lg border border-gray-200 bg-white px-2 py-1.5 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-300">
              <option [ngValue]="LEAVE">Leave as it is</option>
              <option [ngValue]="0">Same as their category (or the fit's chart)</option>
              @for (c of charts(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
            </select>
          </label>
        }
      </div>
      <div class="mt-3 flex items-center gap-2">
        <button type="button" (click)="apply()" [disabled]="busy() || nothingChosen()" data-testid="bulk-charts-apply"
          class="rounded-lg bg-blue-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-40">Set charts</button>
        <button type="button" (click)="closed.emit()" class="rounded-lg border border-gray-200 px-3 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-50">Cancel</button>
      </div>
    </div>
  `,
})
export class BulkChartsComponent implements OnInit {
  private readonly sizeCharts = inject(SizeChartService);

  readonly count = input(0);
  readonly busy = input(false);
  readonly chosen = output<BulkChartChoice>();
  readonly closed = output<void>();

  protected readonly LEAVE = LEAVE;
  protected readonly fits = FITS;
  protected readonly charts = signal<SizeChartEntry[]>([]);
  protected general = LEAVE;
  protected byFit: Record<string, number> = {};

  ngOnInit(): void {
    this.sizeCharts.list().subscribe({ next: (all) => this.charts.set(all), error: () => {} });
  }

  protected nothingChosen(): boolean {
    return this.general === LEAVE && Object.values(this.byFit).every((v) => v === LEAVE);
  }

  protected apply(): void {
    this.chosen.emit({ general: this.general, byFit: { ...this.byFit } });
  }
}
