import { Component, OnInit, inject, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SizeChartEntry, SizeChartService } from '../../core/services/size-chart.service';

/**
 * Chooses a size chart from the shop's library. The value is the chart's id, or
 * 0 for "none of its own" - what that means is said by `noneLabel` ("Same as
 * its category", "Same as the rest of the product", ...).
 *
 * `compact` leaves out the heading and the help line, for a cell in a table.
 */
@Component({
  selector: 'app-size-chart-picker',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    @if (!compact()) {
      <label [attr.for]="fieldId()" class="block text-sm font-medium text-gray-700">{{ label() }}</label>
      @if (help()) { <p class="text-xs text-gray-400 mb-1.5">{{ help() }}</p> }
    }
    <select [id]="fieldId()" [ngModel]="value()" (ngModelChange)="value.set(+$event)" [disabled]="disabled()"
      [attr.aria-label]="compact() ? label() : null" [attr.data-testid]="testId()"
      class="w-full rounded-lg border border-gray-200 bg-white text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-300 disabled:opacity-60"
      [class.px-3]="!compact()" [class.py-2]="!compact()" [class.px-2]="compact()" [class.py-1.5]="compact()" [class.text-xs]="compact()">
      <option [ngValue]="0">{{ noneLabel() }}</option>
      @for (c of charts(); track c.id) {
        <option [ngValue]="c.id">{{ c.name }}{{ c.isDefault ? ' (default)' : '' }}</option>
      }
    </select>
    @if (!compact() && loaded() && charts().length === 0) {
      <p class="mt-1 text-xs text-amber-600">There are no size charts yet. <a routerLink="/size-charts" class="underline">Add one on the Size Charts page.</a></p>
    }
  `,
})
export class SizeChartPickerComponent implements OnInit {
  private readonly service = inject(SizeChartService);

  /** The chosen chart's id; 0 for none of its own. */
  readonly value = model<number>(0);
  readonly label = input('Size chart');
  readonly help = input('');
  readonly noneLabel = input('Same as its category');
  readonly disabled = input(false);
  readonly compact = input(false);
  readonly fieldId = input('size-chart');
  readonly testId = input('size-chart');

  protected readonly charts = signal<SizeChartEntry[]>([]);
  protected readonly loaded = signal(false);

  ngOnInit(): void {
    this.service.list().subscribe({
      next: (all) => { this.charts.set(all); this.loaded.set(true); },
      error: () => this.loaded.set(true),
    });
  }
}
