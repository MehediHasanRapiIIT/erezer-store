import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="mx-auto max-w-md py-16">
      <article class="app-card space-y-4 p-8 text-center">
        <h1 class="text-2xl font-semibold">Newsletter</h1>
        @if (status() === 'invalid') {
          <p class="text-red-500">The unsubscribe link is missing its token.</p>
        } @else if (status() === 'pending') {
          <p class="app-muted">Updating your preferences…</p>
        } @else {
          <p class="text-green-600">You've been unsubscribed. Sorry to see you go.</p>
          <a routerLink="/" class="btn-secondary inline-block">Back to home</a>
        }
      </article>
    </section>
  `,
})
export class UnsubscribePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api   = inject(ApiService);

  protected readonly status = signal<'pending' | 'done' | 'invalid'>('pending');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.status.set('invalid');
      return;
    }
    // The backend returns 200 even for unknown tokens — by design, to prevent
    // address enumeration. We always settle on "done" once the call returns.
    this.api.unsubscribeNewsletter(token)
      .pipe(catchError(() => of(null)))
      .subscribe(() => this.status.set('done'));
  }
}
