import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { FitService, ShopFit } from '../../core/services/fit.service';
import { SizeChartEntry } from '../../core/services/size-chart.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

/**
 * The shop's fits, on the Size Charts page: Drop Shoulder, Regular Fit, and any
 * the shop adds (Oversized). Each fit has the chart its sizes show. A fit can
 * be renamed, moved, hidden from new products, or removed when nothing uses it.
 */
@Component({
  selector: 'app-fits-section',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="bg-white rounded-xl border border-gray-200" data-testid="fits-section">
      <div class="px-5 py-3 border-b border-gray-100 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-sm font-bold text-gray-800">Fits</h2>
          <p class="text-xs text-gray-500">
            The cuts a product can come in. A product can have one, several or none; customers pick the fit, then the size, and see that fit's chart.
          </p>
        </div>
        @if (canEdit() && !adding()) {
          <button type="button" (click)="startAdd()" data-testid="fit-new"
            class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg whitespace-nowrap">+ New fit</button>
        }
      </div>

      @if (error()) {
        <p class="mx-5 mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" data-testid="fit-error">{{ error() }}</p>
      }

      @if (adding()) {
        <div class="mx-5 mt-3 flex flex-wrap items-end gap-3 rounded-lg border border-blue-200 bg-blue-50/50 p-3" data-testid="fit-add-form">
          <label class="text-xs font-medium text-gray-600">Name of the fit
            <input [(ngModel)]="newName" placeholder="e.g. Oversized" maxlength="60" data-testid="fit-new-name"
              class="mt-1 block w-56 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm outline-none focus:ring-2 focus:ring-blue-300" />
          </label>
          <label class="text-xs font-medium text-gray-600">Its size chart
            <select [(ngModel)]="newChart" data-testid="fit-new-chart"
              class="mt-1 block w-56 rounded-lg border border-gray-200 bg-white px-2 py-1.5 text-sm outline-none focus:ring-2 focus:ring-blue-300">
              <option [ngValue]="0">None: use each product's chart</option>
              @for (c of charts(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
            </select>
          </label>
          <button type="button" (click)="add()" [disabled]="saving() || !newName.trim()" data-testid="fit-new-save"
            class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">Add fit</button>
          <button type="button" (click)="adding.set(false)" class="px-3 py-1.5 text-sm font-medium text-gray-600 border border-gray-200 bg-white rounded-lg hover:bg-gray-50">Cancel</button>
        </div>
      }

      @if (loading()) {
        <p class="px-5 py-4 text-sm text-gray-400">Loading…</p>
      } @else {
        <ul class="mt-2">
          @for (f of fits(); track f.id; let i = $index) {
            <li class="flex flex-wrap items-center gap-3 px-5 py-2.5 border-t border-gray-100" data-testid="fit-row" [attr.data-code]="f.code"
              [class.opacity-60]="f.hidden">
              <span class="w-6 text-center text-xs font-semibold text-gray-400">{{ i + 1 }}</span>
              <div class="min-w-[12rem] flex-1">
                @if (renamingId() === f.id) {
                  <input [(ngModel)]="renameTo" maxlength="60" (keydown.enter)="saveRename(f)" (keydown.escape)="renamingId.set(null)" data-testid="fit-rename-input"
                    class="w-48 rounded-lg border border-blue-300 px-2 py-1 text-sm outline-none focus:ring-2 focus:ring-blue-300" />
                  <button type="button" (click)="saveRename(f)" class="ml-1 text-xs font-semibold text-blue-600" data-testid="fit-rename-save">Save</button>
                  <button type="button" (click)="renamingId.set(null)" class="ml-1 text-xs text-gray-500">Cancel</button>
                } @else {
                  <p class="text-sm font-semibold text-gray-800">
                    {{ f.name }}
                    @if (f.hidden) { <span class="ml-1 rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-gray-500" data-testid="fit-hidden-badge">Hidden</span> }
                  </p>
                }
                <p class="text-xs text-gray-500" data-testid="fit-usage">
                  {{ f.productCount ? 'Used by ' + f.productCount + (f.productCount === 1 ? ' product' : ' products') : 'Not used by any product yet' }}
                  · SKU letters {{ f.skuCode }}
                </p>
              </div>
              <label class="text-xs text-gray-500">Size chart
                <select [ngModel]="f.sizeChartId ?? 0" (ngModelChange)="setChart(f, $event)" [disabled]="!canEdit() || saving()" [attr.data-testid]="'fit-chart-' + f.code"
                  class="ml-2 rounded-lg border border-gray-200 bg-white px-2 py-1 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50">
                  <option [ngValue]="0">None: use each product's chart</option>
                  @for (c of charts(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
                </select>
              </label>
              @if (canEdit()) {
                <div class="flex items-center gap-1">
                  <button type="button" (click)="move(i, -1)" [disabled]="saving() || i === 0" [attr.aria-label]="'Move ' + f.name + ' up'" data-testid="fit-up"
                    class="w-7 h-7 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-30">↑</button>
                  <button type="button" (click)="move(i, 1)" [disabled]="saving() || i === fits().length - 1" [attr.aria-label]="'Move ' + f.name + ' down'" data-testid="fit-down"
                    class="w-7 h-7 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-30">↓</button>
                  <button type="button" (click)="startRename(f)" [disabled]="saving()" class="act-btn act-btn-edit" data-testid="fit-rename">Rename</button>
                  <button type="button" (click)="toggleHidden(f)" [disabled]="saving()" class="act-btn" data-testid="fit-hide">{{ f.hidden ? 'Show again' : 'Hide' }}</button>
                  <button type="button" (click)="remove(f)" [disabled]="saving()" class="act-btn act-btn-delete" data-testid="fit-delete">Remove</button>
                </div>
              }
            </li>
          }
        </ul>
        <p class="px-5 py-3 text-xs text-gray-400 border-t border-gray-100">
          A hidden fit is not offered for new products; products that already have it keep it. A fit can be removed only when no product uses it.
          A product can still choose a different chart for one of its fits on its own page.
        </p>
      }
    </section>
  `,
})
export class FitsSectionComponent implements OnInit {
  private readonly fitService = inject(FitService);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  readonly charts = input<SizeChartEntry[]>([]);
  readonly canEdit = input(false);
  /** Tells the page a fit's chart changed, so its chart cards can say where each chart is used. */
  readonly changed = output<void>();

  protected readonly fits = signal<ShopFit[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly adding = signal(false);
  protected readonly renamingId = signal<number | null>(null);
  protected newName = '';
  protected newChart = 0;
  protected renameTo = '';
  protected readonly offered = computed(() => this.fits().filter((f) => !f.hidden).length);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.fitService.adminList().subscribe({
      next: (all) => { this.fits.set(all); this.loading.set(false); },
      error: (err) => { this.error.set(parseApiError(err)); this.loading.set(false); },
    });
  }

  protected startAdd(): void {
    this.newName = '';
    this.newChart = 0;
    this.adding.set(true);
  }

  protected add(): void {
    const name = this.newName.trim();
    if (!name) return;
    this.run(this.fitService.create(name, this.newChart || null), `${name} added`, 'It is now offered on every product form.', () => this.adding.set(false));
  }

  protected startRename(f: ShopFit): void {
    this.renameTo = f.name;
    this.renamingId.set(f.id);
  }

  protected saveRename(f: ShopFit): void {
    const name = this.renameTo.trim();
    if (!name || name === f.name) { this.renamingId.set(null); return; }
    this.run(this.fitService.update(f.id, { name }), 'Fit renamed', `${f.name} is now ${name}. Its products keep their sizes.`, () => this.renamingId.set(null));
  }

  protected setChart(f: ShopFit, chartId: number): void {
    const chart = this.charts().find((c) => c.id === chartId);
    this.run(this.fitService.update(f.id, { sizeChartId: chartId || null }), 'Size chart saved',
      chart ? `${f.name} sizes now show ${chart.name}.` : `${f.name} sizes now show each product's own chart.`);
  }

  protected async toggleHidden(f: ShopFit): Promise<void> {
    if (!f.hidden) {
      const sure = await this.confirmer.ask({
        title: `Hide ${f.name}?`,
        message: `It will not be offered for new products. ${f.productCount ? `The ${f.productCount} product${f.productCount === 1 ? '' : 's'} that have it keep it.` : ''}`,
        confirmLabel: 'Hide',
      });
      if (!sure) return;
    }
    this.run(this.fitService.update(f.id, { hidden: !f.hidden }), f.hidden ? `${f.name} is offered again` : `${f.name} is hidden`, '');
  }

  protected async remove(f: ShopFit): Promise<void> {
    if (f.productCount > 0) {
      this.error.set(`${f.name} is used by ${f.productCount} product${f.productCount === 1 ? '' : 's'}, so it cannot be removed. Hide it instead: it is then not offered for new products.`);
      return;
    }
    const sure = await this.confirmer.ask({
      title: `Remove ${f.name}?`,
      message: 'No product uses it. It is taken off the list of fits.',
      confirmLabel: 'Remove',
      danger: true,
    });
    if (sure) this.run(this.fitService.remove(f.id), `${f.name} removed`, '');
  }

  protected move(index: number, step: -1 | 1): void {
    const ids = this.fits().map((f) => f.id);
    const to = index + step;
    if (to < 0 || to >= ids.length) return;
    [ids[index], ids[to]] = [ids[to], ids[index]];
    this.run(this.fitService.reorder(ids), 'Order of fits saved', 'Customers see the fits in this order.');
  }

  /** Saves, then reads the list again (which also updates every form in the panel). */
  private run(call: Observable<unknown>, title: string, message: string, then?: () => void): void {
    this.saving.set(true);
    this.error.set('');
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.notices.success(title, message);
        then?.();
        this.load();
        this.changed.emit();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(parseApiError(err));
      },
    });
  }
}
