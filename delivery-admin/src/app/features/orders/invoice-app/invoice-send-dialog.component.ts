import { Component, HostListener, computed, input, output } from '@angular/core';
import { InvoiceSendResult } from '../../../core/services/invoice-app.service';

/**
 * The window that opens when orders go to Erezer Invoice: "Sending…" while they
 * go, then one line per order (sent, already there, not sent and why).
 */
@Component({
  selector: 'app-invoice-send-dialog',
  standalone: true,
  template: `
    @if (sending() || results()) {
      <div class="fixed inset-0 z-[55] flex items-center justify-center bg-black/50 p-4" (click)="close()">
        <div role="dialog" aria-modal="true" aria-label="Send to Erezer Invoice" data-testid="invoice-send-dialog"
          class="flex max-h-[85vh] w-full max-w-lg flex-col rounded-xl bg-white shadow-xl" (click)="$event.stopPropagation()">
          <div class="border-b border-gray-100 px-5 py-4">
            <h2 class="text-base font-bold text-gray-900">Send to Erezer Invoice</h2>
            @if (results(); as list) {
              <p class="mt-0.5 text-sm text-gray-600" data-testid="invoice-send-summary">{{ summary() }}</p>
            }
          </div>

          <div class="flex-1 overflow-y-auto px-5 py-3">
            @if (!results()) {
              <div class="flex items-center gap-3 py-6 text-sm text-gray-600">
                <svg class="h-5 w-5 animate-spin text-blue-500" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
                </svg>
                Sending {{ sending() }} {{ sending() === 1 ? 'order' : 'orders' }} with their photos…
              </div>
            } @else {
              <ul class="divide-y divide-gray-100">
                @for (r of results(); track r.orderId) {
                  <li class="flex items-start gap-3 py-2.5" [attr.data-testid]="'invoice-result-' + r.outcome">
                    <span class="mt-0.5 text-base leading-none" aria-hidden="true">{{ icon(r) }}</span>
                    <div class="min-w-0 flex-1">
                      <p class="text-sm">
                        <span class="font-mono font-semibold text-gray-800">{{ r.orderNumber || 'Order' }}</span>
                        <span class="ml-2" [class]="textClass(r)">{{ label(r) }}</span>
                      </p>
                      @if (r.outcome === 'FAILED' && r.message) {
                        <p class="mt-0.5 text-xs text-red-700">{{ r.message }}</p>
                      }
                      @if (r.missingPhotos > 0) {
                        <p class="mt-0.5 text-xs text-amber-700">
                          ⚠️ {{ r.missingPhotos }} product {{ r.missingPhotos === 1 ? 'photo' : 'photos' }} couldn't go with it. Add {{ r.missingPhotos === 1 ? 'it' : 'them' }} in Erezer Invoice if needed.
                        </p>
                      }
                    </div>
                  </li>
                }
              </ul>
            }
          </div>

          <div class="flex justify-end border-t border-gray-100 px-5 py-3">
            <button type="button" (click)="close()" [disabled]="!results()" data-testid="invoice-send-close"
              class="rounded-lg bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800 disabled:opacity-40">
              Close
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class InvoiceSendDialogComponent {
  /** How many orders are on their way; 0 when none. */
  readonly sending = input(0);
  /** The answer; null while sending. */
  readonly results = input<InvoiceSendResult[] | null>(null);
  readonly closed = output<void>();

  protected readonly summary = computed(() => summarise(this.results() ?? []));

  protected close(): void {
    // Nothing to close onto while the orders are still going.
    if (this.results()) this.closed.emit();
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.close();
  }

  protected icon(r: InvoiceSendResult): string {
    return r.outcome === 'SENT' ? '✅' : r.outcome === 'ALREADY_THERE' ? '↩️' : '❌';
  }

  protected label(r: InvoiceSendResult): string {
    if (r.outcome === 'SENT') return r.invoiceOrderId ? `Sent (Erezer Invoice order #${r.invoiceOrderId})` : 'Sent';
    if (r.outcome === 'ALREADY_THERE') return 'Already in Erezer Invoice: nothing changed';
    return 'Not sent';
  }

  protected textClass(r: InvoiceSendResult): string {
    return r.outcome === 'SENT' ? 'text-emerald-700' : r.outcome === 'ALREADY_THERE' ? 'text-gray-600' : 'text-red-700';
  }
}

/** "2 sent, 1 already there, 1 not sent". */
export function summarise(results: InvoiceSendResult[]): string {
  const count = (o: InvoiceSendResult['outcome']) => results.filter((r) => r.outcome === o).length;
  const parts: string[] = [];
  if (count('SENT')) parts.push(`${count('SENT')} sent`);
  if (count('ALREADY_THERE')) parts.push(`${count('ALREADY_THERE')} already there`);
  if (count('FAILED')) parts.push(`${count('FAILED')} not sent`);
  return parts.join(', ') || 'Nothing was sent';
}
