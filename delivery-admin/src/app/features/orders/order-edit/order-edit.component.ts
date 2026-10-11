import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { OrderEdit, OrderService } from '../../../core/services/order.service';
import { ProductService } from '../../../core/services/product.service';
import { VariantResponse, VariantService } from '../../../core/services/variant.service';
import { OrderItem, OrderResponse, ProductResponse } from '../../../core/models/api.models';
import { PermissionService } from '../../../core/services/permission.service';
import { NoticeService } from '../../../core/services/notice.service';
import { parseApiError } from '../../../core/utils/api-error.util';

/** One row of the items table: a line already on the order, or one being added. */
interface EditLine {
  /** The order line's id; null for a product being added. */
  id: string | null;
  productId: number | null;
  variantId: number | null;
  name: string;
  chosen: string;
  code: string;
  imageUrl: string | null;
  quantity: number;
  /** What the price box holds. For a new line it starts as the shop's price today. */
  unitPrice: number;
  /** The price it had when the page opened (or the shop's price, for a new line). */
  startPrice: number;
  startQuantity: number;
  /** Made to the customer's measurements: its quantity can change, and it never touches stock. */
  custom: boolean;
  /** How many more can be added before stock runs out; null when not known. */
  stockLeft: number | null;
}

/**
 * Edit Order: correct the customer's details, the items and their quantities,
 * and the prices, on one screen. Nothing is saved until "Save changes"; the
 * server then moves stock for every item that changed and leaves a note on the
 * order saying who changed what.
 */
@Component({
  selector: 'app-order-edit',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between gap-4 flex-shrink-0">
          <div class="flex items-center gap-3 min-w-0">
            <button type="button" (click)="back()" class="text-sm text-gray-500 hover:text-gray-800">← Order</button>
            <h1 class="text-lg font-bold text-gray-900 truncate" data-testid="edit-title">Edit order {{ order()?.orderNumber || '' }}</h1>
          </div>
          <div class="flex items-center gap-2">
            <button type="button" (click)="back()" [disabled]="saving()"
              class="px-3 py-1.5 text-sm font-medium text-gray-600 border border-gray-200 hover:bg-gray-50 rounded-lg disabled:opacity-50">Cancel</button>
            <button type="button" (click)="save()" [disabled]="saving() || !order() || !changed() || !!problem()" data-testid="edit-save"
              class="px-4 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
              {{ saving() ? 'Saving…' : 'Save changes' }}
            </button>
          </div>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          @if (loading()) {
            <p class="text-sm text-gray-400">Loading…</p>
          } @else if (!order()) {
            <p class="rounded-lg border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700">{{ error() || 'The order could not be loaded.' }}</p>
          } @else {
            <div class="mx-auto max-w-5xl space-y-5">
              @if (error()) {
                <p class="rounded-lg border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700" data-testid="edit-error">{{ error() }}</p>
              }
              <div class="rounded-xl border border-blue-100 bg-blue-50 px-4 py-3 text-xs text-blue-900">
                Nothing changes until you press <span class="font-semibold">Save changes</span>. Stock follows the items: more of an item takes stock, fewer or
                a removed item gives it back. A note saying what you changed is added to the order. The customer is not emailed; use
                <span class="font-semibold">Send invoice</span> on the order when you want them to see the corrected order.
              </div>
              @if (cancelled()) {
                <p class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-2.5 text-xs text-amber-800" data-testid="edit-cancelled">
                  This order is cancelled, and its stock was already returned. Changing its items here will not move stock.
                </p>
              }

              <!-- ── customer ─────────────────────────────────────────────── -->
              <section class="bg-white rounded-xl border border-gray-200 p-5" data-testid="edit-customer">
                <h2 class="text-sm font-bold text-gray-800 mb-3">Customer information</h2>
                <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <label class="block text-xs font-medium text-gray-600">Customer name
                    <input [(ngModel)]="name" data-testid="edit-name"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-300" />
                  </label>
                  <label class="block text-xs font-medium text-gray-600">Phone number
                    <input [(ngModel)]="phone" data-testid="edit-phone"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-300" />
                  </label>
                  <label class="block text-xs font-medium text-gray-600">Email <span class="font-normal text-gray-400">(optional)</span>
                    <input [(ngModel)]="email" type="email" data-testid="edit-email"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-300" />
                  </label>
                  <label class="block text-xs font-medium text-gray-600 sm:col-span-2">Delivery address
                    <textarea [(ngModel)]="address" rows="2" maxlength="255" data-testid="edit-address"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-300"></textarea>
                  </label>
                </div>
              </section>

              <!-- ── items ────────────────────────────────────────────────── -->
              <section class="bg-white rounded-xl border border-gray-200" data-testid="edit-items">
                <div class="px-5 py-3 border-b border-gray-100">
                  <h2 class="text-sm font-bold text-gray-800">Items</h2>
                  @if (!canReprice()) {
                    <p class="text-xs text-gray-400">Changing prices needs the “Change prices on an order” permission.</p>
                  }
                </div>
                <div class="hidden sm:grid grid-cols-[1fr_8rem_6rem_7rem_5rem] gap-3 px-5 py-2 text-[11px] font-semibold uppercase tracking-wider text-gray-400">
                  <span>Product</span><span class="text-right">Unit price (৳)</span><span class="text-right">Qty</span><span class="text-right">Total</span><span></span>
                </div>
                @for (line of lines(); track $index; let i = $index) {
                  <div class="grid grid-cols-1 sm:grid-cols-[1fr_8rem_6rem_7rem_5rem] items-center gap-3 px-5 py-3 border-t border-gray-100" data-testid="edit-line">
                    <div class="flex items-center gap-3 min-w-0">
                      <span class="w-11 h-11 rounded-lg bg-gray-100 overflow-hidden flex-shrink-0 border border-gray-200">
                        @if (line.imageUrl) { <img [src]="line.imageUrl" alt="" class="w-full h-full object-cover" /> }
                      </span>
                      <div class="min-w-0">
                        <p class="text-sm font-medium text-gray-800 truncate">{{ line.name }}
                          @if (!line.id) { <span class="ml-1 rounded-full bg-emerald-50 px-1.5 py-0.5 text-[10px] font-semibold text-emerald-700">New</span> }
                        </p>
                        <p class="text-xs text-gray-500 truncate">
                          <span class="font-mono">{{ line.code || '—' }}</span>
                          @if (line.chosen) { · {{ line.chosen }} }
                          @if (line.custom) { · made to measure }
                        </p>
                      </div>
                    </div>
                    <input type="number" min="0" step="0.01" [ngModel]="line.unitPrice" (ngModelChange)="setPrice(i, $event)" [disabled]="!canReprice()"
                      [attr.aria-label]="'Unit price of ' + line.name" data-testid="edit-price"
                      class="w-full rounded-lg border border-gray-200 px-2 py-1.5 text-right text-sm outline-none focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50 disabled:text-gray-500" />
                    <input type="number" min="1" max="999" step="1" [ngModel]="line.quantity" (ngModelChange)="setQuantity(i, $event)"
                      [attr.aria-label]="'Quantity of ' + line.name" data-testid="edit-quantity"
                      class="w-full rounded-lg border border-gray-200 px-2 py-1.5 text-right text-sm outline-none focus:ring-2 focus:ring-blue-300" />
                    <p class="text-right text-sm font-semibold text-gray-800 tabular-nums" data-testid="edit-line-total">৳ {{ money(line.unitPrice * line.quantity) }}</p>
                    <button type="button" (click)="removeLine(i)" data-testid="edit-remove"
                      class="justify-self-end px-2 py-1 text-xs font-medium text-red-600 border border-red-100 rounded-lg hover:bg-red-50">Remove</button>
                  </div>
                }
                @if (lines().length === 0) {
                  <p class="px-5 py-4 text-sm text-red-600 border-t border-gray-100">An order needs at least one item. To drop the whole order, cancel it instead.</p>
                }

                <!-- add an item -->
                <div class="border-t border-gray-100 px-5 py-4 bg-gray-50/60" data-testid="edit-add">
                  <p class="text-xs font-semibold text-gray-700 mb-2">Add an item</p>
                  @if (!adding()) {
                    <input type="search" [ngModel]="q()" (ngModelChange)="onSearch($event)" placeholder="Search by name, SKU or product code…" data-testid="edit-add-search"
                      class="w-full sm:w-96 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-300" />
                    @if (q().trim()) {
                      @if (finding()) {
                        <p class="mt-2 text-xs text-gray-400">Searching…</p>
                      } @else if (results().length === 0) {
                        <p class="mt-2 text-xs text-gray-500">No product matches.</p>
                      } @else {
                        <ul class="mt-2 max-w-xl rounded-lg border border-gray-200 bg-white divide-y divide-gray-100">
                          @for (p of results(); track p.id) {
                            <li>
                              <button type="button" (click)="pick(p)" data-testid="edit-add-result"
                                class="flex w-full items-center gap-3 px-3 py-2 text-left hover:bg-gray-50">
                                <span class="w-9 h-9 rounded bg-gray-100 overflow-hidden flex-shrink-0 border border-gray-200">
                                  @if (p.imageUrl) { <img [src]="p.imageUrl" alt="" class="w-full h-full object-cover" /> }
                                </span>
                                <span class="min-w-0 flex-1">
                                  <span class="block text-sm font-medium text-gray-800 truncate">{{ p.name }}</span>
                                  <span class="block text-xs text-gray-500 truncate"><span class="font-mono">{{ p.productCode }}</span> · {{ p.stockQuantity > 0 ? p.stockQuantity + ' in stock' : 'out of stock' }}</span>
                                </span>
                              </button>
                            </li>
                          }
                        </ul>
                      }
                    }
                  } @else {
                    <div class="flex flex-wrap items-end gap-3" data-testid="edit-add-form">
                      <p class="text-sm font-medium text-gray-800">{{ adding()!.name }}</p>
                      @if (addVariants().length > 0) {
                        <label class="text-xs font-medium text-gray-600">Which one
                          <select [ngModel]="addVariantId()" (ngModelChange)="addVariantId.set($event)" data-testid="edit-add-variant"
                            class="mt-1 block rounded-lg border border-gray-200 bg-white px-2 py-1.5 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-300">
                            <option [ngValue]="null">Choose…</option>
                            @for (v of addVariants(); track v.id) {
                              <option [ngValue]="v.id" [disabled]="(v.stockQuantity ?? 0) <= 0 && !cancelled()">{{ variantLabel(v) }} — {{ (v.stockQuantity ?? 0) > 0 ? v.stockQuantity + ' in stock' : 'out of stock' }}</option>
                            }
                          </select>
                        </label>
                      } @else if (loadingVariants()) {
                        <p class="text-xs text-gray-400">Loading sizes…</p>
                      }
                      <label class="text-xs font-medium text-gray-600">Quantity
                        <input type="number" min="1" max="999" [ngModel]="addQuantity()" (ngModelChange)="addQuantity.set(+$event || 1)" data-testid="edit-add-quantity"
                          class="mt-1 block w-20 rounded-lg border border-gray-200 px-2 py-1.5 text-right text-sm outline-none focus:ring-2 focus:ring-blue-300" />
                      </label>
                      <button type="button" (click)="confirmAdd()" [disabled]="loadingVariants() || (addVariants().length > 0 && addVariantId() === null)" data-testid="edit-add-confirm"
                        class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">Add to order</button>
                      <button type="button" (click)="adding.set(null)" class="px-3 py-1.5 text-sm font-medium text-gray-600 border border-gray-200 rounded-lg hover:bg-white">Back</button>
                    </div>
                    @if (addError()) { <p class="mt-2 text-xs text-red-600" data-testid="edit-add-error">{{ addError() }}</p> }
                  }
                </div>
              </section>

              <!-- ── money ────────────────────────────────────────────────── -->
              <section class="bg-white rounded-xl border border-gray-200 p-5" data-testid="edit-money">
                <h2 class="text-sm font-bold text-gray-800 mb-3">Totals</h2>
                <div class="max-w-md space-y-2 text-sm">
                  <div class="flex items-center justify-between"><span class="text-gray-600">Subtotal</span><span class="tabular-nums" data-testid="edit-subtotal">৳ {{ money(subtotal()) }}</span></div>
                  <label class="flex items-center justify-between gap-3"><span class="text-gray-600">Discount (৳)</span>
                    <input type="number" min="0" step="0.01" [(ngModel)]="discount" [disabled]="!canReprice()" data-testid="edit-discount"
                      class="w-32 rounded-lg border border-gray-200 px-2 py-1.5 text-right text-sm outline-none focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50 disabled:text-gray-500" />
                  </label>
                  <label class="flex items-center justify-between gap-3"><span class="text-gray-600">Delivery fee (৳)</span>
                    <input type="number" min="0" step="0.01" [(ngModel)]="shipping" [disabled]="!canReprice()" data-testid="edit-shipping"
                      class="w-32 rounded-lg border border-gray-200 px-2 py-1.5 text-right text-sm outline-none focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50 disabled:text-gray-500" />
                  </label>
                  @if (other() > 0) {
                    <div class="flex items-center justify-between"><span class="text-gray-600">Made-to-measure charge and tax</span><span class="tabular-nums">৳ {{ money(other()) }}</span></div>
                  }
                  <div class="flex items-center justify-between border-t border-gray-100 pt-2 text-base font-bold">
                    <span>New total</span><span class="tabular-nums text-blue-700" data-testid="edit-total">৳ {{ money(total()) }}</span>
                  </div>
                  @if (total() !== oldTotal()) {
                    <p class="text-xs text-gray-500" data-testid="edit-total-was">It was ৳ {{ money(oldTotal()) }}.</p>
                  }
                </div>
                @if (paid() > 0 && total() !== paid()) {
                  <p class="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800" data-testid="edit-paid-warning">
                    The customer already paid <span class="font-semibold">৳ {{ money(paid()) }}</span> online.
                    @if (total() > paid()) {
                      With this total, <span class="font-semibold">৳ {{ money(total() - paid()) }}</span> is still to be collected from them.
                    } @else {
                      With this total, <span class="font-semibold">৳ {{ money(paid() - total()) }}</span> has to be refunded to them. The panel does not send the refund; do it in bKash.
                    }
                  </p>
                }
                @if (problem()) {
                  <p class="mt-3 text-xs text-red-600" data-testid="edit-problem">{{ problem() }}</p>
                }
              </section>
            </div>
          }
        </main>
      </div>
    </div>
  `,
})
export class OrderEditComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly orders = inject(OrderService);
  private readonly products = inject(ProductService);
  private readonly variants = inject(VariantService);
  private readonly perms = inject(PermissionService);
  private readonly notices = inject(NoticeService);

  protected readonly canReprice = computed(() => this.perms.can('orders.reprice'));

  protected readonly order = signal<OrderResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');

  // The form. Plain fields for the text boxes; signals where a total depends on them.
  protected name = '';
  protected phone = '';
  protected email = '';
  protected address = '';
  protected readonly lines = signal<EditLine[]>([]);
  private readonly discountValue = signal(0);
  private readonly shippingValue = signal(0);
  protected get discount(): number { return this.discountValue(); }
  protected set discount(v: number) { this.discountValue.set(Number.isFinite(+v) ? +v : 0); }
  protected get shipping(): number { return this.shippingValue(); }
  protected set shipping(v: number) { this.shippingValue.set(Number.isFinite(+v) ? +v : 0); }

  /** As the order stood when the page opened. */
  private start = { discount: 0, shipping: 0 };
  /** What the total carries besides goods, discount and delivery (a made-to-measure charge, tax). */
  protected readonly other = signal(0);
  protected readonly oldTotal = signal(0);
  protected readonly paid = signal(0);
  protected readonly cancelled = computed(() => (this.order()?.orderStatus ?? '').toUpperCase() === 'CANCELLED');

  protected readonly subtotal = computed(() => round2(this.lines().reduce((sum, l) => sum + l.unitPrice * l.quantity, 0)));
  protected readonly total = computed(() =>
    Math.max(0, round2(this.subtotal() - Math.min(this.discountValue(), this.subtotal()) + this.shippingValue() + this.other())));

  // Adding an item
  protected readonly q = signal('');
  protected readonly results = signal<ProductResponse[]>([]);
  protected readonly finding = signal(false);
  protected readonly adding = signal<ProductResponse | null>(null);
  protected readonly addVariants = signal<VariantResponse[]>([]);
  protected readonly loadingVariants = signal(false);
  protected readonly addVariantId = signal<number | null>(null);
  protected readonly addQuantity = signal(1);
  protected readonly addError = signal('');
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private searchRun = 0;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('orderId');
    if (!id) { this.router.navigate(['/orders']); return; }
    this.orders.getAdminOrder(id).subscribe({
      next: (o) => { this.fill(o); this.loading.set(false); },
      error: (err) => { this.error.set(parseApiError(err)); this.loading.set(false); },
    });
  }

  private fill(o: OrderResponse): void {
    this.order.set(o);
    this.name = o.customerName ?? '';
    this.phone = o.customerPhone ?? '';
    this.email = o.customerEmail ?? '';
    this.address = o.deliveryAddress ?? '';
    const lines = (o.orderItems ?? []).map((item) => this.toLine(item));
    this.lines.set(lines);
    const subtotal = o.subtotalAmount ?? round2(lines.reduce((sum, l) => sum + l.unitPrice * l.quantity, 0));
    const discount = +(o.discountAmount ?? 0);
    const shipping = +(o.shippingFee ?? o.deliveryCharge ?? 0);
    this.start = { discount, shipping };
    this.discountValue.set(discount);
    this.shippingValue.set(shipping);
    this.oldTotal.set(round2(+(o.totalAmount ?? 0)));
    const other = round2(+(o.totalAmount ?? 0) - (subtotal - discount + shipping));
    this.other.set(other > 0.009 ? other : 0);
    this.paid.set(round2(+(o.paidAmount ?? 0)));
  }

  private toLine(item: OrderItem): EditLine {
    return {
      id: item.id,
      productId: item.productId == null ? null : +item.productId,
      variantId: item.variantId,
      name: item.productName || 'Item',
      chosen: (item.variantSize || item.variantName || '').trim(),
      code: item.productCode ?? '',
      imageUrl: item.imageUrl,
      quantity: item.quantity,
      unitPrice: +item.priceAtOrder,
      startPrice: +item.priceAtOrder,
      startQuantity: item.quantity,
      custom: !!item.customMeasurements,
      stockLeft: null,
    };
  }

  // ── the items table ────────────────────────────────────────────────────────

  protected setPrice(index: number, value: unknown): void {
    const price = Number(value);
    this.lines.update((lines) => lines.map((l, i) => (i === index ? { ...l, unitPrice: Number.isFinite(price) && price >= 0 ? price : 0 } : l)));
  }

  protected setQuantity(index: number, value: unknown): void {
    const quantity = Math.round(Number(value));
    this.lines.update((lines) => lines.map((l, i) => (i === index ? { ...l, quantity: Number.isFinite(quantity) ? quantity : 0 } : l)));
  }

  protected removeLine(index: number): void {
    this.lines.update((lines) => lines.filter((_, i) => i !== index));
  }

  // ── adding an item ─────────────────────────────────────────────────────────

  protected onSearch(text: string): void {
    this.q.set(text);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    if (!text.trim()) { this.results.set([]); return; }
    this.searchTimer = setTimeout(() => {
      const run = ++this.searchRun;
      this.finding.set(true);
      this.products.searchAdmin({ q: text.trim(), page: 0, size: 8 }).subscribe({
        next: (res) => { if (run === this.searchRun) { this.results.set(res.content ?? []); this.finding.set(false); } },
        error: () => { if (run === this.searchRun) { this.results.set([]); this.finding.set(false); } },
      });
    }, 300);
  }

  protected pick(product: ProductResponse): void {
    this.adding.set(product);
    this.addVariants.set([]);
    this.addVariantId.set(null);
    this.addQuantity.set(1);
    this.addError.set('');
    this.loadingVariants.set(true);
    this.variants.list(product.id).subscribe({
      next: (list) => { this.addVariants.set(list ?? []); this.loadingVariants.set(false); },
      error: () => { this.addVariants.set([]); this.loadingVariants.set(false); },
    });
  }

  protected variantLabel(v: VariantResponse): string {
    return [v.optionLabel, v.fitLabel, v.size].filter(Boolean).join(' / ') || v.name || 'One size';
  }

  protected confirmAdd(): void {
    const product = this.adding();
    if (!product) return;
    const variant = this.addVariants().find((v) => v.id === this.addVariantId()) ?? null;
    const variantId = variant?.id ?? null;
    if (this.lines().some((l) => !l.custom && l.productId === product.id && l.variantId === variantId)) {
      this.addError.set('That is already on this order. Change its quantity in the list above instead.');
      return;
    }
    const quantity = Math.max(1, Math.round(this.addQuantity()));
    const left = variant ? (variant.stockQuantity ?? 0) : product.stockQuantity;
    if (!this.cancelled() && quantity > left) {
      this.addError.set(left > 0 ? `Only ${left} in stock.` : 'It is out of stock.');
      return;
    }
    // The shop's price today, as near as the panel can tell; the server has the last word for a new line.
    const price = +(variant?.priceOverride ?? product.discountPrice ?? product.price ?? 0);
    this.lines.update((lines) => [...lines, {
      id: null, productId: product.id, variantId, name: product.name, chosen: variant ? this.variantLabel(variant) : '',
      code: product.productCode ?? '', imageUrl: product.imageUrl || null, quantity, unitPrice: price, startPrice: price,
      startQuantity: 0, custom: false, stockLeft: left,
    }]);
    this.adding.set(null);
    this.q.set('');
    this.results.set([]);
  }

  // ── saving ─────────────────────────────────────────────────────────────────

  /** What stops the order being saved, in words; '' when nothing does. */
  protected readonly problem = computed(() => {
    const lines = this.lines();
    if (lines.length === 0) return 'An order needs at least one item.';
    if (lines.some((l) => !(l.quantity >= 1 && l.quantity <= 999))) return 'Every quantity has to be between 1 and 999.';
    if (lines.some((l) => !(l.unitPrice >= 0))) return 'A price cannot be less than 0.';
    if (this.discountValue() < 0 || this.shippingValue() < 0) return 'The discount and the delivery fee cannot be less than 0.';
    if (this.discountValue() > this.subtotal() && this.discountValue() !== this.start.discount) {
      return 'The discount is more than the items cost.';
    }
    return '';
  });

  /** True when the form differs from the order as it was loaded. */
  protected changed(): boolean {
    const o = this.order();
    if (!o) return false;
    if (this.name.trim() !== (o.customerName ?? '') || this.phone.trim() !== (o.customerPhone ?? '')
        || this.email.trim() !== (o.customerEmail ?? '') || this.address.trim() !== (o.deliveryAddress ?? '')) return true;
    if (this.discountValue() !== this.start.discount || this.shippingValue() !== this.start.shipping) return true;
    const lines = this.lines();
    if (lines.length !== (o.orderItems ?? []).length) return true;
    return lines.some((l) => !l.id || l.quantity !== l.startQuantity || l.unitPrice !== l.startPrice);
  }

  protected save(): void {
    const o = this.order();
    if (!o || this.problem()) return;
    const edit: OrderEdit = {
      customerName: this.name.trim(),
      customerPhone: this.phone.trim(),
      customerEmail: this.email.trim(),
      deliveryAddress: this.address.trim(),
      items: this.lines().map((l) => ({
        id: l.id,
        productId: l.id ? null : l.productId,
        variantId: l.id ? null : l.variantId,
        quantity: l.quantity,
        // Only a price the admin changed is sent; otherwise the line keeps its price (or gets the shop's, when new).
        unitPrice: l.unitPrice !== l.startPrice ? l.unitPrice : null,
      })),
    };
    if (this.discountValue() !== this.start.discount) edit.discountAmount = this.discountValue();
    if (this.shippingValue() !== this.start.shipping) edit.shippingFee = this.shippingValue();

    this.saving.set(true);
    this.error.set('');
    this.orders.editOrder(o.id, edit).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.notices.success('Order updated', `${saved.orderNumber ?? 'The order'} now totals ৳ ${this.money(+(saved.totalAmount ?? 0))}`);
        this.router.navigate(['/orders', o.id]);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(parseApiError(err));
        this.notices.error('The order was not changed', parseApiError(err));
      },
    });
  }

  protected back(): void {
    const id = this.order()?.id ?? this.route.snapshot.paramMap.get('orderId');
    this.router.navigate(id ? ['/orders', id] : ['/orders']);
  }

  protected money(value: number): string {
    return (Number.isFinite(value) ? value : 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}
