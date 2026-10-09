import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { SizeChart } from '../../core/services/store-settings.service';
import { SizeChartEntry, SizeChartService } from '../../core/services/size-chart.service';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

const NEW_CHART: SizeChart = { columns: ['Chest', 'Length'], rows: [] };

/**
 * The size chart library: as many charts as the shop needs, one of them the
 * default. A chart is chosen for a product on its form, for a whole category on
 * the category's form, and for many products at once from the Products list.
 */
@Component({
  selector: 'app-size-charts',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between gap-4 flex-shrink-0">
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-gray-900">Size Charts</h1>
            <span class="text-xs text-gray-400">{{ charts().length }} {{ charts().length === 1 ? 'chart' : 'charts' }}</span>
          </div>
          @if (canEdit() && !editing()) {
            <button type="button" (click)="startNew()" data-testid="chart-new"
              class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg whitespace-nowrap">+ New size chart</button>
          }
        </header>

        <main class="flex-1 overflow-y-auto p-6 space-y-5">
          <div class="rounded-xl border border-blue-100 bg-blue-50 px-4 py-3 text-xs text-blue-900">
            <p class="font-semibold">How a product gets its chart</p>
            <p class="mt-1">
              A product shows the chart chosen on the product itself. If none is chosen there, it shows the chart chosen on its
              category (or on the nearest category above it). If none is chosen there either, it shows the
              <span class="font-semibold">default</span> chart.
            </p>
            <p class="mt-1">Choose a chart on the product form, on the category form, in "Add several products", or for many products at once from the Products list.</p>
          </div>

          @if (error()) {
            <p class="rounded-lg border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700" data-testid="chart-error">{{ error() }}</p>
          }
          @if (!canEdit()) {
            <p class="text-xs text-gray-400">Changing charts needs the “Edit the size chart” permission.</p>
          }

          <!-- ── the editor ─────────────────────────────────────────────────── -->
          @if (editing()) {
            <section class="bg-white rounded-xl border border-blue-200 p-5 space-y-4" data-testid="chart-editor">
              <div class="flex flex-wrap items-end justify-between gap-3">
                <label class="block text-xs font-medium text-gray-600">
                  Name of the chart
                  <input [(ngModel)]="formName" placeholder="e.g. Men's T-Shirt" maxlength="120" data-testid="chart-name"
                    class="mt-1 block w-72 rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  <span class="mt-1 block text-[11px] font-normal text-gray-400">Only staff see the name. Customers see the measurements.</span>
                </label>
                <div class="flex gap-2">
                  <button type="button" (click)="addColumn()" data-testid="chart-add-column"
                    class="px-2.5 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50">+ Column</button>
                  <button type="button" (click)="addRow()" data-testid="chart-add-row"
                    class="px-2.5 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50">+ Size row</button>
                </div>
              </div>
              <p class="text-xs text-gray-500">A column is something you measure (Chest, Length, Sleeve). A row is a size (S, M, L). Each measurement holds both cm and inch.</p>

              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-gray-100 bg-gray-50 text-xs text-gray-500">
                      <th class="px-2 py-2 text-left">Size</th>
                      @for (col of form.columns; track $index) {
                        <th class="px-2 py-2 text-left">
                          <div class="flex items-center gap-1">
                            <input [(ngModel)]="form.columns[$index]" [attr.aria-label]="'Column ' + ($index + 1)"
                              class="w-28 rounded border border-gray-200 px-2 py-1 text-xs" />
                            <button type="button" (click)="removeColumn($index)" class="act-btn-icon" title="Remove column">
                              <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/></svg>
                            </button>
                          </div>
                        </th>
                      }
                      <th class="px-2 py-2"></th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-50">
                    @for (row of form.rows; track $index; let ri = $index) {
                      <tr data-testid="chart-row">
                        <td class="px-2 py-2">
                          <input [(ngModel)]="row.size" placeholder="M" [attr.aria-label]="'Size of row ' + (ri + 1)"
                            class="w-16 rounded border border-gray-200 px-2 py-1 text-xs font-semibold" />
                        </td>
                        @for (col of form.columns; track $index; let ci = $index) {
                          <td class="px-2 py-2">
                            <div class="flex items-center gap-1">
                              <input type="number" step="0.1" [(ngModel)]="row.cells[ci].cm" placeholder="cm"
                                [attr.aria-label]="col + ' of ' + (row.size || 'row ' + (ri + 1)) + ' in cm'"
                                class="w-16 rounded border border-gray-200 px-2 py-1 text-xs" />
                              <span class="text-[10px] text-gray-400">cm</span>
                              <input type="number" step="0.1" [(ngModel)]="row.cells[ci].inch" placeholder="in"
                                [attr.aria-label]="col + ' of ' + (row.size || 'row ' + (ri + 1)) + ' in inches'"
                                class="w-16 rounded border border-gray-200 px-2 py-1 text-xs" />
                              <span class="text-[10px] text-gray-400">in</span>
                            </div>
                          </td>
                        }
                        <td class="px-2 py-2 text-right">
                          <button type="button" (click)="removeRow(ri)" class="act-btn act-btn-delete" title="Remove row">Remove</button>
                        </td>
                      </tr>
                    } @empty {
                      <tr><td [attr.colspan]="form.columns.length + 2" class="px-2 py-4 text-center text-gray-400 text-xs">No size rows yet. Press “+ Size row”.</td></tr>
                    }
                  </tbody>
                </table>
              </div>

              <div class="flex items-center justify-end gap-2 border-t border-gray-100 pt-4">
                <button type="button" (click)="cancel()" class="px-3 py-1.5 text-sm font-medium text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50">Cancel</button>
                <button type="button" (click)="save()" [disabled]="saving()" data-testid="chart-save"
                  class="px-4 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                  {{ saving() ? 'Saving…' : (editingId() ? 'Save changes' : 'Save chart') }}
                </button>
              </div>
            </section>
          }

          <!-- ── the library ────────────────────────────────────────────────── -->
          @if (loading()) {
            <p class="py-10 text-center text-sm text-gray-400">Loading…</p>
          } @else if (charts().length === 0) {
            <p class="rounded-xl border border-dashed border-gray-200 bg-white py-12 text-center text-sm text-gray-500" data-testid="charts-empty">
              No size charts yet. Add the first one with “+ New size chart”.
            </p>
          } @else {
            <div class="grid gap-4 xl:grid-cols-2">
              @for (c of charts(); track c.id) {
                <section class="bg-white rounded-xl border p-4" [class.border-blue-300]="editingId() === c.id" [class.border-gray-200]="editingId() !== c.id"
                  data-testid="chart-card" [attr.data-chart-id]="c.id">
                  <div class="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <h2 class="text-sm font-bold text-gray-900">
                        {{ c.name }}
                        @if (c.isDefault) {
                          <span class="ml-1 rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-emerald-700" data-testid="chart-default-badge">Default</span>
                        }
                      </h2>
                      <p class="mt-0.5 text-xs text-gray-500" data-testid="chart-usage">{{ usage(c) }}</p>
                    </div>
                    @if (canEdit()) {
                      <div class="flex flex-wrap gap-1.5">
                        @if (!c.isDefault) {
                          <button type="button" (click)="makeDefault(c)" class="act-btn" data-testid="chart-make-default">Make default</button>
                        }
                        <button type="button" (click)="startEdit(c)" class="act-btn act-btn-edit" data-testid="chart-edit">Edit</button>
                        <button type="button" (click)="startCopy(c)" class="act-btn" data-testid="chart-copy">Copy</button>
                        <button type="button" (click)="remove(c)" class="act-btn act-btn-delete" data-testid="chart-delete">Delete</button>
                      </div>
                    }
                  </div>
                  <div class="mt-3 overflow-x-auto">
                    <table class="w-full text-xs">
                      <thead>
                        <tr class="border-b border-gray-100 text-gray-500">
                          <th class="py-1.5 pr-3 text-left font-semibold">Size</th>
                          @for (col of c.chart.columns; track $index) { <th class="py-1.5 pr-3 text-left font-semibold">{{ col }}</th> }
                        </tr>
                      </thead>
                      <tbody class="divide-y divide-gray-50">
                        @for (row of c.chart.rows; track $index) {
                          <tr>
                            <td class="py-1.5 pr-3 font-semibold text-gray-800">{{ row.size }}</td>
                            @for (cell of row.cells; track $index) {
                              <td class="py-1.5 pr-3 text-gray-600">{{ cell.cm ?? '–' }} cm <span class="text-gray-400">/ {{ cell.inch ?? '–' }} in</span></td>
                            }
                          </tr>
                        } @empty {
                          <tr><td [attr.colspan]="c.chart.columns.length + 1" class="py-3 text-center text-gray-400">No sizes in this chart yet.</td></tr>
                        }
                      </tbody>
                    </table>
                  </div>
                </section>
              }
            </div>
          }
        </main>
      </div>
    </div>
  `,
})
export class SizeChartsComponent implements OnInit {
  private readonly service = inject(SizeChartService);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);
  protected readonly perms = inject(PermissionService);

  charts = signal<SizeChartEntry[]>([]);
  loading = signal(true);
  saving = signal(false);
  error = signal('');
  /** The editor is open: for a new chart (editingId null) or an existing one. */
  editing = signal(false);
  editingId = signal<number | null>(null);
  formName = '';
  form: SizeChart = structuredClone(NEW_CHART);

  ngOnInit(): void {
    this.load();
  }

  canEdit(): boolean {
    return this.perms.can('settings.sizechart');
  }

  private load(): void {
    this.service.list().subscribe({
      next: (all) => { this.charts.set(all); this.loading.set(false); },
      error: (err) => { this.error.set(parseApiError(err)); this.loading.set(false); },
    });
  }

  /** "Used by 4 products and 1 category" for a chart's card. */
  usage(c: SizeChartEntry): string {
    const parts: string[] = [];
    if (c.productCount) parts.push(`${c.productCount} product${c.productCount === 1 ? '' : 's'}`);
    if (c.categoryCount) parts.push(`${c.categoryCount} categor${c.categoryCount === 1 ? 'y' : 'ies'}`);
    const chosen = parts.length ? `Chosen on ${parts.join(' and ')}` : 'Not chosen on any product or category';
    return c.isDefault ? `${chosen}. Shown wherever no other chart is chosen.` : `${chosen}.`;
  }

  // ── the editor ─────────────────────────────────────────────────────────────

  startNew(): void {
    this.open(null, '', structuredClone(NEW_CHART));
  }

  startEdit(c: SizeChartEntry): void {
    this.open(c.id, c.name, structuredClone(c.chart));
  }

  /** A new chart that starts as a copy of this one. */
  startCopy(c: SizeChartEntry): void {
    this.open(null, `${c.name} (copy)`, structuredClone(c.chart));
  }

  private open(id: number | null, name: string, chart: SizeChart): void {
    // Every row carries a cell for every column, whatever was stored.
    chart.columns = chart.columns ?? [];
    chart.rows = (chart.rows ?? []).map((r) => ({
      size: r.size ?? '',
      cells: chart.columns.map((_, i) => r.cells?.[i] ?? { cm: null, inch: null }),
    }));
    this.editingId.set(id);
    this.formName = name;
    this.form = chart;
    this.error.set('');
    this.editing.set(true);
  }

  cancel(): void {
    this.editing.set(false);
    this.editingId.set(null);
  }

  addColumn(): void {
    this.form.columns.push('New');
    this.form.rows.forEach((r) => r.cells.push({ cm: null, inch: null }));
  }

  removeColumn(index: number): void {
    this.form.columns.splice(index, 1);
    this.form.rows.forEach((r) => r.cells.splice(index, 1));
  }

  addRow(): void {
    this.form.rows.push({ size: '', cells: this.form.columns.map(() => ({ cm: null, inch: null })) });
  }

  removeRow(index: number): void {
    this.form.rows.splice(index, 1);
  }

  save(): void {
    const name = this.formName.trim();
    if (!name) { this.error.set('Give the chart a name.'); return; }
    if (this.form.columns.length === 0) { this.error.set('A chart needs at least one column, such as Chest.'); return; }
    if (this.form.columns.some((c) => !c.trim())) { this.error.set('Every column needs a name.'); return; }
    if (this.form.rows.some((r) => !r.size.trim())) { this.error.set('Every row needs a size, such as M.'); return; }
    const chart: SizeChart = {
      columns: this.form.columns.map((c) => c.trim()),
      rows: this.form.rows.map((r) => ({ size: r.size.trim(), cells: r.cells })),
    };
    const id = this.editingId();
    this.saving.set(true);
    this.error.set('');
    (id ? this.service.update(id, name, chart) : this.service.create(name, chart)).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.editing.set(false);
        this.editingId.set(null);
        this.notices.success(id ? 'Size chart saved' : 'Size chart added', saved.name);
        this.load();
      },
      error: (err) => { this.saving.set(false); this.error.set(parseApiError(err)); },
    });
  }

  // ── the library ────────────────────────────────────────────────────────────

  makeDefault(c: SizeChartEntry): void {
    this.service.setDefault(c.id).subscribe({
      next: () => { this.notices.success('Default size chart changed', c.name); this.load(); },
      error: (err) => this.error.set(parseApiError(err)),
    });
  }

  async remove(c: SizeChartEntry): Promise<void> {
    const using = c.productCount + c.categoryCount;
    const ok = await this.confirmer.ask({
      title: `Delete the size chart “${c.name}”?`,
      message: using > 0
        ? `${this.usage(c)} They will show their category's chart or the default chart instead. This cannot be undone.`
        : 'No product or category has chosen it. This cannot be undone.',
      confirmLabel: 'Yes, delete it',
      danger: true,
    });
    if (!ok) return;
    this.service.delete(c.id).subscribe({
      next: () => {
        if (this.editingId() === c.id) this.cancel();
        this.notices.success('Size chart deleted', c.name);
        this.load();
      },
      error: (err) => this.error.set(parseApiError(err)),
    });
  }
}
