import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, output, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { PermissionService } from '../../core/services/permission.service';
import { PermissionTemplate, StaffService } from '../../core/services/staff.service';
import { parseApiError } from '../../core/utils/api-error.util';

/**
 * Permission templates next to a checklist: apply one (replaces the ticks,
 * nothing is saved yet), save the current ticks as a new one, and rename or
 * delete them. Saving, renaming and deleting need "Give permissions".
 */
@Component({
  selector: 'app-permission-templates',
  standalone: true,
  template: `
    <div class="flex flex-wrap items-center gap-2">
      <label class="flex items-center gap-2 text-xs font-medium text-gray-600">
        Apply template
        <select (change)="pick($event)" [disabled]="templates().length === 0"
          class="rounded-lg border border-gray-200 bg-white px-2 py-1.5 text-sm">
          <option value="">{{ templates().length ? 'Choose…' : 'No templates yet' }}</option>
          @for (t of templates(); track t.id) {
            <option [value]="t.id">{{ t.name }} ({{ t.permissions.length }})</option>
          }
        </select>
      </label>
      @if (manage() && perms.can('staff.permissions')) {
        <button type="button" class="act-btn" (click)="saveAs()" [disabled]="busy()">Save ticks as template…</button>
        <button type="button" class="act-btn" (click)="open.set(!open())" [attr.aria-expanded]="open()">
          {{ open() ? 'Hide templates' : 'Manage templates' }}
        </button>
      }
    </div>

    @if (manage() && open() && perms.can('staff.permissions')) {
      <ul class="mt-2 divide-y divide-gray-50 rounded-lg border border-gray-200">
        @for (t of templates(); track t.id) {
          <li class="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
            <div>
              <span class="text-sm font-medium text-gray-800">{{ t.name }}</span>
              <span class="ml-2 text-xs text-gray-400">
                {{ t.permissions.length }} permissions
                @if (t.updatedAt) { · updated {{ t.updatedAt }} }
                @if (t.createdBy) { · by {{ t.createdBy }} }
              </span>
            </div>
            <div class="flex gap-1.5">
              <button type="button" class="act-btn act-btn-edit" (click)="rename(t)" [disabled]="busy()">Rename</button>
              <button type="button" class="act-btn act-btn-delete" (click)="remove(t)" [disabled]="busy()">Delete</button>
            </div>
          </li>
        } @empty {
          <li class="px-3 py-2 text-xs text-gray-400">No templates yet. Tick some permissions and save them as a template.</li>
        }
      </ul>
    }
  `,
})
export class PermissionTemplatesComponent {
  private readonly api = inject(StaffService);
  protected readonly perms = inject(PermissionService);

  readonly templates = input.required<PermissionTemplate[]>();
  /** The current ticks, for "Save ticks as template…". */
  readonly selected = input<string[]>([]);
  /** Also offer saving, renaming and deleting templates. */
  readonly manage = input(false);

  /** A template was picked; the checklist should take its ticks. */
  readonly applied = output<PermissionTemplate>();
  /** Templates were added, renamed or deleted; reload them. */
  readonly changed = output<void>();
  readonly failed = output<string>();

  protected readonly busy = signal(false);
  protected readonly open = signal(false);

  protected pick(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const template = this.templates().find((t) => t.id === select.value);
    select.value = '';
    if (template) this.applied.emit(template);
  }

  protected saveAs(): void {
    if (this.selected().length === 0) {
      this.failed.emit('Tick at least one permission before saving a template.');
      return;
    }
    const name = prompt('Name for this template (for example “Order packer”):')?.trim();
    if (!name) return;
    this.run(this.api.createTemplate({ name, permissions: this.selected() }));
  }

  protected rename(t: PermissionTemplate): void {
    const name = prompt('New name for this template:', t.name)?.trim();
    if (!name || name === t.name) return;
    this.run(this.api.updateTemplate(t.id, { name, permissions: t.permissions }));
  }

  protected remove(t: PermissionTemplate): void {
    if (!confirm(`Delete the template “${t.name}”? People already given these permissions keep them.`)) return;
    this.run(this.api.deleteTemplate(t.id));
  }

  private run(request: Observable<unknown>): void {
    this.busy.set(true);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.changed.emit();
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.failed.emit(parseApiError(err));
      },
    });
  }
}
