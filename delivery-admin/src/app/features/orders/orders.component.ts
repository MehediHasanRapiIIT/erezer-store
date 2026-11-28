import { Component, signal, computed, inject, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { OrderService, OrderStatus } from '../../core/services/order.service';
import { CustomerHistory, OrderDaySummary, OrderResponse, OrderSummary } from '../../core/models/api.models';
import { PageBeforeSearch } from '../../core/utils/page-before-search';
import { CustomerHistoryBadgeComponent } from './customer-history/customer-history.component';
import { parseApiError } from '../../core/utils/api-error.util';
import { addDays, businessToday, formatDate, formatDateWithWeekday, formatTaka } from '../../core/utils/business-date.util';
import { PagerComponent } from '../../shared/pager/pager.component';
import { ListMemory } from '../../core/services/list-memory.service';

const ALL_STATUSES: OrderStatus[] = [
  'PLACED', 'ACCEPTED', 'CALL_CONFIRMED', 'PROCESSING', 'SHIPPED',
  'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED', 'RETURNED',
];

/**
 * The Orders page. Search, status, payment and date filters and paging are
 * all done by the server, so a search covers every order, not just the page
 * on screen. Dates are shop days in Dhaka.
 */
@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [FormsModule, SidebarComponent, CustomerHistoryBadgeComponent, PagerComponent],
  templateUrl: './orders.component.html',
})
export class OrdersComponent implements OnInit, OnDestroy {
  private orderService = inject(OrderService);
  private router = inject(Router);
  private memory = inject(ListMemory);

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
  /** The day picked on the calendar (yyyy-mm-dd) when the date filter is "Pick a date". */
  pickedDate = signal<string>('');
  daySummary = signal<OrderDaySummary | null>(null);
  readonly today = businessToday();
  protected readonly formatDay = formatDate;
  protected readonly taka = formatTaka;
  searchQuery = signal('');
  /** Typed search text, sent to the server after a short pause. */
  private searchSubject = new Subject<string>();
  /** Emptying the search box goes back to the page shown before it. */
  private readonly pageBeforeSearch = new PageBeforeSearch();

  // Pagination
  currentPage = signal(0);
  totalElements = signal(0);
  totalPages = signal(0);

  orders = signal<OrderResponse[]>([]);
  /** Order id → other orders from the same phone number, for the badge under the phone. */
  history = signal<Record<string, CustomerHistory>>({});
  summary = signal<OrderSummary | null>(null);
  isLoading = signal(true);
  errorMessage = signal('');

  /** Numbers each request, so an answer that arrives after a newer one is ignored. */
  private latestRequest = 0;



  ngOnInit(): void {
    // Back from an order: the list as it was left.
    const was = this.memory.recall<OrdersListState>('orders');
    if (was) {
      this.tab.set(was.tab);
      this.filterStatus.set(was.status);
      this.filterPayment.set(was.payment);
      this.filterDate.set(was.date);
      this.pickedDate.set(was.pickedDate);
      this.searchQuery.set(was.search);
    }
    this.loadPage(was?.page ?? 0);
    this.loadSummary();
    this.startPolling();
    this.searchSubject.pipe(debounceTime(300), distinctUntilChanged()).subscribe((q) => this.loadPage(this.pageBeforeSearch.pageFor(q)));
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
    this.searchSubject.complete();
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
    const request = ++this.latestRequest;
    if (!silent) this.isLoading.set(true);
    this.errorMessage.set('');
    const history = this.tab() === 'history';
    // History = delivered orders only; Active hides delivered (they moved to history).
    const status = history ? 'DELIVERED' : (this.filterStatus() !== 'ALL' ? this.filterStatus() : undefined);
    const excludeStatus = history ? undefined : 'DELIVERED';
    const { fromDate, toDate } = this.getDateRange(this.filterDate());
    this.loadPeriodSummary(fromDate, toDate);
    this.orderService.getOrdersPaged(page, this.pageSize, status, fromDate, toDate, excludeStatus,
      this.searchQuery().trim(), this.filterPayment()).subscribe({
      next: (data) => {
        if (request !== this.latestRequest) return;
        // A remembered page that no longer exists (fewer orders now): show the last one.
        if (!data.content.length && data.number > 0 && data.totalPages > 0 && data.number >= data.totalPages) {
          this.loadPage(data.totalPages - 1, silent);
          return;
        }
        this.orders.set(data.content);
        this.loadHistory(data.content, request);
        this.currentPage.set(data.number);
        this.memory.remember<OrdersListState>('orders', {
          tab: this.tab(), status: this.filterStatus(), payment: this.filterPayment(), date: this.filterDate(),
          pickedDate: this.pickedDate(), search: this.searchQuery(), page: data.number,
        });
        this.totalElements.set(data.totalElements);
        this.totalPages.set(data.totalPages);
        this.isLoading.set(false);
      },
      error: (err) => {
        if (request !== this.latestRequest) return;
        // A failed background poll shouldn't wipe the table or flash an error.
        if (!silent) this.errorMessage.set(parseApiError(err));
        this.isLoading.set(false);
      },
    });
  }

  /** The customer history badges for this page; the list shows without them if this fails. */
  private loadHistory(orders: OrderResponse[], request: number): void {
    const ids = orders.filter((o) => o.customerPhone).map((o) => o.id);
    if (ids.length === 0) return;
    this.orderService.getCustomerHistoryCounts(ids).subscribe({
      next: (counts) => {
        if (request === this.latestRequest) this.history.set(counts);
      },
      error: () => {},
    });
  }

  /** Shop days in Dhaka, both inclusive; the server turns them into UTC windows. */
  private getDateRange(filter: string): { fromDate?: string; toDate?: string } {
    const today = businessToday();
    if (filter === 'TODAY') return { fromDate: today, toDate: today };
    if (filter === 'YESTERDAY') {
      const yesterday = addDays(today, -1);
      return { fromDate: yesterday, toDate: yesterday };
    }
    if (filter === 'DATE') {
      const day = this.pickedDate();
      return day ? { fromDate: day, toDate: day } : {};
    }
    if (filter === 'LAST_7') return { fromDate: addDays(today, -6), toDate: today };
    if (filter === 'LAST_30') return { fromDate: addDays(today, -29), toDate: today };
    return {};
  }

  setTab(tab: 'active' | 'history'): void {
    if (this.tab() === tab) return;
    this.tab.set(tab);
    this.filterStatus.set('ALL');
    this.loadPage(0);
  }

  onSearchChange(query: string): void {
    this.pageBeforeSearch.typing(query, this.currentPage());
    this.searchQuery.set(query);
    this.searchSubject.next(query);
  }

  onStatusFilterChange(status: string): void {
    this.filterStatus.set(status);
    this.loadPage(0);
  }

  onPaymentFilterChange(payment: string): void {
    this.filterPayment.set(payment);
    this.loadPage(0);
  }

  onDateFilterChange(date: string): void {
    this.filterDate.set(date);
    // "Pick a date" starts on today, ready to change.
    if (date === 'DATE' && !this.pickedDate()) this.pickedDate.set(businessToday());
    this.loadPage(0);
  }

  /** The single day the list shows (Today, Yesterday or a picked date), else null. */
  shownDay(): string | null {
    const today = businessToday();
    switch (this.filterDate()) {
      case 'TODAY': return today;
      case 'YESTERDAY': return addDays(today, -1);
      case 'DATE': return this.pickedDate() || null;
      default: return null;
    }
  }

  /** ◀ ▶: the day before or after the one shown, never past today. */
  stepDay(days: number): void {
    const day = this.shownDay();
    if (!day) return;
    const next = addDays(day, days);
    if (next > businessToday()) return;
    this.filterDate.set('DATE');
    this.pickedDate.set(next);
    this.loadPage(0);
  }

  onPickedDateChange(day: string): void {
    this.pickedDate.set(day);
    if (day) this.loadPage(0);
  }

  /** The totals of the dates the list shows, every status, whatever else is filtered. */
  private loadPeriodSummary(fromDate?: string, toDate?: string): void {
    const key = `${fromDate ?? ''}|${toDate ?? ''}`;
    this.periodKey = key;
    this.orderService.getPeriodSummary(fromDate, toDate).subscribe({
      next: (s) => { if (this.periodKey === key) this.daySummary.set(s); },
      error: () => { if (this.periodKey === key) this.daySummary.set(null); },
    });
  }
  private periodKey = '';

  /** "Orders on Sunday, 8 Nov 2026", "Orders 15 Nov 2026 – 21 Nov 2026" or "All orders". */
  periodLabel(d: OrderDaySummary): string {
    if (!d.fromDate && !d.toDate) return 'All orders';
    if (d.fromDate === d.toDate) return `Orders on ${formatDateWithWeekday(d.fromDate!)}`;
    return `Orders ${d.fromDate ? this.formatDay(d.fromDate) : '…'} – ${d.toDate ? this.formatDay(d.toDate) : '…'}`;
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
      case 'CALL_CONFIRMED':    return 'bg-teal-50 text-teal-700 border border-teal-200';
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
      case 'CALL_CONFIRMED':    return 'bg-teal-500';
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
      + '\n' + d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true });
  }

  formatAmount(amount: number | string): string {
    return Number(amount).toLocaleString('en-BD', { minimumFractionDigits: 2 });
  }

  shortId(id: string): string {
    return id ? '#' + id.slice(0, 8).toUpperCase() : '—';
  }
}

/** What the Orders list remembers while the admin is open. */
interface OrdersListState {
  tab: 'active' | 'history';
  status: string;
  payment: string;
  date: string;
  pickedDate: string;
  search: string;
  page: number;
}
