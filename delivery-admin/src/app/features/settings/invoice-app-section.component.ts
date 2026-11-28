import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InvoiceAppChange, InvoiceAppService, InvoiceAppSettings } from '../../core/services/invoice-app.service';
import { NoticeService } from '../../core/services/notice.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { parseApiError } from '../../core/utils/api-error.util';

/**
 * Settings → Erezer Invoice: where the owner's order system is, and the key it
 * gave the shop. Staff then tick orders and send them there. The key goes in
 * from here and never comes back, only its last letters.
 */
@Component({
  selector: 'app-invoice-app-section',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-4" data-testid="invoice-app" aria-labelledby="invoice-app-heading">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="invoice-app-heading" class="text-base font-semibold">Erezer Invoice</h2>
          <p class="text-xs text-gray-500">
            Your order system. Staff tick orders on the Orders page and choose <strong>Send to Erezer Invoice</strong>:
            they arrive there with their product photos, platform Website.
          </p>
        </div>
        @if (settings(); as s) {
          <span class="rounded-full px-2.5 py-1 text-xs font-semibold" data-testid="invoice-app-status"
            [class]="s.connected ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : 'bg-gray-100 text-gray-600 border border-gray-200'">
            {{ s.connected ? 'Connected' : 'Not connected' }}
          </span>
        }
      </div>

      @if (settings(); as s) {
        <label class="block text-xs font-medium text-gray-600">
          Erezer Invoice's address
          <input [ngModel]="url()" (ngModelChange)="url.set($event)" maxlength="300" placeholder="https://invoice.erezer.com"
            data-testid="invoice-app-url" class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm font-mono" />
          <span class="mt-1 block font-normal text-gray-400">As it opens in the browser. Erezer Invoice shows it on its Shop connection page.</span>
        </label>

        <div class="text-xs font-medium text-gray-600">
          Shop key
          @if (s.keySaved && !replacingKey()) {
            <div class="mt-1 flex flex-wrap items-center gap-3">
              <span class="text-sm font-normal text-gray-700" data-testid="invoice-app-key-hint">Saved, ending <span class="font-mono">{{ s.keyHint }}</span></span>
              <button type="button" (click)="replacingKey.set(true)" class="text-sm font-normal text-blue-600 hover:underline">Replace</button>
              <button type="button" (click)="removeKey()" class="text-sm font-normal text-red-600 hover:underline">Remove</button>
            </div>
          } @else {
            <div class="mt-1 flex gap-2">
              <input [ngModel]="key()" (ngModelChange)="key.set($event)" type="password" autocomplete="off" maxlength="200"
                placeholder="ezs_…" data-testid="invoice-app-key"
                class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm font-mono" />
              @if (s.keySaved) {
                <button type="button" (click)="replacingKey.set(false); key.set('')" class="text-sm font-normal text-gray-500 hover:text-gray-800">Cancel</button>
              }
            </div>
            <span class="mt-1 block font-normal text-gray-400">
              In Erezer Invoice open <strong>Shop connection</strong>, choose <strong>Make a key</strong>, copy it and paste it here.
            </span>
          }
        </div>

        <div class="flex flex-wrap gap-2">
          <button type="button" (click)="save()" [disabled]="saving()" data-testid="invoice-app-save"
            class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
            {{ saving() ? 'Saving…' : 'Save' }}
          </button>
          <button type="button" (click)="test()" [disabled]="testing() || !s.connected" data-testid="invoice-app-test"
            [title]="s.connected ? 'Asks Erezer Invoice whether it accepts the key. Nothing is sent.' : 'Save the address and the key first'"
            class="px-3 py-1.5 text-sm font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50">
            {{ testing() ? 'Checking…' : 'Test the connection' }}
          </button>
        </div>
        @if (testResult(); as t) {
          <p class="rounded-md px-3 py-2 text-sm" data-testid="invoice-app-test-result"
            [class]="t.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'">{{ t.message }}</p>
        }
      } @else if (loadError()) {
        <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ loadError() }}</p>
      }
    </section>
  `,
})
export class InvoiceAppSectionComponent implements OnInit {
  private readonly api = inject(InvoiceAppService);
  private readonly notices = inject(NoticeService);
  private readonly confirm = inject(ConfirmService);

  protected readonly settings = signal<InvoiceAppSettings | null>(null);
  protected readonly loadError = signal('');
  protected readonly url = signal('');
  protected readonly key = signal('');
  protected readonly replacingKey = signal(false);
  protected readonly saving = signal(false);
  protected readonly testing = signal(false);
  protected readonly testResult = signal<{ ok: boolean; message: string } | null>(null);

  ngOnInit(): void {
    this.api.get().subscribe({
      next: (s) => this.show(s),
      error: (err) => this.loadError.set(parseApiError(err)),
    });
  }

  protected save(): void {
    const change: InvoiceAppChange = { url: this.url().trim() };
    if (this.key().trim()) change.key = this.key().trim();
    this.store(change, 'Erezer Invoice settings saved');
  }

  protected async removeKey(): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Remove the shop key?',
      message: 'Orders can\'t be sent to Erezer Invoice until a key is saved again.',
      confirmLabel: 'Remove key',
      danger: true,
    });
    if (ok) this.store({ removeKey: true }, 'Shop key removed');
  }

  protected test(): void {
    this.testing.set(true);
    this.testResult.set(null);
    this.api.test().subscribe({
      next: (r) => {
        this.testing.set(false);
        this.testResult.set({ ok: true, message: r.message });
      },
      error: (err) => {
        this.testing.set(false);
        this.testResult.set({ ok: false, message: parseApiError(err) });
      },
    });
  }

  private store(change: InvoiceAppChange, done: string): void {
    this.saving.set(true);
    this.testResult.set(null);
    this.api.update(change).subscribe({
      next: (s) => {
        this.saving.set(false);
        this.show(s);
        this.notices.success(done);
      },
      error: (err) => {
        this.saving.set(false);
        this.notices.error('Not saved', parseApiError(err));
      },
    });
  }

  private show(s: InvoiceAppSettings): void {
    this.settings.set(s);
    this.url.set(s.url ?? '');
    this.key.set('');
    this.replacingKey.set(false);
  }
}
