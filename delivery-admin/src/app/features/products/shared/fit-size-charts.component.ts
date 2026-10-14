import { Component, computed, input, model } from '@angular/core';
import { SizeChartPickerComponent } from '../../../shared/size-chart-picker/size-chart-picker.component';
import { ALL_FITS } from './fit-sizes.component';

/**
 * "Size chart for each fit" on a product form: one line per fit the product
 * comes in. Each fit shows its own chart from the Fits list (Size Charts page)
 * unless a different one is chosen here, for this product only.
 */
@Component({
  selector: 'app-fit-size-charts',
  standalone: true,
  imports: [SizeChartPickerComponent],
  template: `
    @if (lines().length > 0) {
      <div class="space-y-3" data-testid="fit-size-charts">
        <p class="text-xs text-gray-500">
          @if (forCategory()) {
            A chart for each fit, for every product in this category and in the categories under it. Customers see the chart of the fit they pick.
            A product can still choose its own.
          } @else {
            Each fit shows its chart (from its category, or from the Fits list on the Size Charts page). Choose a different one here only when this product measures differently.
          }
        </p>
        @for (fit of lines(); track fit.value) {
          <app-size-chart-picker [value]="value()[fit.value] ?? 0" (valueChange)="set(fit.value, $event)"
            [label]="'Chart for ' + fit.label" [fieldId]="'fit-chart-' + fit.value" [testId]="'fit-chart-' + fit.value"
            [disabled]="disabled()"
            [noneLabel]="noneLabel(fit)" />
        }
      </div>
    }
  `,
})
export class FitSizeChartsComponent {
  /** The fits to show a line for, by code. */
  readonly fits = input<string[]>([]);
  /** True on a category's form: the lines are for everything in the category. */
  readonly forCategory = input(false);
  /** The product's own chart for a fit, by fit; 0 (or missing) for "the fit's chart". */
  readonly value = model<Record<string, number>>({});
  readonly disabled = input(false);

  protected readonly lines = computed(() => {
    const wanted = this.fits();
    return ALL_FITS.filter((f) => wanted.includes(f.value));
  });

  /** What "none of its own" means on this line. */
  protected noneLabel(fit: { label: string; sizeChartName?: string | null }): string {
    const fitsChart = fit.sizeChartName ? `the fit's chart: ${fit.sizeChartName}` : 'the general chart';
    return this.forCategory()
      ? `Same as the category above (or ${fitsChart})`
      : `Same as its category (or ${fitsChart})`;
  }

  protected set(fit: string, chartId: number): void {
    this.value.set({ ...this.value(), [fit]: chartId ?? 0 });
  }
}
