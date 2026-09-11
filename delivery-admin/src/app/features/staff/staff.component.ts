import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { PermissionTemplate, StaffMember, StaffService } from '../../core/services/staff.service';
import { parseApiError } from '../../core/utils/api-error.util';
import { PermissionChecklistComponent } from './permission-checklist.component';
import { PermissionTemplatesComponent } from './permission-templates.component';
import { LoginNoticeComponent } from './login-notice.component';
import { generatePassword } from './generate-password';
import { EMAIL_PATTERN, passwordProblem } from './staff-rules';

type PanelMode = 'edit' | 'reset' | 'permissions' | 'delete';

/** Login details shown once after resetting someone's password. */
interface LoginNotice {
  heading: string;
  username: string;
  password: string;
}

/**
 * The Staff page: who can log in to the admin panel, and what each moderator
 * may do. Adding someone happens on its own page (/staff/new). The backend
 * enforces every rule; this page only offers what it would allow:
 *  - nobody acts on their own row except to edit their details;
 *  - moderators see no actions on admins' rows;
 *  - only admins change roles;
 *  - permissions can only be given or taken away by someone who holds them.
 */
@Component({
  selector: 'app-staff',
  standalone: true,
  imports: [FormsModule, RouterLink, SidebarComponent, PermissionChecklistComponent, PermissionTemplatesComponent,
    LoginNoticeComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-gray-900">Staff</h1>
            <span class="text-xs text-gray-400">{{ staff().length }} {{ staff().length === 1 ? 'person' : 'people' }}</span>
          </div>
          @if (perms.can('staff.manage')) {
            <a routerLink="/staff/new"
              class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg">
              + Add moderator
            </a>
          }
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-6xl mx-auto space-y-5">

            @if (errorMessage()) {
              <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ errorMessage() }}</p>
            }
            @if (successMessage()) {
              <p class="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700" role="status">{{ successMessage() }}</p>
            }

            <!-- One-time login details after a password reset; stays until dismissed. -->
            @if (notice(); as n) {
              <app-login-notice [heading]="n.heading" [username]="n.username" [password]="n.password"
                (dismissed)="notice.set(null)" />
            }

            <!-- Edit details -->
            @if (mode() === 'edit' && target(); as m) {
              <section id="staff-panel" class="bg-white rounded-xl border border-gray-200 p-5">
                <h2 class="mb-4 text-base font-semibold text-gray-900">Edit details: {{ m.name }}</h2>
                <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <label class="text-xs font-medium text-gray-600">
                    Full name
                    <input [(ngModel)]="editForm.fullName" autocomplete="off"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Email
                    <input type="email" [(ngModel)]="editForm.email" autocomplete="off"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                </div>
                <p class="mt-2 text-xs text-gray-400">The username <span class="font-mono">{{ m.username }}</span> can't be changed.</p>
                <div class="mt-4 flex justify-end gap-2">
                  <button type="button" (click)="closePanel()"
                    class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">
                    Cancel
                  </button>
                  <button type="button" (click)="saveDetails(m)" [disabled]="busy()"
                    class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                    {{ busy() ? 'Saving…' : 'Save details' }}
                  </button>
                </div>
              </section>
            }

            <!-- Reset password -->
            @if (mode() === 'reset' && target(); as m) {
              <section id="staff-panel" class="bg-white rounded-xl border border-gray-200 p-5">
                <h2 class="text-base font-semibold text-gray-900">Reset password: {{ m.name }}</h2>
                <p class="mb-4 mt-1 text-xs text-gray-500">
                  They are signed out everywhere and must choose a new password when they next log in.
                </p>
                <div class="max-w-md text-xs font-medium text-gray-600">
                  <label for="reset-password">New temporary password <span class="font-normal text-gray-400">(8–100 characters)</span></label>
                  <div class="mt-1 flex gap-2">
                    <input id="reset-password" [(ngModel)]="resetPassword" autocomplete="off" spellcheck="false"
                      class="w-full rounded-lg border border-gray-200 px-3 py-2 font-mono text-sm" />
                    <button type="button" (click)="resetPassword = newPassword()" class="act-btn">Generate</button>
                  </div>
                </div>
                <div class="mt-4 flex justify-end gap-2">
                  <button type="button" (click)="closePanel()"
                    class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">
                    Cancel
                  </button>
                  <button type="button" (click)="saveReset(m)" [disabled]="busy()"
                    class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                    {{ busy() ? 'Resetting…' : 'Reset password' }}
                  </button>
                </div>
              </section>
            }

            <!-- Permissions. Keyed by person so the checklist starts fresh for each one. -->
            @for (m of permissionsFor(); track m.id) {
              <section id="staff-panel" class="bg-white rounded-xl border border-gray-200 p-5">
                <h2 class="text-base font-semibold text-gray-900">Permissions: {{ m.name }}</h2>
                @if (m.role === 'ADMIN') {
                  <p class="mt-3 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                    Can do everything. Admins aren't limited by permissions; to limit what {{ m.name }} can do,
                    make them a moderator.
                  </p>
                  <div class="mt-4 flex justify-end">
                    <button type="button" (click)="closePanel()"
                      class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">
                      Close
                    </button>
                  </div>
                } @else {
                  @if (canEditPermissions(m)) {
                    <p class="mb-4 mt-1 text-xs text-gray-500">Changes apply when you save. They take effect within a minute.</p>
                    <div class="mb-4">
                      <app-permission-templates [templates]="templates()" [selected]="draft()" [manage]="true"
                        (applied)="checklist.applyTemplate($event)" (changed)="loadTemplates()"
                        (failed)="errorMessage.set($event)" />
                    </div>
                  } @else {
                    <p class="mb-4 mt-1 text-xs text-gray-400">
                      You can see these but not change them; that needs “{{ perms.label('staff.permissions') }}”.
                    </p>
                  }
                  <app-permission-checklist #checklist [(selected)]="draft" [viewOnly]="!canEditPermissions(m)" />
                  <div class="mt-4 flex justify-end gap-2">
                    <button type="button" (click)="closePanel()"
                      class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">
                      {{ canEditPermissions(m) ? 'Cancel' : 'Close' }}
                    </button>
                    @if (canEditPermissions(m)) {
                      <button type="button" (click)="savePermissions(m)" [disabled]="busy()"
                        class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                        {{ busy() ? 'Saving…' : 'Save permissions' }}
                      </button>
                    }
                  </div>
                }
              </section>
            }

            <!-- Delete -->
            @if (mode() === 'delete' && target(); as m) {
              <section id="staff-panel" class="bg-white rounded-xl border border-red-300 p-5"
                role="alertdialog" aria-labelledby="delete-title" aria-describedby="delete-text">
                <h2 id="delete-title" class="text-base font-semibold text-gray-900">Delete {{ m.name }}?</h2>
                <p id="delete-text" class="mt-1 text-sm text-gray-600">
                  This can't be undone. Their login and permissions are removed; their past actions stay in the activity log.
                </p>
                @if (m.active && perms.can('staff.manage')) {
                  <p class="mt-1 text-xs text-gray-400">To stop them logging in but keep them on the list, deactivate them instead.</p>
                }
                <label class="mt-3 block max-w-sm text-xs font-medium text-gray-600">
                  Type <span class="font-mono font-semibold text-gray-900">{{ m.username }}</span> to confirm
                  <input [(ngModel)]="deleteText" autocomplete="off" spellcheck="false"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 font-mono text-sm" />
                </label>
                <div class="mt-4 flex justify-end gap-2">
                  <button type="button" (click)="closePanel()"
                    class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">
                    Cancel
                  </button>
                  <button type="button" (click)="confirmDelete(m)" [disabled]="busy() || deleteText !== m.username"
                    class="px-3 py-1.5 text-xs font-semibold text-white bg-red-600 hover:bg-red-700 rounded-lg disabled:opacity-50">
                    {{ busy() ? 'Deleting…' : 'Delete' }}
                  </button>
                </div>
              </section>
            }

            <!-- List -->
            <section class="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-gray-100 bg-gray-50 text-xs uppercase text-gray-400">
                      <th class="px-4 py-2.5 text-left">Name</th>
                      <th class="px-4 py-2.5 text-left">Username / email</th>
                      <th class="px-4 py-2.5 text-left">Role</th>
                      <th class="px-4 py-2.5 text-left">Status</th>
                      <th class="px-4 py-2.5 text-left">Last seen</th>
                      <th class="px-4 py-2.5 text-right">Permissions</th>
                      <th class="px-4 py-2.5"><span class="sr-only">Actions</span></th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-50">
                    @if (loading() && staff().length === 0) {
                      <tr><td colspan="7" class="px-4 py-6 text-center text-gray-400">Loading…</td></tr>
                    }
                    @for (m of staff(); track m.id) {
                      <tr [class.bg-blue-50]="mode() !== null && target()?.id === m.id">
                        <td class="px-4 py-2.5" [class.opacity-50]="!m.active">
                          <div class="flex items-center gap-2">
                            <span class="font-medium text-gray-900">{{ m.name }}</span>
                            @if (m.you) {
                              <span class="rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-700">You</span>
                            }
                          </div>
                          @if (m.createdAt) {
                            <div class="text-xs text-gray-400">Added {{ m.createdAt }}@if (m.createdBy) { by {{ m.createdBy }}}</div>
                          }
                        </td>
                        <td class="px-4 py-2.5" [class.opacity-50]="!m.active">
                          <div class="font-mono text-xs text-gray-700">{{ m.username }}</div>
                          <div class="text-xs text-gray-400">{{ m.email || '—' }}</div>
                        </td>
                        <td class="px-4 py-2.5" [class.opacity-50]="!m.active">
                          @if (m.role === 'ADMIN') {
                            <span class="rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">Admin</span>
                          } @else {
                            <span class="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-semibold text-blue-700">Moderator</span>
                          }
                        </td>
                        <td class="px-4 py-2.5">
                          @if (m.active) {
                            <span class="inline-flex items-center gap-1.5 text-xs text-gray-700">
                              <span class="h-2 w-2 rounded-full bg-emerald-500"></span>Active
                            </span>
                          } @else {
                            <span class="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-semibold text-gray-500">Deactivated</span>
                          }
                        </td>
                        <td class="px-4 py-2.5 whitespace-nowrap text-xs text-gray-500" [class.opacity-50]="!m.active">
                          {{ m.lastSeenAt ?? 'Never' }}
                        </td>
                        <td class="px-4 py-2.5 text-right text-xs text-gray-700" [class.opacity-50]="!m.active">
                          @if (m.role === 'ADMIN') {
                            <span title="Can do everything">Everything</span>
                          } @else {
                            {{ m.permissions.length || 'None' }}
                          }
                        </td>
                        <td class="px-4 py-2.5">
                          <div class="flex flex-wrap items-center justify-end gap-1.5">
                            @if (canSeePermissions(m)) {
                              <button type="button" class="act-btn act-btn-edit" (click)="startPermissions(m)">Permissions</button>
                            }
                            @if (canEditDetails(m)) {
                              <button type="button" class="act-btn act-btn-edit" (click)="startEdit(m)">Edit details</button>
                            }
                            @if (canManage(m)) {
                              <button type="button" class="act-btn act-btn-edit" (click)="startReset(m)">Reset password</button>
                              @if (m.active) {
                                <button type="button" class="act-btn act-btn-delete" (click)="deactivate(m)" [disabled]="busy()">Deactivate</button>
                              } @else {
                                <button type="button" class="act-btn act-btn-send" (click)="reactivate(m)" [disabled]="busy()">Reactivate</button>
                              }
                            }
                            @if (canChangeRole(m)) {
                              <button type="button" class="act-btn act-btn-edit" (click)="changeRole(m)" [disabled]="busy()">
                                {{ m.role === 'ADMIN' ? 'Make moderator' : 'Make admin' }}
                              </button>
                            }
                            @if (canDelete(m)) {
                              <button type="button" class="act-btn act-btn-delete" (click)="startDelete(m)">Delete</button>
                            }
                          </div>
                        </td>
                      </tr>
                    } @empty {
                      @if (!loading()) {
                        <tr><td colspan="7" class="px-4 py-6 text-center text-gray-400">No staff yet.</td></tr>
                      }
                    }
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        </main>
      </div>
    </div>
  `,
})
export class StaffComponent implements OnInit {
  private readonly api = inject(StaffService);
  protected readonly perms = inject(PermissionService);
  private readonly confirmer = inject(ConfirmService);

  readonly staff = signal<StaffMember[]>([]);
  readonly templates = signal<PermissionTemplate[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly errorMessage = signal('');
  readonly successMessage = signal('');
  readonly notice = signal<LoginNotice | null>(null);

  /** Which form is open, and for whom. */
  readonly mode = signal<PanelMode | null>(null);
  readonly target = signal<StaffMember | null>(null);
  /** The person whose permissions are open, as a one-item list so the panel is rebuilt per person. */
  protected readonly permissionsFor = computed(() => {
    const m = this.target();
    return this.mode() === 'permissions' && m ? [m] : [];
  });

  protected editForm = { fullName: '', email: '' };
  protected resetPassword = '';
  readonly draft = signal<string[]>([]);
  protected deleteText = '';

  ngOnInit(): void {
    this.reload();
    this.loadTemplates();
    // The checklist needs the permission names; they normally arrive with the first permission check.
    if (this.perms.catalogList().length === 0) this.perms.refresh();
  }

  reload(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (list) => {
        this.staff.set(list);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(parseApiError(err));
      },
    });
  }

  loadTemplates(): void {
    this.api.templates().subscribe({
      next: (list) => this.templates.set(list),
      error: () => this.templates.set([]),
    });
  }

  // ── who may do what (mirrors the server's rules) ───────────────────────────

  /** Moderators take no action on admins' rows. */
  private canActOn(m: StaffMember): boolean {
    return m.role !== 'ADMIN' || this.perms.isAdmin();
  }

  /** Open the checklist (read-only without "Give permissions"). Never on one's own row. */
  protected canSeePermissions(m: StaffMember): boolean {
    return !m.you && this.canActOn(m);
  }

  protected canEditPermissions(m: StaffMember): boolean {
    return this.canSeePermissions(m) && m.role === 'MODERATOR' && this.perms.can('staff.permissions');
  }

  /** Details are the one thing people may change on their own row. */
  protected canEditDetails(m: StaffMember): boolean {
    return this.canActOn(m) && this.perms.can('staff.manage');
  }

  /** Reset password, deactivate and reactivate. */
  protected canManage(m: StaffMember): boolean {
    return !m.you && this.canActOn(m) && this.perms.can('staff.manage');
  }

  protected canChangeRole(m: StaffMember): boolean {
    return !m.you && this.perms.isAdmin();
  }

  protected canDelete(m: StaffMember): boolean {
    return !m.you && this.canActOn(m) && this.perms.can('staff.delete');
  }

  // ── panels ──────────────────────────────────────────────────────────────

  private openPanel(mode: PanelMode, member: StaffMember): void {
    this.errorMessage.set('');
    this.successMessage.set('');
    this.target.set(member);
    this.mode.set(mode);
    setTimeout(() => document.getElementById('staff-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  }

  protected closePanel(): void {
    this.mode.set(null);
    this.target.set(null);
  }

  protected newPassword(): string {
    return generatePassword();
  }

  protected startEdit(m: StaffMember): void {
    this.editForm = { fullName: m.fullName ?? m.name ?? '', email: m.email ?? '' };
    this.openPanel('edit', m);
  }

  protected startReset(m: StaffMember): void {
    this.resetPassword = generatePassword();
    this.openPanel('reset', m);
  }

  protected startPermissions(m: StaffMember): void {
    this.draft.set([...m.permissions]);
    this.openPanel('permissions', m);
  }

  protected startDelete(m: StaffMember): void {
    this.deleteText = '';
    this.openPanel('delete', m);
  }

  // ── actions ─────────────────────────────────────────────────────────────

  protected saveDetails(m: StaffMember): void {
    const fullName = this.editForm.fullName.trim();
    if (!fullName) return this.errorMessage.set('Enter their full name.');
    const email = this.editForm.email.trim();
    if (!EMAIL_PATTERN.test(email)) return this.errorMessage.set('Enter a valid email address.');
    this.run(this.api.updateDetails(m.id, { fullName, email }), (saved) => {
      this.successMessage.set(`Saved details for ${saved.name}.`);
      this.closePanel();
      // Your own name shows in the sidebar.
      if (m.you) this.perms.refresh();
    });
  }

  protected saveReset(m: StaffMember): void {
    const password = this.resetPassword;
    const problem = passwordProblem(password);
    if (problem) return this.errorMessage.set(problem);
    this.run(this.api.resetPassword(m.id, password), () => {
      this.notice.set({ heading: `New temporary password for ${m.name}`, username: m.username, password });
      this.closePanel();
    });
  }

  protected savePermissions(m: StaffMember): void {
    this.run(this.api.setPermissions(m.id, this.draft()), (saved) => {
      this.staff.update((list) => list.map((x) => (x.id === saved.id ? saved : x)));
      this.successMessage.set(`Saved permissions for ${saved.name}.`);
      this.closePanel();
    });
  }

  protected async deactivate(m: StaffMember): Promise<void> {
    const ok = await this.confirmer.ask({
      title: `Deactivate ${m.name}?`,
      message: "They are signed out everywhere and can't log in until reactivated.",
      confirmLabel: 'Deactivate',
      danger: true,
    });
    if (!ok) return;
    this.run(this.api.deactivate(m.id), (saved) => this.successMessage.set(`${saved.name} is deactivated.`));
  }

  protected reactivate(m: StaffMember): void {
    this.run(this.api.reactivate(m.id), (saved) => this.successMessage.set(`${saved.name} can log in again.`));
  }

  protected async changeRole(m: StaffMember): Promise<void> {
    const toAdmin = m.role !== 'ADMIN';
    const ok = await this.confirmer.ask({
      title: toAdmin ? `Make ${m.name} an admin?` : `Make ${m.name} a moderator?`,
      message: toAdmin
        ? 'Admins can do everything, including managing staff. Their current permissions are cleared.'
        : 'They will have no permissions until someone gives them some.',
      confirmLabel: toAdmin ? 'Make admin' : 'Make moderator',
      danger: true,
    });
    if (!ok) return;
    this.run(this.api.changeRole(m.id, toAdmin ? 'ADMIN' : 'MODERATOR'), (saved) => {
      this.successMessage.set(toAdmin
        ? `${saved.name} is now an admin.`
        : `${saved.name} is now a moderator with no permissions. Use Permissions to give them some.`);
      if (this.target()?.id === m.id) this.closePanel();
    });
  }

  protected confirmDelete(m: StaffMember): void {
    if (this.deleteText !== m.username) return;
    this.run(this.api.delete(m.id, this.deleteText), () => {
      this.successMessage.set(`${m.name} was deleted.`);
      this.closePanel();
    });
  }

  /** Runs a change; on success calls `done`, then reloads the list. Errors go to the error box. */
  private run<T>(request: Observable<T>, done: (result: T) => void): void {
    this.busy.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');
    request.subscribe({
      next: (result) => {
        this.busy.set(false);
        done(result);
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.errorMessage.set(parseApiError(err));
      },
    });
  }
}
