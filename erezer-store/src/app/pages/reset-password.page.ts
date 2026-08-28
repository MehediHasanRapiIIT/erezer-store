import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="mx-auto max-w-md py-16">
      <article class="app-card space-y-4 p-8">
        <h1 class="text-2xl font-semibold">Reset password</h1>

        @if (!token()) {
          <p class="text-red-500">This reset link is missing its token. Please use the link from your email.</p>
          <a routerLink="/account" class="btn-secondary inline-block">Back to account</a>
        } @else if (done()) {
          <p class="text-green-600">Your password has been updated.</p>
          <a routerLink="/account" class="btn-primary inline-block">Sign in</a>
        } @else {
          <div class="space-y-3">
            <input
              [(ngModel)]="newPassword"
              type="password"
              placeholder="New password (min 8 characters)"
              autocomplete="new-password"
              class="w-full rounded-xl border border-neutral-300 px-4 py-2 dark:border-neutral-700" />
            <input
              [(ngModel)]="confirmPassword"
              type="password"
              placeholder="Confirm new password"
              autocomplete="new-password"
              class="w-full rounded-xl border border-neutral-300 px-4 py-2 dark:border-neutral-700" />
            @if (mismatch()) {
              <p class="text-sm text-red-500">Passwords don't match.</p>
            }
            @if (auth.error()) {
              <p class="text-sm text-red-500">{{ auth.error() }}</p>
            }
            <button (click)="submit()" [disabled]="auth.loading()" class="btn-primary w-full">
              {{ auth.loading() ? 'Updating…' : 'Update password' }}
            </button>
          </div>
        }
      </article>
    </section>
  `,
})
export class ResetPasswordPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  protected readonly auth = inject(AuthService);

  protected readonly token = signal<string | null>(null);
  protected readonly done  = signal(false);
  protected readonly mismatch = signal(false);

  protected newPassword = '';
  protected confirmPassword = '';

  ngOnInit(): void {
    this.token.set(this.route.snapshot.queryParamMap.get('token'));
  }

  protected async submit(): Promise<void> {
    if (!this.token()) return;
    if (this.newPassword.length < 8) {
      this.auth.error.set('Password must be at least 8 characters.');
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.mismatch.set(true);
      return;
    }
    this.mismatch.set(false);
    try {
      await this.auth.resetPassword(this.token()!, this.newPassword);
      this.done.set(true);
    } catch { /* surfaced via auth.error() */ }
  }
}
