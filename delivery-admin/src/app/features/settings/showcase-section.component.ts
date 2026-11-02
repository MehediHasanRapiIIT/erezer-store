import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ShowcaseService, ShowcaseSettings } from '../../core/services/showcase.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';
import { ProductMultiPickerComponent } from '../../shared/product-picker/product-multi-picker.component';

/**
 * The Settings page's "Showcase screen" section, saved on its own: the in-store
 * screen at /showcase that plays products, bundle offers and flash sales by itself.
 */
@Component({
  selector: 'app-showcase-section',
  standalone: true,
  imports: [FormsModule, ProductMultiPickerComponent],
  template: `
    <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-4" data-testid="showcase-settings">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 class="text-base font-semibold">Showcase screen</h2>
          <p class="text-xs text-gray-500">
            An advert that plays by itself on the screen in your store: open <strong>{{ shopAddress }}/showcase</strong>
            in the screen's browser and press <strong>Full screen</strong> (or F11). It shows your products, and your
            bundle offers and flash sales while they run, and picks up changes by itself every few minutes.
          </p>
        </div>
        @if (canEdit()) {
          <button type="button" (click)="save()" [disabled]="saving() || !model" data-testid="showcase-save"
            class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
            {{ saving() ? 'Saving…' : 'Save' }}
          </button>
        }
      </div>
      @if (!canEdit()) {
        <p class="text-xs text-gray-400">Needs the “Edit policies and support contacts” permission.</p>
      }
      @if (error()) {
        <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" data-testid="showcase-error">{{ error() }}</p>
      }

      @if (model; as m) {
        <fieldset [disabled]="!canEdit()" class="min-w-0 space-y-4 disabled:opacity-60">
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <label class="flex items-center gap-3 text-sm">
              <input type="checkbox" [(ngModel)]="m.enabled" class="h-4 w-4" data-testid="showcase-enabled" />
              <span>Playing <span class="block text-xs text-gray-400">Off: the screen shows only the Erezer name.</span></span>
            </label>
            <label class="text-xs font-medium text-gray-600">
              Seconds for each slide
              <input type="number" min="3" max="30" [(ngModel)]="m.slideSeconds" data-testid="showcase-seconds"
                class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
              <span class="mt-1 block font-normal text-gray-400">3 to 30. Offers stay half as long again.</span>
            </label>
            <label class="flex items-center gap-3 text-sm">
              <input type="checkbox" [(ngModel)]="m.showQr" class="h-4 w-4" data-testid="showcase-qr" />
              <span>QR codes <span class="block text-xs text-gray-400">Customers scan one to open that product on their phone.</span></span>
            </label>
          </div>

          <div class="rounded-lg border border-gray-200 p-4 space-y-3">
            <h3 class="text-sm font-semibold text-gray-800">Which products it shows</h3>
            <div class="flex flex-wrap gap-4 text-sm">
              <label class="flex items-center gap-2"><input type="radio" name="showcase-mode" value="NEW" [(ngModel)]="m.productMode" data-testid="showcase-mode-new" /> New arrivals first</label>
              <label class="flex items-center gap-2"><input type="radio" name="showcase-mode" value="ALL" [(ngModel)]="m.productMode" data-testid="showcase-mode-all" /> All products</label>
              <label class="flex items-center gap-2"><input type="radio" name="showcase-mode" value="PICKED" [(ngModel)]="m.productMode" data-testid="showcase-mode-picked" /> Only the ones I tick</label>
            </div>
            @if (m.productMode === 'PICKED') {
              <app-product-multi-picker label="Products on the screen" [selected]="m.productIds" (selectedChange)="m.productIds = $event" />
              <p class="text-xs text-gray-400">They play in the order you tick them. Products that are out of stock are skipped.</p>
            }
          </div>
        </fieldset>
      } @else if (!error()) {
        <p class="text-sm text-gray-400">Loading…</p>
      }
    </section>
  `,
})
export class ShowcaseSectionComponent implements OnInit {
  private readonly service = inject(ShowcaseService);
  private readonly notices = inject(NoticeService);

  readonly canEdit = input(false);

  protected model: ShowcaseSettings | null = null;
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  /** The shop's address, from the admin's own (admin.erezer.com → erezer.com). */
  protected readonly shopAddress = location.host.replace(/^admin\./, '').replace(/:4300$/, ':4200');

  ngOnInit(): void {
    this.service.get().subscribe({
      next: (s) => (this.model = { ...s, productIds: [...s.productIds] }),
      error: (err: HttpErrorResponse) => this.error.set(parseApiError(err)),
    });
  }

  protected save(): void {
    if (!this.model) return;
    this.saving.set(true);
    this.error.set('');
    this.service.save({ ...this.model, slideSeconds: Number(this.model.slideSeconds) }).subscribe({
      next: (s) => {
        this.model = { ...s, productIds: [...s.productIds] };
        this.saving.set(false);
        this.notices.success('Showcase screen saved');
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(parseApiError(err));
      },
    });
  }
}
