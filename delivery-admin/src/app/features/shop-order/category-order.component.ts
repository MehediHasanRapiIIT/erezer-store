import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CategoryService } from '../../core/services/category.service';
import { CategoryResponse } from '../../core/models/api.models';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

/**
 * The order of categories in the shop, on the "Categories" tab of Shop Order.
 *
 * The list is the shop's category tree, top to bottom. A category moves among
 * its own group: a main category among the main categories, a subcategory among
 * the subcategories of the same parent. Every move is saved at once.
 */
@Component({
  selector: 'app-category-order',
  standalone: true,
  template: `
    <div class="rounded-xl border border-blue-100 bg-blue-50 px-4 py-3 text-xs text-blue-900">
      <p class="font-semibold">How the shop orders its categories</p>
      <p class="mt-1">
        Categories are shown in this order wherever the shop lists them: the category buttons on the Shop page, the menu,
        “Shop by category” and the home page. A subcategory is arranged among the other subcategories of the same category.
      </p>
      <p class="mt-1">Categories you have not arranged come after the arranged ones, from A to Z.</p>
    </div>

    @if (error()) {
      <p class="rounded-lg border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700" data-testid="category-order-error">{{ error() }}</p>
    }
    @if (!canEdit()) {
      <p class="text-xs text-gray-400">Changing the order needs the “Edit categories” permission.</p>
    }

    <section class="bg-white rounded-xl border border-gray-200" data-testid="category-order">
      <div class="px-5 py-3 border-b border-gray-100 flex items-center justify-between gap-3">
        <div>
          <h2 class="text-sm font-bold text-gray-800">Categories</h2>
          <p class="text-xs text-gray-500">Drag a row within its group, or use Top, ↑ and ↓.</p>
        </div>
        <div class="flex items-center gap-3">
          @if (saving()) { <span class="text-xs text-blue-600">Saving…</span> }
          @if (canEdit() && arranged()) {
            <button type="button" (click)="resetAll()" [disabled]="saving()" data-testid="category-order-reset"
              class="px-3 py-1.5 text-xs font-medium text-gray-600 border border-gray-200 hover:bg-gray-50 rounded-lg disabled:opacity-50">Back to A–Z</button>
          }
        </div>
      </div>
      @if (loading()) {
        <p class="px-5 py-6 text-sm text-gray-400">Loading…</p>
      } @else if (categories().length === 0) {
        <p class="px-5 py-6 text-sm text-gray-500">There are no categories yet.</p>
      } @else {
        <ul>
          @for (cat of categories(); track cat.id; let i = $index) {
            <li class="flex items-center gap-3 px-5 py-2.5 border-b border-gray-100 last:border-b-0"
              [class.bg-blue-50]="dragOver() === cat.id && dragFrom() !== null && dragFrom() !== cat.id"
              [class.opacity-50]="dragFrom() === cat.id"
              [attr.draggable]="canEdit() && !saving()" data-testid="category-order-row" [attr.data-id]="cat.id" [attr.data-depth]="cat.depth ?? 0"
              (dragstart)="onDragStart(cat, $event)" (dragover)="onDragOver(cat, $event)" (drop)="onDrop(cat, $event)" (dragend)="onDragEnd()">
              <div class="flex items-center gap-3 min-w-0 flex-1" [style.padding-left.px]="(cat.depth ?? 0) * 28">
                <span class="text-gray-300 select-none" [class.cursor-grab]="canEdit()" aria-hidden="true" title="Drag to move">⋮⋮</span>
                <span class="w-7 text-center text-xs font-semibold text-gray-500" data-testid="category-order-place">{{ placeOf(cat) }}</span>
                <span class="w-9 h-9 rounded-lg bg-gray-100 overflow-hidden flex-shrink-0 border border-gray-200">
                  @if (cat.imageUrl) { <img [src]="cat.imageUrl" alt="" class="w-full h-full object-cover" draggable="false" /> }
                </span>
                <div class="min-w-0">
                  <p class="text-sm font-medium text-gray-800 truncate">
                    @if ((cat.depth ?? 0) > 0) { <span class="text-gray-400">↳</span> }
                    {{ cat.name }}
                  </p>
                  <p class="text-xs text-gray-500">
                    {{ (cat.depth ?? 0) === 0 ? 'Main category' : 'Subcategory' }}
                    @if (!cat.isActive) { · <span class="text-amber-700">hidden from the shop</span> }
                  </p>
                </div>
              </div>
              @if (canEdit()) {
                <div class="flex items-center gap-1 flex-shrink-0">
                  <button type="button" (click)="move(cat, 'top')" [disabled]="saving() || placeOf(cat) === 1" title="Move to the top of its group" data-testid="category-order-top"
                    class="px-2 py-1 text-xs font-medium text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-30">Top</button>
                  <button type="button" (click)="move(cat, 'up')" [disabled]="saving() || placeOf(cat) === 1" [attr.aria-label]="'Move ' + cat.name + ' up'" data-testid="category-order-up"
                    class="w-7 h-7 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-30">↑</button>
                  <button type="button" (click)="move(cat, 'down')" [disabled]="saving() || placeOf(cat) === siblingsOf(cat).length" [attr.aria-label]="'Move ' + cat.name + ' down'" data-testid="category-order-down"
                    class="w-7 h-7 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-30">↓</button>
                </div>
              }
            </li>
          }
        </ul>
      }
    </section>
  `,
  host: { class: 'block space-y-5' },
})
export class CategoryOrderComponent implements OnInit {
  private readonly categoryService = inject(CategoryService);
  private readonly perms = inject(PermissionService);
  private readonly confirm = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  protected readonly canEdit = computed(() => this.perms.can('categories.edit'));
  /** Every category, as the shop's tree read top to bottom. */
  protected readonly categories = signal<CategoryResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  /** True when any category has a place of its own, so there is something to put back. */
  protected readonly arranged = computed(() => this.categories().some((c) => c.shopOrder != null));

  /** The id of the row being dragged, and of the row it is over. */
  protected readonly dragFrom = signal<number | null>(null);
  protected readonly dragOver = signal<number | null>(null);

  ngOnInit(): void {
    this.categoryService.getCategories().subscribe({
      next: (all) => { this.categories.set(all); this.loading.set(false); },
      error: (err) => { this.error.set(parseApiError(err)); this.loading.set(false); },
    });
  }

  /** The categories in the same group as this one, in the order shown. */
  protected siblingsOf(cat: CategoryResponse): CategoryResponse[] {
    const parent = cat.parentId ?? null;
    return this.categories().filter((c) => (c.parentId ?? null) === parent);
  }

  /** This category's place in its group, from 1. */
  protected placeOf(cat: CategoryResponse): number {
    return this.siblingsOf(cat).findIndex((c) => c.id === cat.id) + 1;
  }

  protected move(cat: CategoryResponse, where: 'top' | 'up' | 'down'): void {
    const ids = this.siblingsOf(cat).map((c) => c.id);
    const from = ids.indexOf(cat.id);
    const to = where === 'top' ? 0 : where === 'up' ? from - 1 : from + 1;
    this.place(cat, ids, from, to);
  }

  private place(cat: CategoryResponse, ids: number[], from: number, to: number): void {
    if (from < 0 || to < 0 || to >= ids.length || from === to) return;
    ids.splice(to, 0, ids.splice(from, 1)[0]);
    this.save(cat.parentId ?? null, ids, `${cat.name} is now number ${to + 1}${cat.parentId != null ? ' in its group' : ''}`);
  }

  private save(parentId: number | null, ids: number[], done: string): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.error.set('');
    this.categoryService.arrange(parentId, ids).subscribe({
      next: (all) => {
        this.categories.set(all);
        this.saving.set(false);
        this.notices.success('Category order saved', done);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(parseApiError(err));
        this.notices.error('The order was not saved', parseApiError(err));
      },
    });
  }

  protected async resetAll(): Promise<void> {
    const sure = await this.confirm.ask({
      title: 'Put every category back in A–Z order?',
      message: 'The order you arranged is forgotten. No category is deleted or hidden.',
      confirmLabel: 'Back to A–Z',
    });
    if (!sure) return;
    // One save per group that has an order of its own.
    const groups = [...new Set(this.categories().filter((c) => c.shopOrder != null).map((c) => c.parentId ?? null))];
    this.saving.set(true);
    this.error.set('');
    const next = (): void => {
      const parentId = groups.shift();
      if (parentId === undefined) {
        this.saving.set(false);
        this.notices.success('Category order saved', 'Every category is back in A–Z order');
        return;
      }
      this.categoryService.arrange(parentId, []).subscribe({
        next: (all) => { this.categories.set(all); next(); },
        error: (err) => { this.saving.set(false); this.error.set(parseApiError(err)); },
      });
    };
    next();
  }

  // ── dragging a row within its group ────────────────────────────────────────

  protected onDragStart(cat: CategoryResponse, event: DragEvent): void {
    if (!this.canEdit() || this.saving()) { event.preventDefault(); return; }
    this.dragFrom.set(cat.id);
    event.dataTransfer?.setData('text/plain', String(cat.id));
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
  }

  protected onDragOver(cat: CategoryResponse, event: DragEvent): void {
    const dragged = this.categories().find((c) => c.id === this.dragFrom());
    // Only a row of the same group is somewhere it can be dropped.
    if (!dragged || (dragged.parentId ?? null) !== (cat.parentId ?? null)) return;
    event.preventDefault();
    this.dragOver.set(cat.id);
  }

  protected onDrop(cat: CategoryResponse, event: DragEvent): void {
    event.preventDefault();
    const dragged = this.categories().find((c) => c.id === this.dragFrom());
    this.onDragEnd();
    if (!dragged || (dragged.parentId ?? null) !== (cat.parentId ?? null)) return;
    const ids = this.siblingsOf(dragged).map((c) => c.id);
    this.place(dragged, ids, ids.indexOf(dragged.id), ids.indexOf(cat.id));
  }

  protected onDragEnd(): void {
    this.dragFrom.set(null);
    this.dragOver.set(null);
  }
}
