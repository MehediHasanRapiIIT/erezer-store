import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import {
  CustomOrderDetail,
  CustomOrderService,
  CustomOrderStatus,
  CustomOrderSummary,
} from '../../core/services/custom-order.service';
import { parseApiError } from '../../core/utils/api-error.util';

const STATUSES: CustomOrderStatus[] = ['NEW', 'IN_REVIEW', 'QUOTED', 'CONFIRMED', 'DELIVERED', 'CLOSED'];
// Delivered orders live in the History tab, so they're not offered as an Active-list filter.
const ACTIVE_FILTER_STATUSES: CustomOrderStatus[] = ['NEW', 'IN_REVIEW', 'QUOTED', 'CONFIRMED', 'CLOSED'];

@Component({
  selector: 'app-custom-orders',
  standalone: true,
  imports: [DatePipe, FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <div class="flex items-center gap-4">
            <h1 class="text-lg font-bold text-gray-900">Custom design requests</h1>
            <div class="inline-flex rounded-lg border border-gray-200 p-0.5 text-sm">
              <button (click)="setTab('active')" class="rounded-md px-3 py-1 font-medium"
                [class.bg-gray-900]="tab() === 'active'" [class.text-white]="tab() === 'active'"
                [class.text-gray-500]="tab() !== 'active'">Active</button>
              <button (click)="setTab('history')" class="rounded-md px-3 py-1 font-medium"
                [class.bg-gray-900]="tab() === 'history'" [class.text-white]="tab() === 'history'"
                [class.text-gray-500]="tab() !== 'history'">History</button>
            </div>
          </div>
          @if (tab() === 'active') {
            <div class="flex items-center gap-2 text-sm">
              <label class="text-gray-500">Filter:</label>
              <select [ngModel]="statusFilter()" (ngModelChange)="setStatusFilter($event)"
                class="rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm">
                <option value="ALL">All active</option>
                @for (s of activeFilterStatuses; track s) { <option [value]="s">{{ label(s) }}</option> }
              </select>
            </div>
          } @else {
            <span class="text-sm text-gray-400">Delivered orders</span>
          }
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-6xl mx-auto grid gap-6 lg:grid-cols-[1fr_2fr]">

            <!-- List -->
            <section class="bg-white rounded-xl border border-gray-200 overflow-hidden h-fit">
              <header class="border-b border-gray-100 bg-gray-50 px-4 py-2 text-xs uppercase text-gray-400">
                {{ totalElements() }} request(s)
              </header>
              <ul class="divide-y divide-gray-50">
                @if (loading()) {
                  <li class="px-4 py-6 text-center text-sm text-gray-400">Loading…</li>
                }
                @for (o of orders(); track o.id) {
                  <li>
                    <button (click)="select(o)" class="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-gray-50"
                      [class.bg-blue-50]="detail()?.id === o.id">
                      @if (o.thumbnailUrl) {
                        <img [src]="o.thumbnailUrl" alt="" class="h-10 w-10 rounded object-cover border border-gray-100" />
                      } @else {
                        <span class="flex h-10 w-10 items-center justify-center rounded bg-gray-100 text-gray-400">👕</span>
                      }
                      <div class="min-w-0 flex-1">
                        <div class="flex items-center justify-between gap-2">
                          <span class="truncate text-sm font-medium" [class.text-gray-900]="o.status === 'NEW'"
                            [class.text-gray-500]="o.status !== 'NEW'">{{ o.customerName }}</span>
                          <span class="inline-block rounded-full px-2 py-0.5 text-xs font-medium" [class]="badgeClass(o.status)">{{ label(o.status) }}</span>
                        </div>
                        <p class="truncate text-xs text-gray-500">{{ o.reference }} · {{ o.itemName || '—' }}</p>
                        <p class="text-xs text-gray-400">{{ o.createdAt | date: 'mediumDate' }}</p>
                      </div>
                    </button>
                  </li>
                } @empty {
                  @if (!loading()) {
                    <li class="px-4 py-6 text-center text-sm text-gray-400">No requests yet.</li>
                  }
                }
              </ul>
              @if (totalPages() > 1) {
                <div class="flex items-center justify-between border-t border-gray-100 px-4 py-2 text-xs text-gray-500">
                  <button (click)="goToPage(page() - 1)" [disabled]="page() === 0"
                    class="rounded-lg border border-gray-200 px-2.5 py-1 hover:bg-gray-50 disabled:opacity-40">Prev</button>
                  <span>Page {{ page() + 1 }} / {{ totalPages() }}</span>
                  <button (click)="goToPage(page() + 1)" [disabled]="page() >= totalPages() - 1"
                    class="rounded-lg border border-gray-200 px-2.5 py-1 hover:bg-gray-50 disabled:opacity-40">Next</button>
                </div>
              }
            </section>

            <!-- Detail -->
            <section class="bg-white rounded-xl border border-gray-200 p-5">
              @if (detail(); as d) {
                <div class="space-y-4">
                  <header class="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <h2 class="text-lg font-semibold">{{ d.reference }}</h2>
                      <p class="text-sm text-gray-500">{{ d.firstName }} {{ d.lastName }} &lt;{{ d.email }}&gt; · {{ d.phone }}</p>
                      <p class="text-xs text-gray-400">{{ d.createdAt | date: 'medium' }}</p>
                    </div>
                    <span class="inline-block rounded-full px-2.5 py-1 text-xs font-medium" [class]="badgeClass(d.status)">{{ label(d.status) }}</span>
                  </header>

                  <!-- Design previews -->
                  @if (d.images.length) {
                    <div class="space-y-2">
                      <div class="flex items-center justify-between">
                        <h3 class="text-xs font-semibold uppercase text-gray-400">Design ({{ d.images.length }})</h3>
                        <button (click)="openPreview(0)"
                          class="rounded-lg bg-gray-900 px-3 py-1.5 text-xs font-semibold text-white hover:bg-black">
                          Preview
                        </button>
                      </div>
                      <div class="flex flex-wrap gap-3">
                        @for (img of d.images; track img.url; let i = $index) {
                          <figure class="text-center">
                            <button (click)="openPreview(i)">
                              <img [src]="img.url" [alt]="img.view" class="h-32 w-28 rounded-lg border border-gray-200 object-contain bg-gray-50 transition hover:ring-2 hover:ring-gray-900" />
                            </button>
                            <figcaption class="mt-1 text-xs text-gray-400">{{ img.view }}</figcaption>
                          </figure>
                        }
                      </div>
                    </div>
                  }

                  <!-- Design + shipping facts -->
                  <div class="grid gap-4 sm:grid-cols-2 text-sm">
                    <div>
                      <h3 class="mb-1 text-xs font-semibold uppercase text-gray-400">Design</h3>
                      <dl class="space-y-0.5 text-gray-700">
                        <div><dt class="inline text-gray-400">Item: </dt><dd class="inline">{{ d.itemName || '—' }}</dd></div>
                        <div><dt class="inline text-gray-400">Colour: </dt><dd class="inline">{{ d.colorName || '—' }}</dd></div>
                        <div><dt class="inline text-gray-400">Size: </dt><dd class="inline">{{ d.size || '—' }}</dd></div>
                        <div><dt class="inline text-gray-400">Technique: </dt><dd class="inline">{{ d.printTechnique || '—' }}</dd></div>
                      </dl>
                    </div>
                    <div>
                      <h3 class="mb-1 text-xs font-semibold uppercase text-gray-400">Ship to</h3>
                      <p class="text-gray-700">
                        {{ d.shippingAddress }}@if (d.apartment) {, {{ d.apartment }}}<br>
                        {{ d.city }} {{ d.zipCode }}<br>{{ d.country }}
                      </p>
                    </div>
                  </div>

                  <!-- Notes (rich text from the form) -->
                  <div>
                    <h3 class="mb-1 text-xs font-semibold uppercase text-gray-400">Order details / notes</h3>
                    <div class="prose prose-sm max-w-none rounded-lg border border-gray-100 bg-gray-50 p-4 text-sm text-gray-800" [innerHTML]="d.notes"></div>
                  </div>

                  <!-- Admin notes + status -->
                  <div class="grid gap-3 border-t border-gray-100 pt-3 sm:grid-cols-2">
                    <label class="text-xs font-semibold uppercase text-gray-400">
                      Status
                      <select [(ngModel)]="editStatus" class="mt-1 block w-full rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-800">
                        @for (s of statuses; track s) { <option [value]="s">{{ label(s) }}</option> }
                      </select>
                    </label>
                    <label class="text-xs font-semibold uppercase text-gray-400">
                      Internal notes
                      <textarea [(ngModel)]="editNotes" rows="2" class="mt-1 block w-full rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-800"></textarea>
                    </label>
                  </div>

                  @if (error()) {
                    <p class="rounded-md bg-red-50 px-3 py-2 text-xs text-red-700">{{ error() }}</p>
                  }

                  <div class="flex gap-2 border-t border-gray-100 pt-3">
                    <button (click)="save(d)" [disabled]="acting()"
                      class="rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                      Save changes
                    </button>
                    <a [href]="'mailto:' + d.email + '?subject=' + replyEncoded(d)" target="_blank" rel="noopener"
                      class="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50">
                      Reply via email
                    </a>
                    <button (click)="remove(d)" [disabled]="acting()"
                      class="ml-auto px-3 py-1.5 text-xs font-medium text-red-500 hover:text-red-600">
                      Delete
                    </button>
                  </div>
                </div>
              } @else {
                <p class="text-sm text-gray-400">Select a request to view the design and details.</p>
              }
            </section>
          </div>
        </main>
      </div>

      <!-- ── Design preview modal (all images, one after another) ──────────── -->
      @if (previewOpen() && detail(); as d) {
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" (click)="closePreview()">
          <div class="flex max-h-[92vh] w-full max-w-3xl flex-col rounded-2xl bg-white" (click)="$event.stopPropagation()">
            <header class="flex items-center justify-between border-b border-gray-100 px-5 py-3">
              <h2 class="text-sm font-semibold text-gray-900">
                {{ d.reference }} — {{ currentImage()?.view }}
                <span class="text-gray-400">({{ previewIndex() + 1 }} / {{ d.images.length }})</span>
              </h2>
              <button (click)="closePreview()" class="rounded-full px-2 text-xl leading-none text-gray-400 hover:text-gray-700">&times;</button>
            </header>

            <div class="relative flex flex-1 items-center justify-center overflow-auto bg-gray-50 p-4">
              @if (d.images.length > 1) {
                <button (click)="prevPreview()" class="absolute left-3 top-1/2 -translate-y-1/2 rounded-full bg-white/90 px-3 py-2 text-lg shadow hover:bg-white">‹</button>
              }
              <img [src]="currentImage()?.url" [alt]="currentImage()?.view" class="max-h-[60vh] max-w-full rounded-lg object-contain" />
              @if (d.images.length > 1) {
                <button (click)="nextPreview()" class="absolute right-3 top-1/2 -translate-y-1/2 rounded-full bg-white/90 px-3 py-2 text-lg shadow hover:bg-white">›</button>
              }
            </div>

            <footer class="flex items-center gap-2 overflow-x-auto border-t border-gray-100 px-5 py-3">
              @for (img of d.images; track img.url; let i = $index) {
                <button (click)="goToPreview(i)"
                  class="shrink-0 rounded-lg border-2 p-0.5"
                  [class.border-gray-900]="previewIndex() === i"
                  [class.border-transparent]="previewIndex() !== i">
                  <img [src]="img.url" [alt]="img.view" class="h-14 w-12 rounded object-contain bg-gray-50" />
                </button>
              }
              <a [href]="currentImage()?.url" target="_blank" rel="noopener"
                class="ml-auto shrink-0 rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50">
                Open original
              </a>
            </footer>
          </div>
        </div>
      }
    </div>
  `,
})
export class CustomOrdersComponent implements OnInit {
  private readonly api = inject(CustomOrderService);

  readonly statuses = STATUSES;
  readonly activeFilterStatuses = ACTIVE_FILTER_STATUSES;
  readonly tab = signal<'active' | 'history'>('active');
  readonly orders = signal<CustomOrderSummary[]>([]);
  readonly detail = signal<CustomOrderDetail | null>(null);
  readonly loading = signal(false);
  readonly acting = signal(false);
  readonly error = signal<string>('');
  readonly statusFilter = signal<string>('ALL');

  // Pagination
  private readonly pageSize = 12;
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  protected editStatus: CustomOrderStatus = 'NEW';
  protected editNotes = '';

  // Design preview modal
  readonly previewOpen = signal(false);
  readonly previewIndex = signal(0);

  ngOnInit(): void {
    this.reload();
  }

  protected setTab(tab: 'active' | 'history'): void {
    if (this.tab() === tab) return;
    this.tab.set(tab);
    this.statusFilter.set('ALL');
    this.page.set(0);
    this.detail.set(null);
    this.reload();
  }

  protected setStatusFilter(status: string): void {
    this.statusFilter.set(status);
    this.page.set(0);
    this.reload();
  }

  protected goToPage(p: number): void {
    if (p < 0 || (this.totalPages() && p >= this.totalPages())) return;
    this.page.set(p);
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.list(this.statusFilter(), this.page(), this.pageSize, this.tab() === 'history')
      .pipe(catchError(() => of(null)))
      .subscribe((page) => {
        this.loading.set(false);
        if (page) {
          this.orders.set(page.content);
          this.totalPages.set(page.totalPages);
          this.totalElements.set(page.totalElements);
        }
      });
  }

  protected select(o: CustomOrderSummary): void {
    this.error.set('');
    this.previewOpen.set(false);
    this.previewIndex.set(0);
    this.api.get(o.id).pipe(catchError((err) => { this.error.set(parseApiError(err)); return of(null); }))
      .subscribe((d) => {
        if (d) {
          this.detail.set(d);
          this.editStatus = d.status;
          this.editNotes = d.adminNotes ?? '';
        }
      });
  }

  protected save(d: CustomOrderDetail): void {
    this.acting.set(true);
    this.api.updateStatus(d.id, this.editStatus, this.editNotes)
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); this.acting.set(false); return of(null); }))
      .subscribe((updated) => {
        this.acting.set(false);
        if (updated) {
          this.detail.set(updated);
          this.orders.update((list) => list.map((x) => x.id === updated.id ? { ...x, status: updated.status } : x));
        }
      });
  }

  protected remove(d: CustomOrderDetail): void {
    if (!confirm('Delete this request?')) return;
    this.acting.set(true);
    this.api.delete(d.id)
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); this.acting.set(false); return of(null); }))
      .subscribe(() => {
        this.acting.set(false);
        this.orders.update((list) => list.filter((x) => x.id !== d.id));
        if (this.detail()?.id === d.id) this.detail.set(null);
      });
  }

  // ── Preview modal ──────────────────────────────────────────────────────────

  protected readonly currentImage = computed(() => this.detail()?.images[this.previewIndex()] ?? null);

  protected openPreview(index: number): void {
    if (!this.detail()?.images.length) return;
    this.previewIndex.set(index);
    this.previewOpen.set(true);
  }

  protected closePreview(): void {
    this.previewOpen.set(false);
  }

  protected goToPreview(index: number): void {
    this.previewIndex.set(index);
  }

  protected nextPreview(): void {
    const total = this.detail()?.images.length ?? 0;
    if (total) this.previewIndex.update((i) => (i + 1) % total);
  }

  protected prevPreview(): void {
    const total = this.detail()?.images.length ?? 0;
    if (total) this.previewIndex.update((i) => (i - 1 + total) % total);
  }

  protected label(status: CustomOrderStatus): string {
    return status.charAt(0) + status.slice(1).toLowerCase().replace('_', ' ');
  }

  protected badgeClass(status: CustomOrderStatus): string {
    switch (status) {
      case 'DELIVERED': return 'bg-green-100 text-green-700';
      case 'CONFIRMED': return 'bg-emerald-100 text-emerald-700';
      case 'QUOTED':    return 'bg-indigo-100 text-indigo-700';
      case 'IN_REVIEW': return 'bg-blue-100 text-blue-700';
      case 'CLOSED':    return 'bg-gray-100 text-gray-600';
      default:          return 'bg-amber-100 text-amber-700';
    }
  }

  protected replyEncoded(d: CustomOrderDetail): string {
    return encodeURIComponent(`Re: your custom design request ${d.reference}`);
  }
}
