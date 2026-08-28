import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

type State = 'idle' | 'verifying' | 'success' | 'error';

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="mx-auto max-w-md py-16">
      <article class="app-card space-y-4 p-8 text-center">
        <h1 class="text-2xl font-semibold">Email verification</h1>

        @if (state() === 'verifying') {
          <p class="app-muted">Verifying your email…</p>
        }
        @if (state() === 'success') {
          <p class="text-green-600">Your email is verified. Thanks!</p>
          <a routerLink="/account" class="btn-primary inline-block">Go to your account</a>
        }
        @if (state() === 'error') {
          <p class="text-red-500">{{ message() }}</p>
          <a routerLink="/account" class="btn-secondary inline-block">Back to account</a>
        }
        @if (state() === 'idle') {
          <p class="app-muted">No verification token found in the link.</p>
          <a routerLink="/account" class="btn-secondary inline-block">Back to account</a>
        }
      </article>
    </section>
  `,
})
export class VerifyEmailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly auth  = inject(AuthService);

  protected readonly state   = signal<State>('idle');
  protected readonly message = signal<string>('');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('idle');
      return;
    }
    this.state.set('verifying');
    this.auth.verifyEmail(token).then(
      () => this.state.set('success'),
      (err) => {
        this.message.set(err?.error?.message ?? 'Verification link is invalid or expired.');
        this.state.set('error');
      },
    );
  }
}
