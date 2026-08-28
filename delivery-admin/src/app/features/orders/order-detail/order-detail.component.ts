import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { catchError, of } from 'rxjs';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { OrderService } from '../../../core/services/order.service';
import {
  OrderItem,
  OrderResponse,
  OrderStatus,
  OrderStatusOption,
  OrderTrackingResponse,
} from '../../../core/models/api.models';
import { parseApiError } from '../../../core/utils/api-error.util';
import { OrderNotesComponent } from '../order-notes/order-notes.component';

// Display order for the lifecycle timeline (PENDING omitted — it's a legacy alias).
const TIMELINE: OrderStatus[] = [
  'PLACED',
  'ACCEPTED',
  'IN_PRODUCTION',
  'PROCESSING',
  'SHIPPED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
];

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [FormsModule, SidebarComponent, OrderNotesComponent],
  templateUrl: './order-detail.component.html',
})
export class OrderDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private orderService = inject(OrderService);
  private sanitizer = inject(DomSanitizer);

  readonly order = signal<OrderResponse | null>(null);
  readonly tracking = signal<OrderTrackingResponse | null>(null);
  readonly statusOptions = signal<OrderStatusOption[]>([]);

  readonly selectedStatus = signal<OrderStatus>('PLACED');
  readonly statusNote = signal('');
  readonly courierName = signal('');

  // Invoice modal state
  readonly invoiceModalOpen = signal(false);
  readonly invoiceLoading = signal(false);          // fetching the preview PDF
  readonly invoiceBusy = signal(false);             // send in progress
  readonly invoiceMessage = signal('');
  readonly invoiceError = signal('');
  readonly invoicePreviewUrl = signal<SafeResourceUrl | null>(null);
  private invoiceBlob: Blob | null = null;
  private invoiceObjectUrl: string | null = null;

  readonly trackingNumber = signal('');

  readonly isUpdating = signal(false);
  readonly updateError = signal('');
  readonly updateSuccess = signal(false);

  /** Current status drawn from the tracking endpoint when available, else order. */
  readonly currentStatus = computed<OrderStatus>(() => {
    const t = this.tracking();
    if (t) return t.currentStatus;
    const raw = this.order()?.orderStatus ?? 'PLACED';
    return (raw === 'PENDING' ? 'PLACED' : raw) as OrderStatus;
  });

  /** Statuses the admin is allowed to transition to right now. */
  readonly allowedNext = computed<OrderStatus[]>(() => {
    const current = this.currentStatus();
    const opt = this.statusOptions().find((s) => s.status === current);
    return opt ? opt.allowedNext : [];
  });

  readonly statusChanged = computed(() => this.selectedStatus() !== this.currentStatus());

  readonly shippingFieldsRequired = computed(() => this.selectedStatus() === 'SHIPPED');

  // ── lifecycle ──────────────────────────────────────────────────────────────

  ngOnInit(): void {
    // Router-state order (from the list) gives an instant render; the id comes
    // from the route so the page also works on refresh / deep-link.
    const stateOrder = history.state?.order as OrderResponse | undefined;
    const orderId = stateOrder?.id ?? this.route.snapshot.paramMap.get('orderId') ?? undefined;

    if (!orderId) {
      this.router.navigate(['/orders']);
      return;
    }

    if (stateOrder) this.applyOrder(stateOrder);

    // Always fetch fresh so edits (address/phone) and status changes show the
    // latest — the list data passed via state can be stale.
    this.orderService.getAdminOrder(orderId).subscribe({
      next: (o) => this.applyOrder(o),
      error: () => { if (!stateOrder) this.router.navigate(['/orders']); },
    });

    this.loadTracking(orderId);
    this.loadStatusOptions();
  }

  private applyOrder(o: OrderResponse): void {
    this.order.set(o);
    this.selectedStatus.set(this.normalizeStatus(o.orderStatus));
    this.courierName.set(o.courierName ?? '');
    this.trackingNumber.set(o.trackingNumber ?? '');
  }

  private loadTracking(orderId: string): void {
    this.orderService.getTracking(orderId)
      .pipe(catchError(() => of(null)))
      .subscribe((t) => {
        this.tracking.set(t);
        if (t) {
          // Default the dropdown to the first legal next state if any.
          const opt = this.statusOptions().find((s) => s.status === t.currentStatus);
          const first = opt?.allowedNext[0];
          if (first) {
            this.selectedStatus.set(first);
          } else {
            this.selectedStatus.set(t.currentStatus);
          }
        }
      });
  }

  private loadStatusOptions(): void {
    this.orderService.getStatusOptions()
      .pipe(catchError(() => of([] as OrderStatusOption[])))
      .subscribe((opts) => {
        this.statusOptions.set(opts);
        // Re-default selectedStatus once options arrive.
        const opt = opts.find((s) => s.status === this.currentStatus());
        const first = opt?.allowedNext[0];
        if (first && this.selectedStatus() === this.currentStatus()) {
          this.selectedStatus.set(first);
        }
      });
  }

  // ── nav helpers ────────────────────────────────────────────────────────────

  goBack(): void {
    this.router.navigate(['/orders']);
  }

  /** Open the preview modal and fetch the PDF (auth-fetched blob → embedded preview). */
  printInvoice(): void {
    const order = this.order();
    if (!order) return;

    this.invoiceModalOpen.set(true);
    this.invoiceLoading.set(true);
    this.invoiceMessage.set('');
    this.invoiceError.set('');
    this.revokeInvoiceUrl();
    this.invoiceBlob = null;
    this.invoicePreviewUrl.set(null);

    this.orderService.getInvoicePdf(order.id).subscribe({
      next: (blob) => {
        this.invoiceBlob = blob;
        this.invoiceObjectUrl = URL.createObjectURL(blob);
        // #view=FitH tells the built-in PDF viewer to fit the page width.
        this.invoicePreviewUrl.set(
          this.sanitizer.bypassSecurityTrustResourceUrl(this.invoiceObjectUrl + '#view=FitH'),
        );
        this.invoiceLoading.set(false);
      },
      error: () => {
        this.invoiceLoading.set(false);
        this.invoiceError.set('Could not load the invoice preview.');
      },
    });
  }

  /** Email the invoice (PDF attached) + SMS the customer. */
  sendInvoice(): void {
    const order = this.order();
    if (!order || this.invoiceBusy()) return;
    this.invoiceBusy.set(true);
    this.invoiceMessage.set('Sending…');
    this.invoiceError.set('');

    this.orderService.sendInvoice(order.id).subscribe({
      next: (res) => {
        this.invoiceBusy.set(false);
        this.invoiceMessage.set(res.message);
      },
      error: (err) => {
        this.invoiceBusy.set(false);
        this.invoiceError.set(parseApiError(err) || 'Could not send the invoice.');
      },
    });
  }

  /** Save the previewed PDF to disk. */
  downloadInvoice(): void {
    if (!this.invoiceObjectUrl) return;
    const order = this.order();
    const ref = order ? 'INV-' + order.id.slice(0, 8).toUpperCase() : 'invoice';
    const a = document.createElement('a');
    a.href = this.invoiceObjectUrl;
    a.download = `${ref}.pdf`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  /** Close the modal and release the object URL. */
  closeInvoiceModal(): void {
    this.invoiceModalOpen.set(false);
    this.invoicePreviewUrl.set(null);
    this.invoiceBlob = null;
    this.invoiceMessage.set('');
    this.invoiceError.set('');
    this.revokeInvoiceUrl();
  }

  private revokeInvoiceUrl(): void {
    if (this.invoiceObjectUrl) {
      URL.revokeObjectURL(this.invoiceObjectUrl);
      this.invoiceObjectUrl = null;
    }
  }

  scrollToStatus(): void {
    document.getElementById('status-update-panel')
      ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  // ── timeline ──────────────────────────────────────────────────────────────

  timeline(): { label: string; status: OrderStatus; done: boolean; time?: string }[] {
    const o = this.order();
    if (!o) return [];
    const current = this.currentStatus();
    if (current === 'CANCELLED' || current === 'RETURNED') {
      return [];
    }
    const currIdx = TIMELINE.indexOf(current);
    return TIMELINE.map((s, i) => ({
      label: this.statusLabel(s),
      status: s,
      done: currIdx >= 0 && i <= currIdx,
      time: i === 0 ? this.formatDate(o.createdAt) : undefined,
    }));
  }

  // ── status update ──────────────────────────────────────────────────────────

  onUpdateStatus(): void {
    const o = this.order();
    if (!o) return;

    if (this.shippingFieldsRequired() && (!this.courierName().trim() || !this.trackingNumber().trim())) {
      this.updateError.set('Courier and tracking number are required when marking as Shipped.');
      return;
    }

    this.isUpdating.set(true);
    this.updateError.set('');
    this.updateSuccess.set(false);

    this.orderService.updateOrderStatusFull(o.id, {
      status: this.selectedStatus(),
      note: this.statusNote().trim() || undefined,
      courierName: this.courierName().trim() || undefined,
      trackingNumber: this.trackingNumber().trim() || undefined,
    }).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.statusNote.set('');
        this.isUpdating.set(false);
        this.updateSuccess.set(true);
        this.loadTracking(updated.id);
        setTimeout(() => this.updateSuccess.set(false), 3000);
      },
      error: (err) => {
        this.updateError.set(parseApiError(err));
        this.isUpdating.set(false);
      },
    });
  }

  // ── formatting helpers ─────────────────────────────────────────────────────

  statusLabel(status: string): string {
    switch (status) {
      case 'PLACED':           return 'Placed';
      case 'PENDING':          return 'Placed';
      case 'ACCEPTED':         return 'Accepted';
      case 'IN_PRODUCTION':    return 'In production';
      case 'PROCESSING':       return 'Processing';
      case 'SHIPPED':          return 'Shipped';
      case 'OUT_FOR_DELIVERY': return 'Out for delivery';
      case 'DELIVERED':        return 'Delivered';
      case 'CANCELLED':        return 'Cancelled';
      case 'RETURNED':         return 'Returned';
      default:                 return status;
    }
  }

  statusBadgeClass(status: string): string {
    switch (status?.toUpperCase()) {
      case 'PLACED':
      case 'PENDING':           return 'bg-yellow-100 text-yellow-700';
      case 'ACCEPTED':          return 'bg-blue-100 text-blue-700';
      case 'IN_PRODUCTION':     return 'bg-sky-100 text-sky-700';
      case 'PROCESSING':        return 'bg-orange-100 text-orange-700';
      case 'SHIPPED':           return 'bg-purple-100 text-purple-700';
      case 'OUT_FOR_DELIVERY':  return 'bg-indigo-100 text-indigo-700';
      case 'DELIVERED':         return 'bg-emerald-100 text-emerald-700';
      case 'CANCELLED':         return 'bg-red-100 text-red-600';
      case 'RETURNED':          return 'bg-amber-100 text-amber-700';
      default:                  return 'bg-gray-100 text-gray-600';
    }
  }

  private normalizeStatus(raw: string): OrderStatus {
    const upper = (raw || '').toUpperCase();
    return upper === 'PENDING' ? 'PLACED' : (upper as OrderStatus);
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleString('en-GB', {
      day: 'numeric', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  }

  formatAmount(amount: number | string): string {
    return Number(amount).toLocaleString('en-BD', { minimumFractionDigits: 2 });
  }

  shortId(id: string): string {
    return id ? '#' + id.slice(0, 8).toUpperCase() : '—';
  }

  /** "Size M" label from an order line's snapshotted variant (size-only). */
  variantLabel(item: OrderItem): string {
    if (item.variantSize) return `Size ${item.variantSize}`;
    return item.variantName ?? '';
  }

  /** Render the custom-measurements JSON ("{"Chest":38,"Length":40,"comments":"…"}") as readable text. */
  customMeasurementText(item: OrderItem): string {
    if (!item.customMeasurements) return '';
    try {
      const data = JSON.parse(item.customMeasurements) as Record<string, unknown>;
      const parts: string[] = [];
      for (const [key, value] of Object.entries(data)) {
        if (value == null || value === '') continue;
        const label = key === 'comments' ? 'Notes' : key;
        parts.push(`${label}: ${value}`);
      }
      return parts.join(' · ');
    } catch {
      return item.customMeasurements;
    }
  }

  subtotal(): number {
    const o = this.order();
    if (!o) return 0;
    return o.orderItems.reduce((sum, item) => sum + item.priceAtOrder * item.quantity, 0);
  }

  grandTotal(): number {
    const o = this.order();
    if (!o) return 0;
    return this.subtotal() + Number(o.deliveryCharge ?? 0);
  }
}
