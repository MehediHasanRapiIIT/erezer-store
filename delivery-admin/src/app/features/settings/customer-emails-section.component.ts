import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { CustomerEmailSettings, CustomerEmailsService } from '../../core/services/customer-emails.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

/**
 * The Settings page's "Customer emails" section, saved on its own: where the
 * "delivered" email invites a review, the shop's address in every email's
 * footer, and the switches for the cart reminder and newsletter emails.
 */
@Component({
  selector: 'app-customer-emails-section',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-4" data-testid="customer-emails">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 class="text-base font-semibold">Customer emails</h2>
          <p class="text-xs text-gray-500">
            A customer gets an email when their order is <strong>accepted</strong>, <strong>shipped</strong>,
            <strong>delivered</strong> or <strong>cancelled</strong>. Placing an order sends an SMS instead, and the steps in between send nothing.
          </p>
        </div>
        @if (canEdit()) {
          <button type="button" (click)="save()" [disabled]="saving() || !model" data-testid="customer-emails-save"
            class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
            {{ saving() ? 'Saving…' : 'Save' }}
          </button>
        }
      </div>
      @if (!canEdit()) {
        <p class="text-xs text-gray-400">Needs the “Edit policies and support contacts” permission.</p>
      }
      @if (error()) {
        <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" data-testid="customer-emails-error">{{ error() }}</p>
      }

      @if (model; as m) {
        <fieldset [disabled]="!canEdit()" class="min-w-0 space-y-4 disabled:opacity-60">
          <div class="rounded-lg border border-gray-200 p-4 space-y-3">
            <h3 class="text-sm font-semibold text-gray-800">“How was your order?” in the delivered email</h3>
            <label class="flex items-center gap-3 text-sm">
              <input type="checkbox" [(ngModel)]="m.reviewOnWebsite" class="h-4 w-4" data-testid="review-website" />
              <span>Link to each product's reviews on the website</span>
            </label>
            <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <label class="text-xs font-medium text-gray-600">
                Facebook page message link
                <input [(ngModel)]="m.facebookUrl" placeholder="https://m.me/erezer" data-testid="review-facebook"
                  class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
              </label>
              <label class="text-xs font-medium text-gray-600">
                Instagram message link
                <input [(ngModel)]="m.instagramUrl" placeholder="https://ig.me/m/erezer" data-testid="review-instagram"
                  class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
              </label>
              <label class="text-xs font-medium text-gray-600">
                WhatsApp number
                <input [(ngModel)]="m.whatsappNumber" placeholder="01712-345678" data-testid="review-whatsapp"
                  class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
              </label>
            </div>
            <p class="text-xs text-gray-400">Leave a box empty to leave that button out. Facebook's m.me link and Instagram's ig.me link open a message to your page.</p>
          </div>

          <label class="block text-xs font-medium text-gray-600">
            Shop address (shown at the bottom of every email)
            <input [(ngModel)]="m.businessAddress" maxlength="300" placeholder="House 12, Road 5, Dhanmondi, Dhaka 1209" data-testid="business-address"
              class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
            <span class="mt-1 block font-normal text-gray-400">A real address and contact details make emails look like a genuine business to mail services.</span>
          </label>

          <div class="rounded-lg border border-amber-200 bg-amber-50/50 p-4 space-y-2">
            <h3 class="text-sm font-semibold text-gray-800">Marketing emails</h3>
            <p class="text-xs text-gray-600">These share the shop's mailbox (about 100 emails a day) with the order emails, so they are best kept off until they move to their own email service.</p>
            <label class="flex items-center gap-3 text-sm">
              <input type="checkbox" [(ngModel)]="m.cartReminderEnabled" class="h-4 w-4" data-testid="cart-reminder" />
              <span>Cart reminder (“Your Erezer cart is still waiting”)</span>
            </label>
            <label class="flex items-center gap-3 text-sm">
              <input type="checkbox" [(ngModel)]="m.newsletterEnabled" class="h-4 w-4" data-testid="newsletter-on" />
              <span>Newsletter campaigns can be sent</span>
            </label>
          </div>
        </fieldset>
      } @else if (!error()) {
        <p class="text-sm text-gray-400">Loading…</p>
      }
    </section>
  `,
})
export class CustomerEmailsSectionComponent implements OnInit {
  private readonly service = inject(CustomerEmailsService);
  private readonly notices = inject(NoticeService);

  readonly canEdit = input(false);

  protected model: CustomerEmailSettings | null = null;
  protected readonly saving = signal(false);
  protected readonly error = signal('');

  ngOnInit(): void {
    this.service.get().subscribe({
      next: (s) => (this.model = { ...s }),
      error: (err: HttpErrorResponse) => this.error.set(parseApiError(err)),
    });
  }

  protected save(): void {
    if (!this.model) return;
    this.saving.set(true);
    this.error.set('');
    this.service.save(this.model).subscribe({
      next: (s) => {
        this.model = { ...s };
        this.saving.set(false);
        this.notices.success('Customer emails saved');
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(parseApiError(err));
      },
    });
  }
}
