import { Component, input, output, signal } from '@angular/core';

/**
 * Login details shown once, after adding someone or resetting their
 * password, to hand over privately. Extra buttons can be placed inside it.
 */
@Component({
  selector: 'app-login-notice',
  standalone: true,
  template: `
    <section class="rounded-xl border border-emerald-300 bg-emerald-50 p-5" role="status">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h2 class="font-semibold text-gray-900">{{ heading() }}</h2>
          <p class="mt-1 text-sm text-gray-600">
            Give these to them privately. They must choose a new password when they first log in.
          </p>
        </div>
        @if (dismissible()) {
          <button type="button" (click)="dismissed.emit()" class="act-btn">Dismiss</button>
        }
      </div>
      <dl class="mt-3 grid gap-2 text-sm sm:grid-cols-2">
        <div class="rounded-lg border border-gray-200 bg-white px-3 py-2">
          <dt class="text-xs text-gray-500">Username</dt>
          <dd class="select-all font-mono text-gray-900">{{ username() }}</dd>
        </div>
        <div class="rounded-lg border border-gray-200 bg-white px-3 py-2">
          <dt class="text-xs text-gray-500">Temporary password</dt>
          <dd class="select-all font-mono text-gray-900">{{ password() }}</dd>
        </div>
      </dl>
      <div class="mt-3 flex flex-wrap items-center gap-2">
        <button type="button" (click)="copy()"
          class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg">
          {{ copied() ? 'Copied' : 'Copy' }}
        </button>
        <ng-content />
      </div>
      @if (copyFailed()) {
        <p class="mt-2 text-xs text-red-700">Couldn't copy. Select the username and password and copy them by hand.</p>
      }
    </section>
  `,
})
export class LoginNoticeComponent {
  readonly heading = input.required<string>();
  readonly username = input.required<string>();
  readonly password = input.required<string>();
  /** Show a Dismiss button; off when the page offers its own next steps. */
  readonly dismissible = input(true);
  readonly dismissed = output<void>();

  protected readonly copied = signal(false);
  protected readonly copyFailed = signal(false);

  protected copy(): void {
    const text = `Username: ${this.username()}\nTemporary password: ${this.password()}`;
    this.copyFailed.set(false);
    if (!navigator.clipboard?.writeText) {
      this.copyFailed.set(true);
      return;
    }
    navigator.clipboard.writeText(text).then(
      () => {
        this.copied.set(true);
        setTimeout(() => this.copied.set(false), 2000);
      },
      () => this.copyFailed.set(true),
    );
  }
}
