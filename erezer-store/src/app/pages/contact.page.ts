import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="mx-auto max-w-2xl space-y-6 py-12">
      <header>
        <h1 class="app-section-title">Contact us</h1>
        <p class="app-muted text-sm">
          Questions about an order, sizing, or anything else — we read every message.
        </p>
      </header>

      @if (submitted()) {
        <article class="app-card space-y-3 p-6 text-center">
          <p class="text-green-600">Thanks for getting in touch. We'll reply to <strong>{{ email }}</strong> as soon as we can.</p>
          <a routerLink="/" class="btn-secondary inline-block">Back to home</a>
        </article>
      } @else {
        <form (ngSubmit)="submit()" class="app-card space-y-4 p-6">
          <div class="grid gap-3 sm:grid-cols-2">
            <input
              [(ngModel)]="name"
              name="name"
              placeholder="Your name"
              required
              maxlength="200"
              class="rounded-xl border border-neutral-300 px-4 py-2.5 text-sm dark:border-neutral-700 dark:bg-neutral-900" />
            <input
              [(ngModel)]="email"
              name="email"
              type="email"
              placeholder="Email"
              required
              maxlength="255"
              class="rounded-xl border border-neutral-300 px-4 py-2.5 text-sm dark:border-neutral-700 dark:bg-neutral-900" />
          </div>
          <input
            [(ngModel)]="subject"
            name="subject"
            placeholder="Subject (optional)"
            maxlength="255"
            class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm dark:border-neutral-700 dark:bg-neutral-900" />
          <input
            [(ngModel)]="orderId"
            name="orderId"
            placeholder="Order ID (optional)"
            class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm font-mono dark:border-neutral-700 dark:bg-neutral-900" />
          <textarea
            [(ngModel)]="message"
            name="message"
            rows="6"
            placeholder="How can we help?"
            required
            maxlength="4000"
            class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm dark:border-neutral-700 dark:bg-neutral-900"
          ></textarea>

          @if (error()) {
            <p class="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-600 dark:border-red-800 dark:bg-red-950 dark:text-red-400">
              {{ error() }}
            </p>
          }

          <button type="submit" class="btn-primary w-full" [disabled]="submitting() || !canSubmit()">
            {{ submitting() ? 'Sending…' : 'Send message' }}
          </button>
        </form>
      }
    </section>
  `,
})
export class ContactPage {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly submitting = signal(false);
  protected readonly submitted  = signal(false);
  protected readonly error      = signal<string>('');

  protected name    = '';
  protected email   = this.auth.email() ?? '';
  protected subject = '';
  protected orderId = '';
  protected message = '';

  protected canSubmit(): boolean {
    return !!this.name.trim() && !!this.email.trim() && !!this.message.trim();
  }

  protected submit(): void {
    if (!this.canSubmit()) return;
    this.submitting.set(true);
    this.error.set('');
    this.api.submitContactMessage({
      name:    this.name.trim(),
      email:   this.email.trim(),
      subject: this.subject.trim() || undefined,
      orderId: this.orderId.trim() || undefined,
      message: this.message.trim(),
    }).pipe(catchError((err) => {
      this.submitting.set(false);
      this.error.set(err?.error?.message ?? 'Could not send your message. Please try again.');
      return of(null);
    })).subscribe((response) => {
      this.submitting.set(false);
      if (response) {
        this.submitted.set(true);
      }
    });
  }
}
