import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, of } from 'rxjs';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { PagerComponent } from '../../../shared/pager/pager.component';
import { CategoryService } from '../../../core/services/category.service';
import { CategoryResponse } from '../../../core/models/api.models';
import {
  CodeChangePreview,
  CodeChangeRequest,
  CodeChangeService,
  CodeMode,
} from '../../../core/services/code-change.service';
import { parseApiError } from '../../../core/utils/api-error.util';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NoticeService } from '../../../core/services/notice.service';

/**
 * Give a whole category its product codes: the same code for every product
 * (EP-1001), or a prefix with running numbers in name order (EP-001, EP-002…).
 * Nothing is saved until Apply, and then all together or not at all. Unticked
 * products keep the codes they have.
 */
@Component({
  selector: 'app-code-change',
  standalone: true,
  imports: [FormsModule, RouterLink, SidebarComponent, PagerComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <div>
            <h1 class="text-lg font-bold text-gray-900">Change product codes by category</h1>
            <p class="text-xs text-gray-400">Nothing is saved until you apply. One product at a time still works on its edit page.</p>
          </div>
          <a routerLink="/products" class="text-sm text-blue-600 underline">Back to products</a>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-5xl mx-auto space-y-5">

            <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-4">
              <div class="grid gap-4 sm:grid-cols-2">
                <label class="block text-sm font-medium text-gray-700">
                  Category
                  <select [ngModel]="categoryId()" (ngModelChange)="categoryId.set($event); clearPreview()"
                    class="mt-1 block w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm">
                    <option [ngValue]="null">Choose a category…</option>
                    @for (c of categories(); track c.id) {
                      <option [ngValue]="c.id">{{ c.name }} ({{ c.productCount }} products)</option>
                    }
                  </select>
                </label>
                <label class="block text-sm font-medium text-gray-700">
                  {{ mode() === 'SAME' ? 'Product code' : 'Prefix' }}
                  <input type="text" [attr.maxlength]="mode() === 'SAME' ? 40 : 20"
                    [ngModel]="code()" (ngModelChange)="code.set($event); clearPreview()"
                    [placeholder]="mode() === 'SAME' ? 'e.g., EP-1001' : 'e.g., EP'"
                    [attr.aria-label]="mode() === 'SAME' ? 'Product code' : 'Prefix'"
                    class="mt-1 block w-full rounded-lg border border-gray-200 px-3 py-2 font-mono text-sm" />
                  <span class="mt-1 block text-xs font-normal text-gray-400">
                    @if (mode() === 'SAME') {
                      Every ticked product in the category gets this code. Products may share a code.
                    } @else {
                      Products become {{ (code().trim() || 'EP') }}-001, {{ (code().trim() || 'EP') }}-002… in name order.
                      Letters, numbers, dashes and underscores.
                    }
                  </span>
                </label>
              </div>

              <fieldset class="rounded-lg border border-gray-100 p-4">
                <legend class="px-1 text-sm font-semibold text-gray-800">What each product gets</legend>
                <div class="space-y-1.5">
                  <label class="flex items-center gap-2 text-sm text-gray-700">
                    <input type="radio" name="codeMode" value="SAME" [ngModel]="mode()"
                      (ngModelChange)="mode.set($event); clearPreview()" />
                    The same code for every product (for example EP-1001)
                  </label>
                  <label class="flex items-center gap-2 text-sm text-gray-700">
                    <input type="radio" name="codeMode" value="NUMBERED" [ngModel]="mode()"
                      (ngModelChange)="mode.set($event); clearPreview()" />
                    A prefix with numbers, in name order (EP-001, EP-002, EP-003…)
                  </label>
                </div>
              </fieldset>

              @if (error()) {
                <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
              }
              <button type="button" (click)="showPreview()" [disabled]="!categoryId() || !code().trim() || loading()"
                class="rounded-lg bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-black disabled:opacity-50">
                {{ loading() ? 'Working it out…' : 'Show preview' }}
              </button>
            </section>

            @if (preview(); as p) {
              <section class="bg-white rounded-xl border border-gray-200 overflow-hidden">
                <div class="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-5 py-3">
                  <div class="text-sm text-gray-700">
                    <strong>{{ p.categoryName }}</strong>: {{ p.productCount }} products,
                    {{ p.changedCount }} would get a new code · <strong>{{ tickedCount() }}</strong> ticked
                  </div>
                  <div class="flex flex-wrap items-center gap-2 text-xs">
                    <input type="search" [ngModel]="search()" (ngModelChange)="onSearch($event)"
                      placeholder="Find a product by name or code…" aria-label="Find a product"
                      class="w-56 rounded-lg border border-gray-200 px-3 py-1.5 text-sm outline-none focus:ring-2 focus:ring-blue-300" />
                    <button type="button" (click)="tickAll(true)" class="rounded-lg border border-gray-200 px-2.5 py-1 hover:bg-gray-50">Tick all</button>
                    <button type="button" (click)="tickAll(false)" class="rounded-lg border border-gray-200 px-2.5 py-1 hover:bg-gray-50">Untick all</button>
                  </div>
                </div>
                <div class="overflow-x-auto">
                  <table class="w-full text-sm">
                    <thead>
                      <tr class="border-b border-gray-100 bg-gray-50 text-xs uppercase text-gray-400">
                        <th class="w-10 px-4 py-2.5"></th>
                        <th class="px-4 py-2.5 text-left">Product</th>
                        <th class="px-4 py-2.5 text-left">Code now</th>
                        <th class="px-4 py-2.5 text-left">New code</th>
                      </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-50">
                      @for (r of p.rows; track r.productId) {
                        <tr [class.bg-red-50]="r.problem" [class.opacity-60]="!isTicked(r.productId)">
                          <td class="px-4 py-2.5 text-center">
                            <input type="checkbox" [checked]="isTicked(r.productId)"
                              (change)="toggle(r.productId)" [attr.aria-label]="'Change ' + r.name" />
                          </td>
                          <td class="px-4 py-2.5">
                            <div class="flex items-center gap-2">
                              @if (r.imageUrl) { <img [src]="r.imageUrl" alt="" class="h-8 w-8 rounded object-cover" /> }
                              <div>
                                <p class="font-medium text-gray-800">{{ r.name }}</p>
                                @if (r.problem) { <p class="text-xs font-medium text-red-600">{{ r.problem }}</p> }
                                @else if (!r.changed) { <p class="text-xs text-gray-400">No change</p> }
                              </div>
                            </div>
                          </td>
                          <td class="px-4 py-2.5 font-mono text-xs text-gray-400">{{ r.oldCode }}</td>
                          <td class="px-4 py-2.5 font-mono text-sm font-semibold text-gray-900">{{ r.newCode }}</td>
                        </tr>
                      } @empty {
                        <tr><td colspan="4" class="px-4 py-6 text-center text-gray-400">
                          {{ search().trim() ? 'No product matches your search.' : 'This category has no products.' }}
                        </td></tr>
                      }
                    </tbody>
                  </table>
                </div>
                <app-pager [page]="p.page" [size]="p.size" [total]="p.totalRows" [disabled]="loading()"
                  (pageChange)="loadPage($event)" />
                <div class="flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 bg-gray-50 px-5 py-3">
                  <p class="text-xs text-gray-500">
                    Unticked products keep their code. Past orders keep the code they were placed with.
                    The Activity log will list every old and new code.
                  </p>
                  <button type="button" (click)="apply(p)" [disabled]="p.changedCount === 0 || applying()"
                    class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                    {{ applying() ? 'Saving…' : 'Apply to ' + p.changedCount + ' product' + (p.changedCount === 1 ? '' : 's') }}
                  </button>
                </div>
              </section>
            }
          </div>
        </main>
      </div>
    </div>
  `,
})
export class CodeChangeComponent implements OnInit {
  private readonly api = inject(CodeChangeService);
  private readonly categoryApi = inject(CategoryService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  readonly categories = signal<CategoryResponse[]>([]);
  readonly categoryId = signal<number | null>(null);
  /** The code itself, or the prefix when the numbered way is chosen. */
  readonly code = signal('');
  readonly mode = signal<CodeMode>('SAME');
  readonly preview = signal<CodeChangePreview | null>(null);
  /** The products left alone; the numbering depends on them, so the server is told. */
  private readonly excluded = signal<ReadonlySet<number>>(new Set());
  readonly search = signal('');
  private readonly search$ = new Subject<string>();
  private readonly ticks$ = new Subject<void>();
  private readonly pageSize = 20;
  private latestRequest = 0;
  readonly loading = signal(false);
  readonly applying = signal(false);
  readonly error = signal('');

  protected readonly tickedCount = computed(() => {
    const p = this.preview();
    return p ? p.productCount - this.excluded().size : 0;
  });

  ngOnInit(): void {
    const fromList = Number(this.route.snapshot.queryParamMap.get('categoryId'));
    if (fromList) this.categoryId.set(fromList);
    this.categoryApi.getCategories()
      .pipe(catchError(() => of([] as CategoryResponse[])))
      .subscribe((list) => this.categories.set([...list].sort((a, b) => a.name.localeCompare(b.name))));
    this.search$.pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => { if (this.preview()) this.loadPage(0); });
    // Ticking changes which numbers are given out, so the preview is worked out again.
    this.ticks$.pipe(debounceTime(350), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => { const p = this.preview(); if (p) this.loadPage(p.page); });
  }

  protected clearPreview(): void {
    this.latestRequest++;
    this.preview.set(null);
    this.excluded.set(new Set());
    this.search.set('');
    this.error.set('');
  }

  protected showPreview(): void {
    this.clearPreview();
    this.loadPage(0);
  }

  protected onSearch(q: string): void {
    this.search.set(q);
    this.search$.next(q);
  }

  protected loadPage(page: number): void {
    const request = this.request();
    if (!request) return;
    const call = ++this.latestRequest;
    this.loading.set(true);
    this.error.set('');
    this.api.preview(request, this.search().trim(), page, this.pageSize).subscribe({
      next: (p) => {
        if (call !== this.latestRequest) return;
        this.preview.set(p);
        this.loading.set(false);
      },
      error: (err) => {
        if (call !== this.latestRequest) return;
        this.error.set(parseApiError(err));
        this.loading.set(false);
      },
    });
  }

  protected async apply(p: CodeChangePreview): Promise<void> {
    const request = this.request();
    if (!request || p.changedCount === 0) return;
    const count = p.changedCount === 1 ? '1 product' : `${p.changedCount} products`;
    const what = request.mode === 'SAME' ? `the code ${request.code.trim()}` : 'new numbered codes';
    const ok = await this.confirmer.ask({
      title: `Give ${count} in ${p.categoryName} ${what}?`,
      message: 'The Activity log keeps the old ones.',
      confirmLabel: 'Change codes',
    });
    if (!ok) return;
    this.applying.set(true);
    this.error.set('');
    this.api.apply(request).subscribe({
      next: (result) => {
        this.applying.set(false);
        this.clearPreview();
        this.notices.success('Codes changed', request.mode === 'SAME'
          ? `${result.changedCount} product(s) in ${result.categoryName} now have the code `
            + `${request.code.trim()}. The Activity log lists every old and new code.`
          : `${result.changedCount} product(s) in ${result.categoryName} now have codes starting `
            + `${request.code.trim()}-. The Activity log lists every old and new code.`);
      },
      error: (err) => {
        this.applying.set(false);
        this.error.set(parseApiError(err));
      },
    });
  }

  protected isTicked(id: number): boolean {
    return !this.excluded().has(id);
  }

  protected toggle(id: number): void {
    this.excluded.update((set) => {
      const next = new Set(set);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
    this.ticks$.next();
  }

  /** Every page, not just the one on screen. */
  protected tickAll(on: boolean): void {
    const p = this.preview();
    if (!p) return;
    // "Untick all" needs every id, which only the server has; ask it for one big page.
    if (!on) {
      this.api.preview({ ...this.request()!, excludedProductIds: [] }, '', 0, 100)
        .pipe(catchError(() => of(null)))
        .subscribe((all) => {
          if (!all) return;
          this.excluded.set(new Set(all.rows.map((r) => r.productId)));
          this.loadPage(p.page);
        });
      return;
    }
    this.excluded.set(new Set());
    this.loadPage(p.page);
  }

  private request(): CodeChangeRequest | null {
    const categoryId = this.categoryId();
    const code = this.code().trim();
    if (categoryId == null || !code) return null;
    return { categoryId, mode: this.mode(), code, excludedProductIds: [...this.excluded()] };
  }
}
