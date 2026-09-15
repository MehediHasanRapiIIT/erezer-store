import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '../core/i18n/translate.pipe';

/**
 * Thank you, after an order placed without signing in: the order number to keep,
 * a way to copy it, and the way to track it. The number is in the address, so a
 * refresh or a bookmark still shows it.
 */
@Component({
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  template: `
    <section class="mx-auto max-w-xl px-1 py-16 text-center">
      <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-3xl text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300" aria-hidden="true">✓</div>
      <h1 class="app-section-title mt-6">{{ 'placed.title' | t }}</h1>
      <p class="app-muted mt-2">{{ 'placed.subtitle' | t }}</p>

      @if (number()) {
        <div class="app-card mx-auto mt-8 max-w-sm p-6">
          <p class="text-xs uppercase tracking-[0.2em] text-neutral-500 dark:text-neutral-400">{{ 'placed.your_number' | t }}</p>
          <p class="mt-2 text-3xl font-semibold tracking-wider" data-testid="placed-number">{{ number() }}</p>
          <button type="button" (click)="copy()"
            class="mt-3 inline-flex items-center gap-1.5 rounded-full border border-neutral-300 px-3 py-1 text-xs font-medium transition hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-800">
            {{ copied() ? ('placed.copied' | t) : ('placed.copy' | t) }}
          </button>
          <p class="app-muted mt-4 text-xs">
            @if (email()) {
              {{ 'placed.email_sent' | t }} <strong>{{ email() }}</strong>.
            } @else {
              {{ 'placed.keep_number' | t }}
            }
          </p>
        </div>
      }

      <div class="mt-8 flex flex-wrap justify-center gap-3">
        @if (number()) {
          <a [routerLink]="['/track-order']" [queryParams]="{ number: number() }" class="btn-primary !rounded-full">{{ 'placed.track' | t }}</a>
        }
        <a routerLink="/shop" class="btn-secondary !rounded-full">{{ 'placed.continue' | t }}</a>
      </div>
    </section>
  `,
})
export class OrderPlacedPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly number = signal('');
  protected readonly email = signal('');
  protected readonly copied = signal(false);

  ngOnInit(): void {
    this.number.set(this.route.snapshot.queryParamMap.get('number') ?? '');
    // The email comes only with the navigation from checkout, never in the address.
    const state = this.router.getCurrentNavigation()?.extras.state ?? history.state;
    this.email.set(typeof state?.['email'] === 'string' ? state['email'] : '');
  }

  protected copy(): void {
    const value = this.number();
    const done = () => { this.copied.set(true); setTimeout(() => this.copied.set(false), 2000); };
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(value).then(done, done);
    } else {
      done();
    }
  }
}
