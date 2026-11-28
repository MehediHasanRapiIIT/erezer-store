import { Component, computed, input } from '@angular/core';
import { InvoiceSentMark } from '../../../core/services/invoice-app.service';

/** "In Invoice ✓" beside an order that reached Erezer Invoice; hover says when and by whom. */
@Component({
  selector: 'app-invoice-mark',
  standalone: true,
  template: `
    @if (mark(); as m) {
      <span class="inline-flex items-center gap-1 rounded-full border border-indigo-200 bg-indigo-50 px-2 py-0.5 text-[11px] font-semibold text-indigo-700 whitespace-nowrap"
        [title]="title()" data-testid="invoice-mark">
        In Invoice ✓
      </span>
    }
  `,
})
export class InvoiceMarkComponent {
  readonly mark = input<InvoiceSentMark | undefined | null>(null);

  protected readonly title = computed(() => {
    const m = this.mark();
    if (!m) return '';
    const when = new Date(m.sentAt).toLocaleString('en-GB', {
      day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit', hour12: true, timeZone: 'Asia/Dhaka',
    });
    const parts = [`Sent to Erezer Invoice ${when}${m.sentBy ? ' by ' + m.sentBy : ''}`];
    if (m.invoiceOrderId) parts.push(`Erezer Invoice order #${m.invoiceOrderId}`);
    if (m.missingPhotos > 0) parts.push(`${m.missingPhotos} photo${m.missingPhotos === 1 ? '' : 's'} missing`);
    return parts.join(' · ');
  });
}
