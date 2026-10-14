import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { FinderRule, SizeFinder, SizeFinderService } from '../../core/services/size-finder.service';
import { ALL_FITS } from '../products/shared/fit-sizes.component';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

/** A row of the table as it is being edited: height as feet and inches, like the customer enters it. */
interface EditRow {
  size: string;
  feet: number | null;
  inches: number | null;
  kg: number | null;
}

/**
 * The size finder ("Don't know your size? Click here" on a product page): a
 * table for each fit and one for products with no fit. The customer's height
 * gives one size, their weight another, and the larger is recommended.
 */
@Component({
  selector: 'app-size-finder-section',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="bg-white rounded-xl border border-gray-200" data-testid="finder-section">
      <div class="px-5 py-3 border-b border-gray-100 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-sm font-bold text-gray-800">Size finder</h2>
          <p class="text-xs text-gray-500">
            “Don't know your size?” on a product page. The customer enters their height and weight; each gives a size from the table of the fit
            they picked, and the larger of the two is recommended.
          </p>
        </div>
        @if (finder()) {
          <label class="flex items-center gap-2 text-sm font-medium text-gray-700">
            <input type="checkbox" [checked]="finder()!.enabled" [disabled]="!canEdit() || saving()" (change)="setShop($event)" data-testid="finder-shop"
              class="h-4 w-4 rounded border-gray-300 text-blue-600" />
            On in the shop
          </label>
        }
      </div>

      @if (error()) {
        <p class="mx-5 mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" data-testid="finder-error">{{ error() }}</p>
      }

      @if (!finder()) {
        <p class="px-5 py-4 text-sm text-gray-400">Loading…</p>
      } @else {
        <!-- one tab per table -->
        <div class="px-5 pt-3 flex flex-wrap gap-1" role="tablist" data-testid="finder-tabs">
          @for (t of tabs(); track t.scope) {
            <button type="button" role="tab" (click)="open(t.scope)" [attr.aria-selected]="scope() === t.scope" [attr.data-testid]="'finder-tab-' + t.scope"
              class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
              [class.bg-blue-600]="scope() === t.scope" [class.text-white]="scope() === t.scope"
              [class.text-gray-600]="scope() !== t.scope" [class.hover:bg-gray-100]="scope() !== t.scope">
              {{ t.label }}@if (!t.enabled) { <span class="ml-1 text-[10px] uppercase opacity-70">off</span> }
            </button>
          }
        </div>

        <div class="px-5 py-4 space-y-3" data-testid="finder-table">
          <label class="flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" [(ngModel)]="enabled" [disabled]="!canEdit()" data-testid="finder-enabled" class="h-4 w-4 rounded border-gray-300 text-blue-600" />
            Show the size finder {{ scope() === 'GENERAL' ? 'on products with no fit' : 'for ' + tabLabel() }}
          </label>

          <div class="overflow-x-auto">
            <table class="w-full max-w-2xl text-sm">
              <thead>
                <tr class="text-left text-xs text-gray-500">
                  <th class="py-1.5 pr-3 font-semibold">Size</th>
                  <th class="py-1.5 pr-3 font-semibold">Height up to</th>
                  <th class="py-1.5 pr-3 font-semibold">Weight up to (kg)</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (row of rows; track $index; let i = $index; let last = $last) {
                  <tr class="border-t border-gray-100" data-testid="finder-row">
                    <td class="py-1.5 pr-3">
                      <input [(ngModel)]="row.size" maxlength="10" [disabled]="!canEdit()" data-testid="finder-size"
                        class="w-20 rounded-lg border border-gray-200 px-2 py-1 text-sm font-semibold outline-none focus:ring-2 focus:ring-blue-300" />
                    </td>
                    <td class="py-1.5 pr-3 whitespace-nowrap">
                      <input type="number" min="3" max="8" [(ngModel)]="row.feet" [disabled]="!canEdit()" [placeholder]="last ? 'any' : 'ft'" data-testid="finder-feet"
                        class="w-14 rounded-lg border border-gray-200 px-2 py-1 text-right text-sm outline-none focus:ring-2 focus:ring-blue-300" /> ft
                      <input type="number" min="0" max="11" [(ngModel)]="row.inches" [disabled]="!canEdit()" [placeholder]="last ? '' : 'in'" data-testid="finder-inches"
                        class="ml-1 w-14 rounded-lg border border-gray-200 px-2 py-1 text-right text-sm outline-none focus:ring-2 focus:ring-blue-300" /> in
                    </td>
                    <td class="py-1.5 pr-3">
                      <input type="number" min="10" max="250" [(ngModel)]="row.kg" [disabled]="!canEdit()" [placeholder]="last ? 'any' : 'kg'" data-testid="finder-kg"
                        class="w-20 rounded-lg border border-gray-200 px-2 py-1 text-right text-sm outline-none focus:ring-2 focus:ring-blue-300" />
                    </td>
                    <td class="py-1.5">
                      @if (canEdit()) {
                        <button type="button" (click)="removeRow(i)" [disabled]="rows.length <= 1" class="text-xs text-red-600 hover:underline disabled:opacity-30" data-testid="finder-remove">Remove</button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <p class="text-xs text-gray-400">
            Smallest size first. A customer up to that height gets that size by height, and up to that weight gets it by weight.
            Leave the largest size's boxes empty for “anything above”.
          </p>

          @if (canEdit()) {
            <div class="flex flex-wrap items-center gap-2">
              <button type="button" (click)="addRow()" class="rounded-lg border border-gray-200 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50" data-testid="finder-add">+ Size</button>
              <button type="button" (click)="useStandard()" class="rounded-lg border border-gray-200 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50" data-testid="finder-standard">Use the standard rules</button>
              <button type="button" (click)="save()" [disabled]="saving()" data-testid="finder-save"
                class="rounded-lg bg-blue-600 px-4 py-1.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">{{ saving() ? 'Saving…' : 'Save' }}</button>
            </div>
          }
        </div>
      }
    </section>
  `,
})
export class SizeFinderSectionComponent implements OnInit {
  private readonly service = inject(SizeFinderService);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  readonly canEdit = input(false);

  protected readonly finder = signal<SizeFinder | null>(null);
  protected readonly scope = signal('GENERAL');
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected rows: EditRow[] = [];
  protected enabled = true;

  /** General first, then a tab for each fit in the shop's order. */
  protected readonly tabs = computed(() => {
    const f = this.finder();
    if (!f) return [];
    return [
      { scope: 'GENERAL', label: 'No fit (caps, bags)', enabled: f.general.enabled },
      ...ALL_FITS.filter((fit) => f.fits[fit.value]).map((fit) => ({ scope: fit.value, label: fit.label, enabled: f.fits[fit.value].enabled })),
    ];
  });
  protected readonly tabLabel = computed(() => this.tabs().find((t) => t.scope === this.scope())?.label ?? '');

  ngOnInit(): void {
    this.service.get().subscribe({
      next: (f) => { this.finder.set(f); this.open('GENERAL'); },
      error: (err: HttpErrorResponse) => this.error.set(parseApiError(err)),
    });
  }

  protected open(scope: string): void {
    const f = this.finder();
    if (!f) return;
    const table = scope === 'GENERAL' ? f.general : f.fits[scope];
    if (!table) return;
    this.scope.set(scope);
    this.enabled = table.enabled;
    this.rows = table.rules.map(toEditRow);
    this.error.set('');
  }

  protected addRow(): void {
    this.rows = [...this.rows, { size: '', feet: null, inches: null, kg: null }];
  }

  protected removeRow(index: number): void {
    this.rows = this.rows.filter((_, i) => i !== index);
  }

  protected async useStandard(): Promise<void> {
    const sure = await this.confirmer.ask({
      title: `Use the standard rules for ${this.tabLabel()}?`,
      message: 'The table goes back to the rules the shop started with (S up to 5 ft 4 in or 54 kg … 3XL for anything above). It is saved at once.',
      confirmLabel: 'Use the standard rules',
    });
    if (sure) this.send([], 'The standard rules are back');
  }

  protected save(): void {
    const rules: FinderRule[] = this.rows.map((r) => ({
      size: (r.size ?? '').trim(),
      heightUpToIn: r.feet == null || (r.feet as unknown) === '' ? null : r.feet * 12 + (r.inches ?? 0),
      weightUpToKg: r.kg == null || (r.kg as unknown) === '' ? null : Math.round(r.kg),
    }));
    this.send(rules, 'Size finder saved');
  }

  protected setShop(event: Event): void {
    const on = (event.target as HTMLInputElement).checked;
    this.saving.set(true);
    this.service.setShop(on).subscribe({
      next: (f) => { this.saving.set(false); this.finder.set(f); this.notices.success(on ? 'Size finder switched on' : 'Size finder switched off', on ? 'Customers can use it again.' : 'Product pages no longer offer it.'); },
      error: (err: HttpErrorResponse) => { this.saving.set(false); this.error.set(parseApiError(err)); },
    });
  }

  private send(rules: FinderRule[], done: string): void {
    const scope = this.scope();
    this.saving.set(true);
    this.error.set('');
    this.service.save(scope, this.enabled, rules).subscribe({
      next: (f) => {
        this.saving.set(false);
        this.finder.set(f);
        this.open(scope);
        this.notices.success(done, this.tabLabel());
      },
      error: (err: HttpErrorResponse) => { this.saving.set(false); this.error.set(parseApiError(err)); },
    });
  }
}

function toEditRow(rule: FinderRule): EditRow {
  const h = rule.heightUpToIn;
  return { size: rule.size, feet: h == null ? null : Math.floor(h / 12), inches: h == null ? null : h % 12, kg: rule.weightUpToKg };
}
