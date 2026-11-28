import { TestBed } from '@angular/core/testing';
import { InvoiceSendResult } from '../../../core/services/invoice-app.service';
import { InvoiceSendDialogComponent, summarise } from './invoice-send-dialog.component';
import { buildPermissionGraph } from '../../staff/permission-deps';

const result = (orderNumber: string, outcome: InvoiceSendResult['outcome'], extra: Partial<InvoiceSendResult> = {}): InvoiceSendResult => ({
  orderId: orderNumber, orderNumber, outcome, invoiceOrderId: null, message: null, missingPhotos: 0, ...extra,
});

describe('Send to Erezer Invoice', () => {
  it('sums up how the orders went', () => {
    expect(summarise([result('EZ-1', 'SENT'), result('EZ-2', 'SENT'), result('EZ-3', 'ALREADY_THERE'), result('EZ-4', 'FAILED')]))
      .toBe('2 sent, 1 already there, 1 not sent');
    expect(summarise([result('EZ-1', 'FAILED')])).toBe('1 not sent');
    expect(summarise([])).toBe('Nothing was sent');
  });

  it('shows each order with its reason and missing photos', () => {
    const fixture = TestBed.createComponent(InvoiceSendDialogComponent);
    fixture.componentRef.setInput('sending', 3);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Sending 3 orders with their photos…');

    fixture.componentRef.setInput('results', [
      result('EZ-1', 'SENT', { invoiceOrderId: 153, missingPhotos: 1 }),
      result('EZ-2', 'ALREADY_THERE'),
      result('EZ-3', 'FAILED', { message: 'Cancelled orders can\'t be sent.' }),
    ]);
    fixture.detectChanges();
    const text = el.textContent ?? '';
    expect(text).toContain('1 sent, 1 already there, 1 not sent');
    expect(text).toContain('Sent (Erezer Invoice order #153)');
    expect(text).toContain('1 product photo couldn\'t go with it');
    expect(text).toContain('Already in Erezer Invoice');
    expect(text).toContain('Cancelled orders can\'t be sent.');
  });

  it('closes only once the answer is in', () => {
    const fixture = TestBed.createComponent(InvoiceSendDialogComponent);
    let closed = 0;
    fixture.componentInstance.closed.subscribe(() => closed++);
    fixture.componentRef.setInput('sending', 1);
    fixture.detectChanges();
    const close = () => (fixture.nativeElement.querySelector('[data-testid="invoice-send-close"]') as HTMLButtonElement);
    expect(close().disabled).toBe(true);

    fixture.componentRef.setInput('results', [result('EZ-1', 'SENT')]);
    fixture.detectChanges();
    close().click();
    expect(closed).toBe(1);
  });

  it('ticking "Send orders to Erezer Invoice" also ticks "See orders"', () => {
    const graph = buildPermissionGraph(['orders.view', 'orders.invoice_app.send', 'settings.view', 'settings.invoice_app']);
    expect(graph.needs('orders.invoice_app.send')).toContain('orders.view');
    expect(graph.needs('settings.invoice_app')).toContain('settings.view');
  });
});
