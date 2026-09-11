import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { PermissionService } from '../../core/services/permission.service';
import { AddStaffRequest, PermissionTemplate, StaffRole, StaffService } from '../../core/services/staff.service';
import { parseApiError } from '../../core/utils/api-error.util';
import { PermissionChecklistComponent } from './permission-checklist.component';
import { PermissionTemplatesComponent } from './permission-templates.component';
import { LoginNoticeComponent } from './login-notice.component';
import { generatePassword } from './generate-password';
import { EMAIL_PATTERN, passwordProblem, USERNAME_PATTERN } from './staff-rules';

/** The person just added, with the password to hand over once. */
interface Added {
  name: string;
  username: string;
  password: string;
}

/**
 * Adding someone to the staff, on its own page. After saving, the page shows
 * their username and temporary password once, to hand over privately, and
 * offers to add another or go back to the list.
 */
@Component({
  selector: 'app-staff-add',
  standalone: true,
  imports: [FormsModule, RouterLink, SidebarComponent, PermissionChecklistComponent, PermissionTemplatesComponent,
    LoginNoticeComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center gap-3 flex-shrink-0">
          <a routerLink="/staff" class="text-sm text-gray-500 hover:text-gray-800">← Staff</a>
          <span class="text-gray-300">/</span>
          <h1 class="text-lg font-bold text-gray-900">{{ perms.isAdmin() ? 'Add staff member' : 'Add moderator' }}</h1>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-4xl mx-auto space-y-5">

            @if (errorMessage()) {
              <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ errorMessage() }}</p>
            }

            @if (added(); as a) {
              <app-login-notice [heading]="a.name + ' can now log in'" [username]="a.username" [password]="a.password"
                [dismissible]="false">
                <button type="button" (click)="startOver()" class="act-btn">Add another</button>
                <a routerLink="/staff" class="act-btn">Back to staff</a>
              </app-login-notice>
            } @else {
              <section class="bg-white rounded-xl border border-gray-200 p-5">
                <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <label class="text-xs font-medium text-gray-600">
                    Full name
                    <input [(ngModel)]="form.fullName" autocomplete="off" placeholder="Rahim Uddin"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Username <span class="font-normal text-gray-400">(3–50 letters, digits, . _ -)</span>
                    <input [(ngModel)]="form.username" autocomplete="off" spellcheck="false" placeholder="rahim"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Email
                    <input type="email" [(ngModel)]="form.email" autocomplete="off" placeholder="rahim@example.com"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <div class="text-xs font-medium text-gray-600">
                    <label for="add-password">Temporary password <span class="font-normal text-gray-400">(8–100 characters)</span></label>
                    <div class="mt-1 flex gap-2">
                      <input id="add-password" [(ngModel)]="form.temporaryPassword" autocomplete="off" spellcheck="false"
                        class="w-full rounded-lg border border-gray-200 px-3 py-2 font-mono text-sm" />
                      <button type="button" (click)="form.temporaryPassword = newPassword()" class="act-btn">Generate</button>
                    </div>
                  </div>
                  @if (perms.isAdmin()) {
                    <label class="text-xs font-medium text-gray-600">
                      Role
                      <select [(ngModel)]="form.role"
                        class="mt-1 w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm">
                        <option value="MODERATOR">Moderator: only what is ticked</option>
                        <option value="ADMIN">Admin: can do everything</option>
                      </select>
                    </label>
                  }
                </div>

                @if (form.role === 'ADMIN') {
                  <p class="mt-4 rounded-md bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
                    A new admin can do everything, so there is nothing to tick.
                  </p>
                } @else if (perms.can('staff.permissions')) {
                  <div class="mt-5 border-t border-gray-100 pt-4">
                    <h2 class="text-sm font-semibold text-gray-900">Permissions</h2>
                    <p class="mb-3 text-xs text-gray-500">Tick what they may do. You can change this later.</p>
                    <div class="mb-3">
                      <app-permission-templates [templates]="templates()" (applied)="checklist.applyTemplate($event)" />
                    </div>
                    <app-permission-checklist #checklist [(selected)]="selected" />
                  </div>
                } @else {
                  <p class="mt-4 text-xs text-gray-400">
                    They start with no permissions. Someone who can give permissions can add some afterwards.
                  </p>
                }

                <div class="mt-4 flex justify-end gap-2">
                  <a routerLink="/staff"
                    class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">
                    Cancel
                  </a>
                  <button type="button" (click)="submit()" [disabled]="busy()"
                    class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                    {{ busy() ? 'Adding…' : (form.role === 'ADMIN' ? 'Add admin' : 'Add moderator') }}
                  </button>
                </div>
              </section>
            }
          </div>
        </main>
      </div>
    </div>
  `,
})
export class StaffAddComponent implements OnInit {
  private readonly api = inject(StaffService);
  protected readonly perms = inject(PermissionService);

  readonly templates = signal<PermissionTemplate[]>([]);
  readonly busy = signal(false);
  readonly errorMessage = signal('');
  readonly added = signal<Added | null>(null);
  readonly selected = signal<string[]>([]);
  protected form = StaffAddComponent.emptyForm();

  ngOnInit(): void {
    if (this.perms.can('staff.permissions')) {
      this.api.templates().subscribe({
        next: (list) => this.templates.set(list),
        error: () => this.templates.set([]),
      });
    }
    // The checklist needs the permission names; they normally arrive with the first permission check.
    if (this.perms.catalogList().length === 0) this.perms.refresh();
  }

  protected newPassword(): string {
    return generatePassword();
  }

  protected startOver(): void {
    this.form = StaffAddComponent.emptyForm();
    this.selected.set([]);
    this.errorMessage.set('');
    this.added.set(null);
  }

  protected submit(): void {
    const fullName = this.form.fullName.trim();
    const username = this.form.username.trim();
    const email = this.form.email.trim();
    const password = this.form.temporaryPassword;
    if (!fullName) return this.errorMessage.set('Enter their full name.');
    if (!USERNAME_PATTERN.test(username)) {
      return this.errorMessage.set('The username must be 3–50 characters: letters, digits, dots, underscores or hyphens.');
    }
    if (!EMAIL_PATTERN.test(email)) return this.errorMessage.set('Enter a valid email address.');
    const problem = passwordProblem(password);
    if (problem) return this.errorMessage.set(problem);

    // Only admins may add an admin; a new admin needs no permissions.
    const role: StaffRole = this.perms.isAdmin() ? this.form.role : 'MODERATOR';
    const payload: AddStaffRequest = { fullName, username, email, temporaryPassword: password };
    if (role === 'ADMIN') payload.role = 'ADMIN';
    if (role === 'MODERATOR' && this.perms.can('staff.permissions') && this.selected().length > 0) {
      payload.permissions = this.selected();
    }

    this.busy.set(true);
    this.errorMessage.set('');
    this.api.add(payload).subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.added.set({ name: saved.name, username: saved.username, password });
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.errorMessage.set(parseApiError(err));
      },
    });
  }

  private static emptyForm() {
    return { fullName: '', username: '', email: '', temporaryPassword: generatePassword(), role: 'MODERATOR' as StaffRole };
  }
}
