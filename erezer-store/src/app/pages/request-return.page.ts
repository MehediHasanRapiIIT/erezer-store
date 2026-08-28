import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import {
  ApiOrder,
  ReturnItemCondition,
  ReturnReason,
  ReturnRequestPayload,
} from '../core/api.models';
import { AuthService } from '../core/auth.service';

interface ItemFormRow {
  orderItemId: string;
  productName: string;
  maxQuantity: number;
  selected: boolean;
  quantity: number;
  condition: ReturnItemCondition | '';
}

const REASONS: { value: ReturnReason; label: string }[] = [
  { value: 'WRONG_SIZE',        label: "Wrong size" },
  { value: 'DEFECTIVE',         label: "Defective / damaged on arrival" },
  { value: 'NOT_AS_DESCRIBED',  label: "Not as described" },
  { value: 'CHANGED_MIND',      label: "Changed my mind" },
  { value: 'OTHER',             label: "Other" },
];

const CONDITIONS: { value: ReturnItemCondition; label: string }[] = [
  { value: 'SEALED',  label: 'Unopened / tags on' },
  { value: 'OPENED',  label: 'Opened / tried on' },
  { value: 'DAMAGED', label: 'Damaged' },
  { value: 'OTHER',   label: 'Other' },
];

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <section class="mx-auto max-w-3xl space-y-6 py-8">
      <header>
        <a [routerLink]="['/orders', orderId()]" class="text-sm text-neutral-600 underline underline-offset-4 dark:text-neutral-300">
          Back to order
        </a>
        <h1 class="app-section-title mt-2">Request a return</h1>
        <p class="app-muted text-sm">
          Returns are accepted within {{ windowDays }} days of delivery. Photos help us process your request faster.
        </p>
      </header>

      @if (loading()) {
        <p class="app-muted">Loading order…</p>
      } @else if (!order()) {
        <p class="text-red-500">Order not found.</p>
      } @else {
        <form (ngSubmit)="submit()" class="space-y-6">

          <!-- Items -->
          <article class="app-card p-5">
            <h2 class="mb-3 text-lg font-semibold">Which items?</h2>
            <div class="space-y-3">
              @for (row of items(); track row.orderItemId) {
                <div class="rounded-xl border border-neutral-200 p-3 dark:border-neutral-700">
                  <label class="flex items-start gap-3">
                    <input type="checkbox" class="mt-1" [(ngModel)]="row.selected" [ngModelOptions]="{ standalone: true }" />
                    <div class="flex-1">
                      <p class="font-medium">{{ row.productName }}</p>
                      <p class="text-xs text-neutral-500 dark:text-neutral-400">
                        Up to {{ row.maxQuantity }} units
                      </p>
                      @if (row.selected) {
                        <div class="mt-2 grid gap-2 sm:grid-cols-2">
                          <label class="text-xs font-medium text-neutral-600 dark:text-neutral-300">
                            Quantity
                            <input type="number" min="1" [max]="row.maxQuantity"
                              [(ngModel)]="row.quantity" [ngModelOptions]="{ standalone: true }"
                              class="mt-1 w-full rounded-lg border border-neutral-300 px-3 py-1.5 text-sm dark:border-neutral-700 dark:bg-neutral-900" />
                          </label>
                          <label class="text-xs font-medium text-neutral-600 dark:text-neutral-300">
                            Condition
                            <select [(ngModel)]="row.condition" [ngModelOptions]="{ standalone: true }"
                              class="mt-1 w-full rounded-lg border border-neutral-300 bg-white px-3 py-1.5 text-sm dark:border-neutral-700 dark:bg-neutral-900">
                              <option value="">Select…</option>
                              @for (c of conditions; track c.value) {
                                <option [value]="c.value">{{ c.label }}</option>
                              }
                            </select>
                          </label>
                        </div>
                      }
                    </div>
                  </label>
                </div>
              }
            </div>
          </article>

          <!-- Reason + notes -->
          <article class="app-card space-y-3 p-5">
            <h2 class="text-lg font-semibold">Why are you returning?</h2>
            <select [(ngModel)]="reason" [ngModelOptions]="{ standalone: true }"
              class="w-full rounded-xl border border-neutral-300 bg-white px-4 py-2.5 text-sm dark:border-neutral-700 dark:bg-neutral-900">
              <option value="">Select a reason…</option>
              @for (r of reasons; track r.value) {
                <option [value]="r.value">{{ r.label }}</option>
              }
            </select>
            <textarea
              [(ngModel)]="customerNotes"
              [ngModelOptions]="{ standalone: true }"
              rows="4"
              maxlength="2000"
              placeholder="Add any extra context that will help our team."
              class="w-full rounded-xl border border-neutral-300 bg-white px-4 py-2.5 text-sm dark:border-neutral-700 dark:bg-neutral-900"
            ></textarea>
          </article>

          <!-- Photos -->
          <article class="app-card space-y-3 p-5">
            <h2 class="text-lg font-semibold">Photos (optional)</h2>
            <p class="text-xs text-neutral-500 dark:text-neutral-400">Up to 3 photos. Defects must include at least one photo to speed approval.</p>
            <input type="file" accept="image/*" multiple (change)="onPhotosChosen($event)"
              class="text-sm" />
            @if (selectedPhotos().length > 0) {
              <ul class="text-xs text-neutral-600 dark:text-neutral-300">
                @for (f of selectedPhotos(); track f.name) {
                  <li>{{ f.name }} ({{ (f.size / 1024) | number: '1.0-0' }} KB)</li>
                }
              </ul>
            }
          </article>

          @if (error()) {
            <p class="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-600 dark:border-red-800 dark:bg-red-950 dark:text-red-400">
              {{ error() }}
            </p>
          }

          <div class="flex gap-2">
            <button type="submit" class="btn-primary"
              [disabled]="submitting() || !canSubmit()">
              {{ submitting() ? 'Submitting…' : 'Submit return request' }}
            </button>
            <a [routerLink]="['/orders', orderId()]" class="btn-secondary">Cancel</a>
          </div>
        </form>
      }
    </section>
  `,
})
export class RequestReturnPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly windowDays = 14;
  protected readonly reasons = REASONS;
  protected readonly conditions = CONDITIONS;

  protected readonly orderId    = signal<string>('');
  protected readonly order      = signal<ApiOrder | null>(null);
  protected readonly loading    = signal(true);
  protected readonly submitting = signal(false);
  protected readonly error      = signal<string>('');

  protected readonly items = signal<ItemFormRow[]>([]);
  protected readonly selectedPhotos = signal<File[]>([]);

  protected reason: ReturnReason | '' = '';
  protected customerNotes = '';

  protected readonly canSubmit = computed(() => {
    if (!this.reason) return false;
    const selected = this.items().filter((i) => i.selected && i.quantity > 0);
    return selected.length > 0;
  });

  ngOnInit(): void {
    const oid = this.route.snapshot.paramMap.get('orderId');
    if (!oid) {
      this.loading.set(false);
      return;
    }
    this.orderId.set(oid);

    const userId = this.auth.userId();
    if (!userId) {
      this.error.set('Please sign in to request a return.');
      this.loading.set(false);
      return;
    }

    this.api.getOrderById(userId, oid)
      .pipe(catchError(() => of(null)))
      .subscribe((o) => {
        this.order.set(o);
        if (o) {
          this.items.set(o.orderItems.map((oi) => ({
            orderItemId: String((oi as any).id),
            productName: (oi as any).productName ?? 'Item',
            maxQuantity: oi.quantity,
            selected: false,
            quantity: oi.quantity,
            condition: '',
          })));
        }
        this.loading.set(false);
      });
  }

  protected onPhotosChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    const list: File[] = [];
    for (let i = 0; i < input.files.length && list.length < 3; i++) {
      list.push(input.files[i]);
    }
    this.selectedPhotos.set(list);
  }

  protected submit(): void {
    const userId = this.auth.userId();
    const oid = this.orderId();
    if (!userId || !oid || !this.reason) return;

    const selected = this.items().filter((i) => i.selected && i.quantity > 0);
    if (selected.length === 0) {
      this.error.set('Select at least one item to return.');
      return;
    }

    this.submitting.set(true);
    this.error.set('');

    const payload: ReturnRequestPayload = {
      reason: this.reason as ReturnReason,
      customerNotes: this.customerNotes || undefined,
      items: selected.map((row) => ({
        orderItemId: row.orderItemId,
        quantity: row.quantity,
        condition: row.condition || undefined,
      })),
    };

    this.api.createReturnRequest(userId, oid, payload, this.selectedPhotos())
      .pipe(catchError((err) => {
        this.error.set(err?.error?.message ?? 'Could not submit return request.');
        this.submitting.set(false);
        return of(null);
      }))
      .subscribe((response) => {
        this.submitting.set(false);
        if (response) {
          this.router.navigateByUrl(`/orders/${oid}`);
        }
      });
  }
}
