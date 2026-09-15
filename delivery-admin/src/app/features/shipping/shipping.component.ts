import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import {
  ShippingRulesChange,
  ShippingService,
  ShippingSettings,
  ShippingZone,
} from '../../core/services/shipping.service';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

/**
 * Shipping prices and the free-shipping rules. Shipping is charged on every
 * order unless "free shipping for all orders" or the free-shipping offer is on.
 */
@Component({
  selector: 'app-shipping',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center flex-shrink-0">
          <h1 class="text-lg font-bold text-gray-900">Shipping</h1>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-3xl mx-auto space-y-5">

            @if (errorMessage()) {
              <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ errorMessage() }}</p>
            }
            @if (!canEdit()) {
              <p class="rounded-md bg-gray-100 px-3 py-2 text-xs text-gray-500">
                You can see these settings. Changing them needs the “Change shipping prices and free shipping” permission.
              </p>
            }

            @if (settings(); as s) {
              <!-- What customers get right now, in one sentence. -->
              <section class="rounded-xl border px-5 py-4"
                [class.border-emerald-200]="s.freeAll || s.offerEnabled" [class.bg-emerald-50]="s.freeAll || s.offerEnabled"
                [class.border-gray-200]="!s.freeAll && !s.offerEnabled" [class.bg-white]="!s.freeAll && !s.offerEnabled">
                <p class="text-xs font-semibold uppercase tracking-wide text-gray-400">Right now</p>
                <p class="mt-1 font-semibold text-gray-900" data-testid="shipping-summary">{{ summary() }}</p>
              </section>

              <!-- Free shipping for all orders -->
              <section class="rounded-xl border border-gray-200 bg-white px-5 py-4">
                <div class="flex items-center justify-between gap-4">
                  <div>
                    <h2 class="font-bold text-gray-900">Free shipping for all orders</h2>
                    <p class="text-xs text-gray-500 mt-0.5">
                      On: no order pays shipping, whatever its size — for a sale or a holiday. Off: shipping is charged.
                    </p>
                  </div>
                  <button type="button" role="switch" [attr.aria-checked]="s.freeAll"
                    aria-label="Free shipping for all orders" (click)="toggleFreeAll()"
                    [disabled]="saving() || !canEdit()"
                    class="relative inline-flex h-7 flex-shrink-0 rounded-full transition-colors disabled:opacity-50"
                    [class.bg-emerald-500]="s.freeAll" [class.bg-gray-300]="!s.freeAll" style="width:3.25rem">
                    <span class="absolute top-1 h-5 w-5 rounded-full bg-white shadow transition-all"
                      [style.left]="s.freeAll ? '1.75rem' : '0.25rem'"></span>
                  </button>
                </div>
              </section>

              <!-- Free shipping offer -->
              <section class="rounded-xl border border-gray-200 bg-white px-5 py-4 space-y-3"
                [class.opacity-60]="s.freeAll">
                <div class="flex items-center justify-between gap-4">
                  <div>
                    <h2 class="font-bold text-gray-900">Free shipping offer</h2>
                    <p class="text-xs text-gray-500 mt-0.5">
                      Orders of at least this amount ship free; smaller orders pay shipping. The amount is what the
                      customer pays for the products, after any discount or promo code.
                    </p>
                    @if (s.freeAll) {
                      <p class="text-xs text-amber-700 mt-1">Not used while free shipping for all orders is on.</p>
                    }
                  </div>
                  <button type="button" role="switch" [attr.aria-checked]="s.offerEnabled"
                    aria-label="Free shipping offer" (click)="toggleOffer()"
                    [disabled]="saving() || !canEdit()"
                    class="relative inline-flex h-7 flex-shrink-0 rounded-full transition-colors disabled:opacity-50"
                    [class.bg-emerald-500]="s.offerEnabled" [class.bg-gray-300]="!s.offerEnabled" style="width:3.25rem">
                    <span class="absolute top-1 h-5 w-5 rounded-full bg-white shadow transition-all"
                      [style.left]="s.offerEnabled ? '1.75rem' : '0.25rem'"></span>
                  </button>
                </div>
                <div class="flex flex-wrap items-end gap-2">
                  <label class="text-xs font-medium text-gray-600">
                    Free shipping for orders from
                    <div class="mt-1 flex items-center rounded-lg border border-gray-300 bg-white focus-within:border-blue-500">
                      <span class="pl-3 text-sm text-gray-400">৳</span>
                      <input type="number" min="1" step="1" [(ngModel)]="offerInput" name="offerMin"
                        aria-label="Free shipping minimum order amount"
                        [disabled]="!canEdit()" placeholder="2000"
                        class="w-32 rounded-lg px-2 py-1.5 text-sm outline-none" />
                    </div>
                  </label>
                  <button type="button" (click)="saveOfferMin()"
                    [disabled]="saving() || !canEdit() || !offerAmountChanged()"
                    class="px-3 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-40">
                    Save amount
                  </button>
                </div>
              </section>

              <!-- Zone prices -->
              <section class="rounded-xl border border-gray-200 bg-white overflow-hidden">
                <div class="px-5 py-4 border-b border-gray-100">
                  <h2 class="font-bold text-gray-900">Shipping prices</h2>
                  <p class="text-xs text-gray-500 mt-0.5">What each area pays when shipping is charged.</p>
                </div>
                <table class="w-full text-sm">
                  <tbody class="divide-y divide-gray-50">
                    @for (z of s.zones; track z.id) {
                      <tr>
                        <td class="px-5 py-3">
                          <p class="font-medium text-gray-900">{{ z.displayName }}</p>
                          @if (z.isActive === false) {
                            <p class="text-xs text-gray-400">Not offered at checkout</p>
                          }
                        </td>
                        <td class="px-5 py-3">
                          <div class="flex items-center justify-end gap-2">
                            <div class="flex items-center rounded-lg border border-gray-300 bg-white focus-within:border-blue-500">
                              <span class="pl-3 text-sm text-gray-400">৳</span>
                              <input type="number" min="0" step="1" [(ngModel)]="feeInputs[z.id]" [name]="'fee' + z.id"
                                [attr.aria-label]="z.displayName + ' shipping price'" [disabled]="!canEdit()"
                                class="w-24 rounded-lg px-2 py-1.5 text-sm outline-none" />
                            </div>
                            <button type="button" (click)="saveFee(z)"
                              [disabled]="saving() || !canEdit() || !feeChanged(z)"
                              [attr.aria-label]="'Save ' + z.displayName + ' price'"
                              class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-40">
                              Save
                            </button>
                          </div>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </section>
            } @else if (loading()) {
              <p class="text-sm text-gray-400">Loading…</p>
            }
          </div>
        </main>
      </div>
    </div>
  `,
})
export class ShippingComponent implements OnInit {
  private readonly api = inject(ShippingService);
  protected readonly perms = inject(PermissionService);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  readonly settings = signal<ShippingSettings | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal('');

  protected offerInput: number | null = null;
  protected feeInputs: Record<number, number | null> = {};

  protected readonly canEdit = computed(() => this.perms.can('shipping.edit'));

  protected readonly summary = computed(() => {
    const s = this.settings();
    if (!s) return '';
    if (s.freeAll) return 'Every order ships free.';
    if (s.offerEnabled && s.offerMin) {
      return `Orders from ${this.taka(s.offerMin)} ship free. Smaller orders pay shipping.`;
    }
    return 'Every order pays shipping.';
  });

  ngOnInit(): void {
    this.api.get().subscribe({
      next: (s) => { this.apply(s); this.loading.set(false); },
      error: (err) => { this.errorMessage.set(parseApiError(err)); this.loading.set(false); },
    });
  }

  protected feeChanged(z: ShippingZone): boolean {
    const v = this.feeInputs[z.id];
    return v != null && v >= 0 && Number(v) !== Number(z.flatFee);
  }

  protected offerAmountChanged(): boolean {
    const v = this.offerInput;
    return v != null && v > 0 && Number(v) !== Number(this.settings()?.offerMin ?? NaN);
  }

  protected async toggleFreeAll(): Promise<void> {
    const s = this.settings();
    if (!s) return;
    const turnOn = !s.freeAll;
    if (turnOn) {
      const ok = await this.confirmer.ask({
        title: 'Free shipping for all orders?',
        message: 'No order will pay shipping until you turn this off again.',
        confirmLabel: 'Turn on',
      });
      if (!ok) return;
    }
    this.save({ freeAll: turnOn },
      turnOn ? 'Free shipping for all orders is on' : 'Free shipping for all orders is off',
      turnOn ? 'No order pays shipping.' : 'Shipping is charged again.');
  }

  protected toggleOffer(): void {
    const s = this.settings();
    if (!s) return;
    const turnOn = !s.offerEnabled;
    if (turnOn) {
      // The amount typed in the box, even if not saved yet, goes with the switch.
      const amount = this.offerInput ?? s.offerMin;
      if (!amount || amount <= 0) {
        this.errorMessage.set('Type the order amount for free shipping first.');
        return;
      }
      this.save({ offerEnabled: true, offerMin: Number(amount) },
        'Free shipping offer is on', `Orders from ${this.taka(Number(amount))} ship free.`);
    } else {
      this.save({ offerEnabled: false }, 'Free shipping offer is off', 'Every order pays shipping.');
    }
  }

  protected saveOfferMin(): void {
    const amount = Number(this.offerInput);
    this.save({ offerMin: amount }, 'Free shipping amount saved', `Orders from ${this.taka(amount)}.`);
  }

  protected saveFee(z: ShippingZone): void {
    const fee = Number(this.feeInputs[z.id]);
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.updateZoneFee(z.id, fee).subscribe({
      next: (s) => {
        this.apply(s);
        this.saving.set(false);
        this.notices.success('Shipping price saved', `${z.displayName}: ${this.taka(fee)}`);
      },
      error: (err) => { this.saving.set(false); this.errorMessage.set(parseApiError(err)); },
    });
  }

  private save(change: ShippingRulesChange, title: string, message: string): void {
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.updateRules(change).subscribe({
      next: (s) => {
        this.apply(s);
        this.saving.set(false);
        this.notices.success(title, message);
      },
      error: (err) => { this.saving.set(false); this.errorMessage.set(parseApiError(err)); },
    });
  }

  private apply(s: ShippingSettings): void {
    this.settings.set(s);
    this.offerInput = s.offerMin;
    this.feeInputs = Object.fromEntries(s.zones.map((z) => [z.id, z.flatFee]));
  }

  private taka(amount: number): string {
    return '৳' + amount.toLocaleString('en-US', { maximumFractionDigits: 2 });
  }
}
