import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import {
  CouponDiscountType,
  CouponRequest,
  CouponResponse,
  CouponService,
} from '../../core/services/coupon.service';
import { parseApiError } from '../../core/utils/api-error.util';

interface CouponForm {
  code: string;
  discountType: CouponDiscountType;
  discountValue: number | null;
  minOrderAmount: number | null;
  usageLimit: number | null;
  perUserLimit: number | null;
  validFrom: string;
  validTo: string;
  description: string;
  isActive: boolean;
}

const EMPTY_FORM: CouponForm = {
  code: '',
  discountType: 'PERCENT',
  discountValue: 10,
  minOrderAmount: null,
  usageLimit: null,
  perUserLimit: null,
  validFrom: '',
  validTo: '',
  description: '',
  isActive: true,
};

@Component({
  selector: 'app-coupons',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-gray-900">Coupons</h1>
            <span class="text-xs text-gray-400">{{ coupons().length }} total</span>
          </div>
          <button (click)="startCreate()"
            class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg">
            + New coupon
          </button>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-5xl mx-auto space-y-5">

            @if (errorMessage()) {
              <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ errorMessage() }}</p>
            }

            <!-- List -->
            <section class="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-gray-100 bg-gray-50 text-xs uppercase text-gray-400">
                    <th class="px-4 py-2.5 text-left">Code</th>
                    <th class="px-4 py-2.5 text-left">Type</th>
                    <th class="px-4 py-2.5 text-right">Value</th>
                    <th class="px-4 py-2.5 text-right">Min order</th>
                    <th class="px-4 py-2.5 text-right">Used / Limit</th>
                    <th class="px-4 py-2.5 text-left">Window</th>
                    <th class="px-4 py-2.5 text-center">Active</th>
                    <th class="px-4 py-2.5"></th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-50">
                  @if (loading()) {
                    <tr><td colspan="8" class="px-4 py-6 text-center text-gray-400">Loading…</td></tr>
                  }
                  @for (c of coupons(); track c.id) {
                    <tr [class.bg-blue-50]="editingId() === c.id">
                      <td class="px-4 py-2.5 font-mono text-xs font-semibold">{{ c.code }}</td>
                      <td class="px-4 py-2.5">{{ typeLabel(c.discountType) }}</td>
                      <td class="px-4 py-2.5 text-right">
                        @switch (c.discountType) {
                          @case ('PERCENT') { {{ c.discountValue ?? 0 }}% }
                          @case ('FLAT')    { ৳ {{ c.discountValue ?? 0 }} }
                          @default          { — }
                        }
                      </td>
                      <td class="px-4 py-2.5 text-right">
                        @if (c.minOrderAmount != null) { ৳ {{ c.minOrderAmount }} } @else { — }
                      </td>
                      <td class="px-4 py-2.5 text-right">
                        {{ c.timesUsed }} / {{ c.usageLimit ?? '∞' }}
                      </td>
                      <td class="px-4 py-2.5 text-xs text-gray-500">
                        @if (c.validFrom || c.validTo) {
                          {{ shortDate(c.validFrom) || '—' }} → {{ shortDate(c.validTo) || '—' }}
                        } @else { — }
                      </td>
                      <td class="px-4 py-2.5 text-center">
                        <span class="inline-block h-2.5 w-2.5 rounded-full"
                          [class.bg-emerald-500]="c.isActive"
                          [class.bg-gray-300]="!c.isActive"></span>
                      </td>
                      <td class="px-4 py-2.5">
                        <div class="flex items-center justify-end gap-2">
                          <button (click)="startEdit(c)" class="act-btn act-btn-edit" title="Edit">
                            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z"/></svg>
                            Edit
                          </button>
                          <button (click)="remove(c)" class="act-btn act-btn-delete" title="Delete">
                            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  } @empty {
                    @if (!loading()) {
                      <tr><td colspan="8" class="px-4 py-6 text-center text-gray-400">No coupons yet.</td></tr>
                    }
                  }
                </tbody>
              </table>
            </section>

            <!-- Form -->
            @if (creating() || editingId() !== null) {
              <section class="bg-white rounded-xl border border-gray-200 p-5">
                <h2 class="mb-4 text-base font-semibold">
                  {{ editingId() ? 'Edit coupon' : 'New coupon' }}
                </h2>
                <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  <label class="text-xs font-medium text-gray-600">
                    Code
                    <input [(ngModel)]="form.code" placeholder="WELCOME10"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm font-mono uppercase" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Discount type
                    <select [(ngModel)]="form.discountType"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm bg-white">
                      <option value="PERCENT">Percentage</option>
                      <option value="FLAT">Flat amount</option>
                      <option value="FREE_SHIPPING">Free shipping</option>
                    </select>
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Value
                    <input type="number" step="0.01" min="0" [(ngModel)]="form.discountValue"
                      [disabled]="form.discountType === 'FREE_SHIPPING'"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm disabled:bg-gray-50" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Min order amount
                    <input type="number" step="0.01" min="0" [(ngModel)]="form.minOrderAmount"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Usage limit (total)
                    <input type="number" min="1" [(ngModel)]="form.usageLimit"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Per-user limit
                    <input type="number" min="1" [(ngModel)]="form.perUserLimit"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Valid from
                    <input type="datetime-local" [(ngModel)]="form.validFrom"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Valid to
                    <input type="datetime-local" [(ngModel)]="form.validTo"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="flex items-center gap-2 text-xs font-medium text-gray-600">
                    <input type="checkbox" [(ngModel)]="form.isActive" />
                    Active
                  </label>
                  <label class="text-xs font-medium text-gray-600 sm:col-span-3">
                    Description (admin-facing)
                    <input [(ngModel)]="form.description"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                </div>

                <div class="mt-4 flex justify-end gap-2">
                  <button type="button" (click)="cancelEdit()"
                    class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">
                    Cancel
                  </button>
                  <button type="button" (click)="save()" [disabled]="saving()"
                    class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                    {{ saving() ? 'Saving…' : 'Save coupon' }}
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
export class CouponsComponent implements OnInit {
  private readonly api = inject(CouponService);

  readonly coupons    = signal<CouponResponse[]>([]);
  readonly loading    = signal(false);
  readonly saving     = signal(false);
  readonly creating   = signal(false);
  readonly editingId  = signal<string | null>(null);
  readonly errorMessage = signal<string>('');

  protected form: CouponForm = { ...EMPTY_FORM };

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.api.list().pipe(catchError(() => of([] as CouponResponse[]))).subscribe((list) => {
      this.coupons.set(list);
      this.loading.set(false);
    });
  }

  protected startCreate(): void {
    this.editingId.set(null);
    this.creating.set(true);
    this.errorMessage.set('');
    this.form = { ...EMPTY_FORM };
  }

  protected startEdit(c: CouponResponse): void {
    this.creating.set(false);
    this.editingId.set(c.id);
    this.errorMessage.set('');
    this.form = {
      code: c.code,
      discountType: c.discountType,
      discountValue: c.discountValue,
      minOrderAmount: c.minOrderAmount,
      usageLimit: c.usageLimit,
      perUserLimit: c.perUserLimit,
      validFrom: c.validFrom ?? '',
      validTo:   c.validTo ?? '',
      description: c.description ?? '',
      isActive: c.isActive,
    };
  }

  protected cancelEdit(): void {
    this.creating.set(false);
    this.editingId.set(null);
    this.form = { ...EMPTY_FORM };
  }

  protected save(): void {
    this.saving.set(true);
    this.errorMessage.set('');
    const payload: CouponRequest = {
      code: this.form.code.trim(),
      discountType: this.form.discountType,
      discountValue: this.form.discountType === 'FREE_SHIPPING' ? null : this.form.discountValue,
      minOrderAmount: this.form.minOrderAmount,
      usageLimit: this.form.usageLimit,
      perUserLimit: this.form.perUserLimit,
      validFrom: this.form.validFrom || null,
      validTo:   this.form.validTo || null,
      description: this.form.description || null,
      isActive: this.form.isActive,
    };

    const editId = this.editingId();
    const obs$ = editId ? this.api.update(editId, payload) : this.api.create(payload);

    obs$.pipe(catchError((err) => {
      this.errorMessage.set(parseApiError(err));
      this.saving.set(false);
      return of(null);
    })).subscribe((saved) => {
      this.saving.set(false);
      if (!saved) return;
      if (editId) {
        this.coupons.update((list) => list.map((c) => c.id === editId ? saved : c));
      } else {
        this.coupons.update((list) => [...list, saved]);
      }
      this.cancelEdit();
    });
  }

  protected remove(c: CouponResponse): void {
    if (!confirm(`Delete coupon "${c.code}"?`)) return;
    this.api.delete(c.id)
      .pipe(catchError((err) => { this.errorMessage.set(parseApiError(err)); return of(null); }))
      .subscribe(() => {
        this.coupons.update((list) => list.filter((x) => x.id !== c.id));
      });
  }

  protected typeLabel(t: CouponDiscountType): string {
    return t === 'PERCENT' ? 'Percent'
         : t === 'FLAT'    ? 'Flat'
         : 'Free shipping';
  }

  protected shortDate(iso: string | null): string {
    if (!iso) return '';
    try {
      const d = new Date(iso);
      return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: '2-digit' });
    } catch {
      return iso;
    }
  }
}
