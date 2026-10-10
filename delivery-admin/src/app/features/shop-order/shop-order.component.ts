import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { ShopOrderItem, ShopOrderService } from '../../core/services/shop-order.service';
import { ProductService } from '../../core/services/product.service';
import { CategoryService } from '../../core/services/category.service';
import { CategoryResponse, ProductResponse } from '../../core/models/api.models';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

const PAGE_SIZE = 20;
/** The most products that can be ranked; the server refuses more. */
const MAX_RANKED = 500;

/**
 * Shop Order: which products come first on the Shop page and the category pages.
 *
 * The top list is the ranking itself, first to last: drag a row, type a place,
 * or use the arrows. The bottom list finds products (by name, SKU or code, or
 * within a category) to add to it. Every change is saved at once.
 */
@Component({
  selector: 'app-shop-order',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between gap-4 flex-shrink-0">
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-gray-900">Shop Order</h1>
            <span class="text-xs text-gray-400" data-testid="ranked-count">{{ ranked().length }} ranked</span>
            @if (saving()) { <span class="text-xs text-blue-600">Saving…</span> }
          </div>
          @if (canRank() && ranked().length > 0) {
            <button type="button" (click)="clearAll()" [disabled]="saving()" data-testid="rank-clear"
              class="px-3 py-1.5 text-sm font-medium text-gray-600 border border-gray-200 hover:bg-gray-50 rounded-lg disabled:opacity-50">Remove all</button>
          }
        </header>

        <main class="flex-1 overflow-y-auto p-6 space-y-5">
          <div class="rounded-xl border border-blue-100 bg-blue-50 px-4 py-3 text-xs text-blue-900">
            <p class="font-semibold">How the shop orders its products</p>
            <p class="mt-1">
              The products ranked here come first, in this order, on the Shop page and on every category page (a category shows
              its own products in the same order). All other products follow them, as before.
            </p>
            <p class="mt-1">
              A ranked product that is out of stock is shown after the others until it is back in stock; it keeps its place here.
              When a customer sorts by price, the price decides.
            </p>
          </div>

          @if (error()) {
            <p class="rounded-lg border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700" data-testid="rank-error">{{ error() }}</p>
          }
          @if (!canRank()) {
            <p class="text-xs text-gray-400">Changing the order needs the “Arrange products in the shop” permission.</p>
          }

          <!-- ── the ranking ──────────────────────────────────────────────── -->
          <section class="bg-white rounded-xl border border-gray-200" data-testid="ranked-list">
            <div class="px-5 py-3 border-b border-gray-100">
              <h2 class="text-sm font-bold text-gray-800">Ranked products</h2>
              <p class="text-xs text-gray-500">First to last. Drag a row, type its place, or use the arrows.</p>
            </div>
            @if (loading()) {
              <p class="px-5 py-6 text-sm text-gray-400">Loading…</p>
            } @else if (ranked().length === 0) {
              <p class="px-5 py-6 text-sm text-gray-500" data-testid="ranked-empty">
                No product is ranked yet, so the shop shows them as it always has. Find a product below and choose “Add to ranking”.
              </p>
            } @else {
              <ul>
                @for (item of ranked(); track item.id; let i = $index) {
                  <li class="flex items-center gap-3 px-5 py-2.5 border-b border-gray-100 last:border-b-0"
                    [class.bg-blue-50]="dragOver() === i && dragFrom() !== null && dragFrom() !== i"
                    [class.opacity-50]="dragFrom() === i"
                    [attr.draggable]="canRank() && !saving()" data-testid="ranked-row" [attr.data-id]="item.id"
                    (dragstart)="onDragStart(i, $event)" (dragover)="onDragOver(i, $event)" (drop)="onDrop(i, $event)" (dragend)="onDragEnd()">
                    <span class="text-gray-300 select-none" [class.cursor-grab]="canRank()" aria-hidden="true" title="Drag to move">⋮⋮</span>
                    <input type="number" min="1" [max]="ranked().length" [value]="i + 1" [disabled]="!canRank() || saving()"
                      (change)="moveToTyped(item.id, $event)" [attr.aria-label]="'Place of ' + item.name" data-testid="ranked-position"
                      class="w-14 rounded-lg border border-gray-200 px-2 py-1 text-center text-sm font-semibold text-gray-800 outline-none focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50" />
                    <span class="w-10 h-10 rounded-lg bg-gray-100 overflow-hidden flex-shrink-0 border border-gray-200">
                      @if (item.imageUrl) { <img [src]="item.imageUrl" [alt]="" class="w-full h-full object-cover" draggable="false" /> }
                    </span>
                    <div class="min-w-0 flex-1">
                      <p class="text-sm font-medium text-gray-800 truncate">{{ item.name }}</p>
                      <p class="text-xs text-gray-500 truncate">
                        <span class="font-mono">{{ item.productCode || '—' }}</span>
                        @if (item.sku) { · SKU {{ item.sku }} }
                        @if (item.categoryName) { · {{ item.categoryName }} }
                      </p>
                      @if (!item.available) {
                        <p class="text-xs text-amber-700" data-testid="ranked-note">Hidden from the shop, so customers do not see it.</p>
                      } @else if (item.stockQuantity <= 0) {
                        <p class="text-xs text-amber-700" data-testid="ranked-note">Out of stock: shown after the others until it is back.</p>
                      }
                    </div>
                    @if (canRank()) {
                      <div class="flex items-center gap-1 flex-shrink-0">
                        <button type="button" (click)="move(i, 0)" [disabled]="saving() || i === 0" title="Move to the top" data-testid="ranked-top"
                          class="px-2 py-1 text-xs font-medium text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-30">Top</button>
                        <button type="button" (click)="move(i, i - 1)" [disabled]="saving() || i === 0" [attr.aria-label]="'Move ' + item.name + ' up'" data-testid="ranked-up"
                          class="w-7 h-7 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-30">↑</button>
                        <button type="button" (click)="move(i, i + 1)" [disabled]="saving() || i === ranked().length - 1" [attr.aria-label]="'Move ' + item.name + ' down'" data-testid="ranked-down"
                          class="w-7 h-7 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-30">↓</button>
                        <button type="button" (click)="remove(item.id)" [disabled]="saving()" data-testid="ranked-remove"
                          class="px-2 py-1 text-xs font-medium text-red-600 border border-red-100 rounded-lg hover:bg-red-50 disabled:opacity-30">Remove</button>
                      </div>
                    }
                  </li>
                }
              </ul>
            }
          </section>

          <!-- ── finding products to rank ─────────────────────────────────── -->
          <section class="bg-white rounded-xl border border-gray-200" data-testid="find-products">
            <div class="px-5 py-3 border-b border-gray-100 flex flex-wrap items-center gap-3">
              <div class="mr-auto">
                <h2 class="text-sm font-bold text-gray-800">Find products</h2>
                <p class="text-xs text-gray-500">Search by name, SKU or product code, or choose a category.</p>
              </div>
              <input type="search" [ngModel]="q()" (ngModelChange)="onSearch($event)" placeholder="Search by name, SKU or code…" data-testid="find-search"
                class="w-64 rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-300" />
              <select [ngModel]="categoryId()" (ngModelChange)="onCategory($event)" aria-label="Filter by category" data-testid="find-category"
                class="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-600 outline-none focus:ring-2 focus:ring-blue-300">
                <option [ngValue]="null">All categories</option>
                @for (c of categories(); track c.id) {
                  <option [ngValue]="c.id">{{ c.label || c.name }}</option>
                }
              </select>
            </div>

            @if (canRank() && picked().length > 0) {
              <div class="px-5 py-2 border-b border-gray-100 bg-blue-50 flex items-center gap-3 text-sm" data-testid="find-bulk">
                <span class="font-medium text-blue-900">{{ picked().length }} selected</span>
                <button type="button" (click)="addPicked(false)" [disabled]="saving()" data-testid="find-add-picked"
                  class="px-3 py-1 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">Add to ranking</button>
                <button type="button" (click)="addPicked(true)" [disabled]="saving()" data-testid="find-first-picked"
                  class="px-3 py-1 text-xs font-medium text-blue-700 border border-blue-200 bg-white hover:bg-blue-50 rounded-lg disabled:opacity-50">Put first</button>
                <button type="button" (click)="picked.set([])" class="text-xs text-gray-500 hover:underline">Clear</button>
              </div>
            }

            @if (finding()) {
              <p class="px-5 py-6 text-sm text-gray-400">Searching…</p>
            } @else if (results().length === 0) {
              <p class="px-5 py-6 text-sm text-gray-500" data-testid="find-empty">No product matches.</p>
            } @else {
              <ul>
                @for (p of results(); track p.id) {
                  <li class="flex items-center gap-3 px-5 py-2.5 border-b border-gray-100 last:border-b-0" data-testid="find-row" [attr.data-id]="p.id">
                    @if (canRank()) {
                      <input type="checkbox" [checked]="picked().includes(p.id)" [disabled]="placeOf(p.id) !== null" (change)="togglePick(p.id)"
                        [attr.aria-label]="'Select ' + p.name" data-testid="find-pick" class="h-4 w-4 rounded border-gray-300 text-blue-600" />
                    }
                    <span class="w-10 h-10 rounded-lg bg-gray-100 overflow-hidden flex-shrink-0 border border-gray-200">
                      @if (p.imageUrl) { <img [src]="p.imageUrl" [alt]="" class="w-full h-full object-cover" /> }
                    </span>
                    <div class="min-w-0 flex-1">
                      <p class="text-sm font-medium text-gray-800 truncate">{{ p.name }}</p>
                      <p class="text-xs text-gray-500 truncate">
                        <span class="font-mono">{{ p.productCode || '—' }}</span>
                        @if (p.sku) { · SKU {{ p.sku }} }
                        @if (p.categoryName) { · {{ p.categoryName }} }
                        · {{ p.stockQuantity > 0 ? p.stockQuantity + ' in stock' : 'out of stock' }}
                      </p>
                    </div>
                    @if (placeOf(p.id); as place) {
                      <span class="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700" data-testid="find-ranked">Ranked #{{ place }}</span>
                    } @else if (canRank()) {
                      <div class="flex items-center gap-1 flex-shrink-0">
                        <button type="button" (click)="add([p.id], false)" [disabled]="saving()" data-testid="find-add"
                          class="px-2.5 py-1 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">Add to ranking</button>
                        <button type="button" (click)="add([p.id], true)" [disabled]="saving()" data-testid="find-first"
                          class="px-2.5 py-1 text-xs font-medium text-gray-700 border border-gray-200 hover:bg-gray-50 rounded-lg disabled:opacity-50">Put first</button>
                      </div>
                    }
                  </li>
                }
              </ul>
              <div class="px-5 py-3 flex items-center justify-between text-xs text-gray-500">
                <span data-testid="find-count">{{ total() }} {{ total() === 1 ? 'product' : 'products' }} · page {{ page() + 1 }} of {{ pages() }}</span>
                <span class="flex gap-2">
                  <button type="button" (click)="goTo(page() - 1)" [disabled]="page() === 0" data-testid="find-prev"
                    class="px-3 py-1 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-40">Previous</button>
                  <button type="button" (click)="goTo(page() + 1)" [disabled]="page() + 1 >= pages()" data-testid="find-next"
                    class="px-3 py-1 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-40">Next</button>
                </span>
              </div>
            }
          </section>
        </main>
      </div>
    </div>
  `,
})
export class ShopOrderComponent implements OnInit {
  private readonly shopOrder = inject(ShopOrderService);
  private readonly productService = inject(ProductService);
  private readonly categoryService = inject(CategoryService);
  private readonly perms = inject(PermissionService);
  private readonly confirm = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  protected readonly canRank = computed(() => this.perms.can('products.rank'));

  protected readonly ranked = signal<ShopOrderItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');

  protected readonly categories = signal<CategoryResponse[]>([]);
  protected readonly q = signal('');
  protected readonly categoryId = signal<number | null>(null);
  protected readonly results = signal<ProductResponse[]>([]);
  protected readonly finding = signal(true);
  protected readonly page = signal(0);
  protected readonly total = signal(0);
  protected readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / PAGE_SIZE)));
  /** Products ticked in the search results, to add together. */
  protected readonly picked = signal<number[]>([]);

  protected readonly dragFrom = signal<number | null>(null);
  protected readonly dragOver = signal<number | null>(null);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  /** Only the latest search's answer is shown. */
  private searchRun = 0;

  ngOnInit(): void {
    this.shopOrder.list().subscribe({
      next: (items) => { this.ranked.set(items); this.loading.set(false); },
      error: (err) => { this.error.set(parseApiError(err)); this.loading.set(false); },
    });
    this.categoryService.getCategories().subscribe({ next: (c) => this.categories.set(c), error: () => {} });
    this.find();
  }

  /** The place a product has in the ranking, or null when it is not ranked. */
  protected placeOf(id: number): number | null {
    const index = this.ranked().findIndex((r) => r.id === id);
    return index < 0 ? null : index + 1;
  }

  // ── changing the order: each makes the new list of ids and saves it ───────

  protected add(ids: number[], first: boolean): void {
    const current = this.ranked().map((r) => r.id);
    const fresh = ids.filter((id) => !current.includes(id));
    if (fresh.length === 0) return;
    if (current.length + fresh.length > MAX_RANKED) {
      this.notices.error('Too many ranked products', `At most ${MAX_RANKED} can be ranked. The rest follow them in the shop by themselves.`);
      return;
    }
    this.save(first ? [...fresh, ...current] : [...current, ...fresh], fresh.length === 1 ? 'Added to the ranking' : `${fresh.length} products added to the ranking`);
  }

  protected addPicked(first: boolean): void {
    const ids = this.picked();
    this.picked.set([]);
    this.add(ids, first);
  }

  protected remove(id: number): void {
    this.save(this.ranked().map((r) => r.id).filter((r) => r !== id), 'Removed from the ranking');
  }

  /** Moves the product at `from` so that it ends up at `to` (both counted from 0). */
  protected move(from: number, to: number): void {
    const ids = this.ranked().map((r) => r.id);
    const target = Math.max(0, Math.min(ids.length - 1, to));
    if (from === target || from < 0 || from >= ids.length) return;
    const [moved] = ids.splice(from, 1);
    ids.splice(target, 0, moved);
    this.save(ids, `Moved to place ${target + 1}`);
  }

  protected moveToTyped(id: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const from = this.ranked().findIndex((r) => r.id === id);
    const typed = Math.round(Number(input.value));
    if (!Number.isFinite(typed) || typed < 1) { input.value = String(from + 1); return; }
    const to = Math.min(this.ranked().length, typed) - 1;
    if (to === from) { input.value = String(from + 1); return; }
    this.move(from, to);
  }

  protected async clearAll(): Promise<void> {
    const sure = await this.confirm.ask({
      title: 'Remove every product from the ranking?',
      message: 'The shop will show its products as it did before any were ranked. No product is deleted.',
      confirmLabel: 'Remove all',
      danger: true,
    });
    if (sure) this.save([], 'The ranking was cleared');
  }

  private save(ids: number[], done: string): void {
    if (this.saving()) return;
    const before = this.ranked();
    // Show the new order at once; the server's answer then replaces it.
    const byId = new Map(before.map((r) => [r.id, r]));
    this.ranked.set(ids.map((id) => byId.get(id)).filter((r): r is ShopOrderItem => !!r));
    this.saving.set(true);
    this.error.set('');
    this.shopOrder.save(ids).subscribe({
      next: (items) => {
        this.ranked.set(items);
        this.saving.set(false);
        this.notices.success('Shop order saved', done);
      },
      error: (err) => {
        this.ranked.set(before);
        this.saving.set(false);
        this.error.set(parseApiError(err));
        this.notices.error('The order was not saved', parseApiError(err));
      },
    });
  }

  // ── dragging a row ─────────────────────────────────────────────────────────

  protected onDragStart(index: number, event: DragEvent): void {
    if (!this.canRank() || this.saving()) { event.preventDefault(); return; }
    this.dragFrom.set(index);
    event.dataTransfer?.setData('text/plain', String(index));
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
  }

  protected onDragOver(index: number, event: DragEvent): void {
    if (this.dragFrom() === null) return;
    event.preventDefault();
    this.dragOver.set(index);
  }

  protected onDrop(index: number, event: DragEvent): void {
    event.preventDefault();
    const from = this.dragFrom();
    this.onDragEnd();
    if (from !== null) this.move(from, index);
  }

  protected onDragEnd(): void {
    this.dragFrom.set(null);
    this.dragOver.set(null);
  }

  // ── finding products ──────────────────────────────────────────────────────

  protected onSearch(text: string): void {
    this.q.set(text);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => { this.page.set(0); this.find(); }, 300);
  }

  protected onCategory(id: number | null): void {
    this.categoryId.set(id);
    this.page.set(0);
    this.find();
  }

  protected goTo(page: number): void {
    if (page < 0 || page >= this.pages()) return;
    this.page.set(page);
    this.find();
  }

  protected togglePick(id: number): void {
    this.picked.update((ids) => (ids.includes(id) ? ids.filter((i) => i !== id) : [...ids, id]));
  }

  private find(): void {
    const run = ++this.searchRun;
    this.finding.set(true);
    this.productService.searchAdmin({ q: this.q().trim(), categoryId: this.categoryId(), page: this.page(), size: PAGE_SIZE }).subscribe({
      next: (res) => {
        if (run !== this.searchRun) return;
        this.results.set(res.content ?? []);
        this.total.set(res.totalElements ?? 0);
        this.finding.set(false);
      },
      error: (err) => {
        if (run !== this.searchRun) return;
        this.results.set([]);
        this.total.set(0);
        this.finding.set(false);
        this.error.set(parseApiError(err));
      },
    });
  }
}
