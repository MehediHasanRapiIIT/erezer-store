import { Component, signal, computed, inject, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { OrderService, OrderStatus } from '../../core/services/order.service';
import { OrderResponse, OrderSummary } from '../../core/models/api.models';
import { parseApiError } from '../../core/utils/api-error.util';

const ALL_STATUSES: OrderStatus[] = [
  'PLACED', 'ACCEPTED', 'IN_PRODUCTION', 'PROCESSING', 'SHIPPED',
  'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED', 'RETURNED',
];

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  templateUrl: './orders.component.html',
})
export class OrdersComponent implements OnInit, OnDestroy {
  private orderService = inject(OrderService);
  private router = inject(Router);

  readonly statuses = ALL_STATUSES;
  readonly pageSize = 10;

  /** Auto-refresh so newly placed orders appear without a manual reload. */
  private readonly POLL_MS = 60_000;
  private poll?: ReturnType<typeof setInterval>;

  // Active vs History (delivered) view
  tab = signal<'active' | 'history'>('active');

  // Filter state
  filterStatus = signal<string>('ALL');
  filterPayment = signal<string>('ALL');
  filterDate = signal<string>('ALL');
  searchQuery = signal('');

  // Pagination
  currentPage = signal(0);
  totalElements = signal(0);
  totalPages = signal(0);

  orders = signal<OrderResponse[]>([]);
  summary = signal<OrderSummary | null>(null);
  isLoading = signal(true);
  errorMessage = signal('');

  // Client-side search filter on current page
  filtered = computed(() => {
    const q = this.searchQuery().toLowerCase();
    const pay = this.filterPayment();
    let list = this.orders();

    if (q) list = list.filter(o =>
      o.id.toLowerCase().includes(q) ||
      (o.customerName ?? '').toLowerCase().includes(q) ||
      (o.customerPhone ?? '').toLowerCase().includes(q) ||
      o.deliveryAddress.toLowerCase().includes(q)
    );

    if (pay !== 'ALL') list = list.filter(o => o.paymentMethod === pay);

    return list;
  });

  showingFrom = computed(() => this.currentPage() * this.pageSize + 1);
  showingTo = computed(() => Math.min((this.currentPage() + 1) * this.pageSize, this.totalElements()));

  pageNumbers = computed(() => {
    const total = this.totalPages();
    const current = this.currentPage();
    if (total <= 5) return Array.from({ length: total }, (_, i) => i);
    const pages: number[] = [];
    if (current > 1) pages.push(0);
    if (current > 2) pages.push(-1);
    for (let i = Math.max(0, current - 1); i <= Math.min(total - 1, current + 1); i++) pages.push(i);
    if (current < total - 3) pages.push(-1);
    if (current < total - 2) pages.push(total - 1);
    return [...new Set(pages)];
  });

  ngOnInit(): void {
    this.loadPage(0);
    this.loadSummary();
    this.startPolling();
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  /** Silently re-fetch the current page + summary every POLL_MS (visible tabs only). */
  private startPolling(): void {
    if (typeof window === 'undefined') return;
    this.poll = setInterval(() => {
      if (document.visibilityState === 'hidden') return;
      this.loadPage(this.currentPage(), true);
      this.loadSummary();
    }, this.POLL_MS);
  }

  private loadSummary(): void {
    this.orderService.getSummary().subscribe({
      next: (s) => this.summary.set(s),
      error: () => {},
    });
  }

  loadPage(page: number, silent = false): void {
    if (!silent) this.isLoading.set(true);
    this.errorMessage.set('');
    const history = this.tab() === 'history';
    // History = delivered orders only; Active hides delivered (they moved to history).
    const status = history ? 'DELIVERED' : (this.filterStatus() !== 'ALL' ? this.filterStatus() : undefined);
    const excludeStatus = history ? undefined : 'DELIVERED';
    const { fromDate, toDate } = this.getDateRange(this.filterDate());
    this.orderService.getOrdersPaged(page, this.pageSize, status, fromDate, toDate, excludeStatus).subscribe({
      next: (data) => {
        this.orders.set(data.content);
        this.currentPage.set(data.number);
        this.totalElements.set(data.totalElements);
        this.totalPages.set(data.totalPages);
        this.isLoading.set(false);
      },
      error: (err) => {
        // A failed background poll shouldn't wipe the table or flash an error.
        if (!silent) this.errorMessage.set(parseApiError(err));
        this.isLoading.set(false);
      },
    });
  }

  private getDateRange(filter: string): { fromDate?: string; toDate?: string } {
    const today = new Date();
    const fmt = (d: Date) => d.toISOString().split('T')[0]; // yyyy-MM-dd

    if (filter === 'TODAY') {
      return { fromDate: fmt(today), toDate: fmt(today) };
    }
    if (filter === 'YESTERDAY') {
      const y = new Date(today); y.setDate(today.getDate() - 1);
      return { fromDate: fmt(y), toDate: fmt(y) };
    }
    if (filter === 'LAST_7') {
      const d = new Date(today); d.setDate(today.getDate() - 7);
      return { fromDate: fmt(d), toDate: fmt(today) };
    }
    if (filter === 'LAST_30') {
      const d = new Date(today); d.setDate(today.getDate() - 30);
      return { fromDate: fmt(d), toDate: fmt(today) };
    }
    return {};
  }

  setTab(tab: 'active' | 'history'): void {
    if (this.tab() === tab) return;
    this.tab.set(tab);
    this.filterStatus.set('ALL');
    this.loadPage(0);
  }

  onStatusFilterChange(status: string): void {
    this.filterStatus.set(status);
    this.loadPage(0);
  }

  onDateFilterChange(date: string): void {
    this.filterDate.set(date);
    this.loadPage(0);
  }

  setPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) this.loadPage(page);
  }

  viewOrder(order: OrderResponse): void {
    this.router.navigate(['/orders', order.id], { state: { order } });
  }

  statusBadgeClass(status: string): string {
    switch (status?.toUpperCase()) {
      case 'PLACED':
      case 'PENDING':           return 'bg-yellow-50 text-yellow-600 border border-yellow-200';
      case 'ACCEPTED':          return 'bg-blue-50 text-blue-600 border border-blue-200';
      case 'IN_PRODUCTION':     return 'bg-orange-50 text-orange-600 border border-orange-200';
      case 'PROCESSING':        return 'bg-amber-50 text-amber-600 border border-amber-200';
      case 'SHIPPED':           return 'bg-sky-50 text-sky-600 border border-sky-200';
      case 'OUT_FOR_DELIVERY':  return 'bg-purple-50 text-purple-600 border border-purple-200';
      case 'DELIVERED':         return 'bg-emerald-50 text-emerald-600 border border-emerald-200';
      case 'CANCELLED':         return 'bg-red-50 text-red-600 border border-red-200';
      case 'RETURNED':          return 'bg-rose-50 text-rose-600 border border-rose-200';
      default:                  return 'bg-gray-50 text-gray-600 border border-gray-200';
    }
  }

  statusDotClass(status: string): string {
    switch (status?.toUpperCase()) {
      case 'PLACED':
      case 'PENDING':           return 'bg-yellow-500';
      case 'ACCEPTED':          return 'bg-blue-500';
      case 'IN_PRODUCTION':     return 'bg-orange-500';
      case 'PROCESSING':        return 'bg-amber-500';
      case 'SHIPPED':           return 'bg-sky-500';
      case 'OUT_FOR_DELIVERY':  return 'bg-purple-500';
      case 'DELIVERED':         return 'bg-emerald-500';
      case 'CANCELLED':         return 'bg-red-500';
      case 'RETURNED':          return 'bg-rose-500';
      default:                  return 'bg-gray-400';
    }
  }

  statusLabel(status: string): string {
    return status?.replace(/_/g, ' ') ?? '—';
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    const d = new Date(dateStr);
    return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
      + '\n' + d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  }

  formatAmount(amount: number | string): string {
    return Number(amount).toLocaleString('en-BD', { minimumFractionDigits: 2 });
  }

  shortId(id: string): string {
    return id ? '#' + id.slice(0, 8).toUpperCase() : '—';
  }
}
