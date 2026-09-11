import { Component, computed, inject, input, model, signal } from '@angular/core';
import { PermissionInfo, PermissionService } from '../../core/services/permission.service';
import { PermissionTemplate } from '../../core/services/staff.service';
import {
  applyTemplateTicks,
  buildPermissionGraph,
  tick,
  tickBlockers,
  untick,
  untickBlockers,
} from './permission-deps';

interface PermissionGroup {
  area: string;
  items: PermissionInfo[];
}

/** A short explanation after an automatic change; `area` null shows it above the list. */
interface ChecklistNote {
  area: string | null;
  text: string;
}

/**
 * The permission checklist on the Staff page, grouped by area in catalogue
 * order. Ticking something also ticks what it needs; unticking something also
 * unticks what needs it.
 *
 * The person using it may only give or take away permissions they hold
 * themselves (an admin holds all of them). Other permissions are shown but
 * disabled, and keep their current state.
 */
@Component({
  selector: 'app-permission-checklist',
  standalone: true,
  template: `
    @if (catalog().length === 0) {
      <p class="text-sm text-gray-400">Loading permissions…</p>
    } @else {
      <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
        <label class="min-w-[12rem] flex-1 text-xs font-medium text-gray-600">
          <span class="sr-only">Search permissions</span>
          <input type="search" [value]="query()" (input)="query.set($any($event.target).value)"
            placeholder="Search permissions…"
            class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
        </label>
        <span class="text-xs text-gray-500" aria-live="polite">{{ tickedCount() }} of {{ catalog().length }} selected</span>
      </div>

      @if (!viewOnly() && !perms.isAdmin()) {
        <p class="mb-3 text-xs text-gray-400">
          Greyed-out permissions are ones you don't have yourself, so you can't give them or take them away.
        </p>
      }

      @if (note(); as n) {
        @if (n.area === null) {
          <p class="mb-3 rounded-md bg-blue-50 px-3 py-2 text-xs text-blue-700" role="status">{{ n.text }}</p>
        }
      }

      <div class="grid gap-3 md:grid-cols-2">
        @for (g of groups(); track g.area) {
          <section class="rounded-lg border border-gray-200" role="group" [attr.aria-label]="g.area">
            <label class="flex items-center gap-2 rounded-t-lg border-b border-gray-100 bg-gray-50 px-3 py-2"
              [class.cursor-pointer]="groupTargets(g).length > 0">
              <input type="checkbox" class="h-4 w-4 rounded border-gray-300"
                [checked]="groupState(g) === 'all'" [indeterminate]="groupState(g) === 'some'"
                [disabled]="groupTargets(g).length === 0" (change)="toggleGroup(g)"
                [attr.aria-label]="'Select all in ' + g.area" />
              <span class="text-sm font-semibold text-gray-800">{{ g.area }}</span>
            </label>
            <ul class="divide-y divide-gray-50">
              @for (p of g.items; track p.key) {
                <li>
                  <label class="flex items-start gap-2.5 px-3 py-2"
                    [class.cursor-pointer]="!isDisabled(p.key)"
                    [class.opacity-50]="!viewOnly() && isDisabled(p.key)"
                    [title]="blocked().get(p.key) ?? ''">
                    <input type="checkbox" class="mt-0.5 h-4 w-4 flex-shrink-0 rounded border-gray-300"
                      [checked]="ticked().has(p.key)" [disabled]="isDisabled(p.key)" (change)="toggle(p)" />
                    <span>
                      <span class="block text-sm text-gray-800">{{ p.label }}</span>
                      @if (p.description) {
                        <span class="block text-xs text-gray-400">{{ p.description }}</span>
                      }
                    </span>
                  </label>
                </li>
              }
            </ul>
            @if (note(); as n) {
              @if (n.area === g.area) {
                <p class="rounded-b-lg border-t border-gray-100 bg-blue-50 px-3 py-2 text-xs text-blue-700" role="status">
                  {{ n.text }}
                </p>
              }
            }
          </section>
        } @empty {
          <p class="text-sm text-gray-400">No permission matches “{{ query() }}”.</p>
        }
      </div>
    }
  `,
})
export class PermissionChecklistComponent {
  protected readonly perms = inject(PermissionService);

  /** The ticked keys. */
  readonly selected = model.required<string[]>();
  /** Show the ticks without letting anything change. */
  readonly viewOnly = input(false);

  protected readonly query = signal('');
  protected readonly note = signal<ChecklistNote | null>(null);

  protected readonly catalog = this.perms.catalogList;
  private readonly known = computed(() => new Set(this.catalog().map((p) => p.key)));
  private readonly graph = computed(() => buildPermissionGraph(this.catalog().map((p) => p.key)));
  protected readonly ticked = computed(() => new Set(this.selected()));
  protected readonly tickedCount = computed(() => this.selected().filter((k) => this.known().has(k)).length);

  /** Only permissions the viewer holds may be given or taken away (an admin holds them all). */
  private readonly mayChange = (key: string): boolean => this.perms.can(key);

  /** Groups in catalogue order, narrowed by the search box (label, area or key). */
  protected readonly groups = computed<PermissionGroup[]>(() => {
    const q = this.query().trim().toLowerCase();
    const groups: PermissionGroup[] = [];
    const byArea = new Map<string, PermissionGroup>();
    for (const p of this.catalog()) {
      if (q && !`${p.label} ${p.area} ${p.key}`.toLowerCase().includes(q)) continue;
      let g = byArea.get(p.area);
      if (!g) {
        g = { area: p.area, items: [] };
        byArea.set(p.area, g);
        groups.push(g);
      }
      g.items.push(p);
    }
    return groups;
  });

  /** Why each key that can't be clicked right now is disabled. */
  protected readonly blocked = computed(() => {
    const reasons = new Map<string, string>();
    if (this.viewOnly()) return reasons;
    const ticked = this.ticked();
    const graph = this.graph();
    for (const p of this.catalog()) {
      if (!this.mayChange(p.key)) {
        reasons.set(p.key, "You don't have this permission, so you can't give it or take it away.");
        continue;
      }
      if (ticked.has(p.key)) {
        const b = untickBlockers(p.key, ticked, graph, this.mayChange);
        if (b.length) reasons.set(p.key, `Can't untick: ${this.names(b)} needs this, and you can't take that away.`);
      } else {
        const b = tickBlockers(p.key, ticked, graph, this.mayChange);
        if (b.length) reasons.set(p.key, `Can't tick: this needs ${this.names(b)}, which you can't give.`);
      }
    }
    return reasons;
  });

  protected isDisabled(key: string): boolean {
    return this.viewOnly() || this.blocked().has(key);
  }

  /** The shown keys in a group that "select all" may change. */
  protected groupTargets(g: PermissionGroup): string[] {
    if (this.viewOnly()) return [];
    return g.items.map((p) => p.key).filter(this.mayChange);
  }

  protected groupState(g: PermissionGroup): 'all' | 'some' | 'none' {
    const targets = this.groupTargets(g);
    const ticked = this.ticked();
    const on = targets.filter((k) => ticked.has(k)).length;
    if (targets.length > 0 && on === targets.length) return 'all';
    return on > 0 ? 'some' : 'none';
  }

  protected toggle(p: PermissionInfo): void {
    if (this.isDisabled(p.key)) return;
    const ticked = this.ticked();
    const turningOff = ticked.has(p.key);
    const change = turningOff ? untick(p.key, ticked, this.graph()) : tick(p.key, ticked, this.graph());
    this.commit(change.next);
    this.note.set(change.also.length
      ? { area: p.area, text: `${turningOff ? 'Also unticked' : 'Also ticked'}: ${this.names(change.also)}` }
      : null);
  }

  /** Ticks every shown key in the group this person may change, or unticks them all when all are ticked. */
  protected toggleGroup(g: PermissionGroup): void {
    const targets = this.groupTargets(g);
    if (targets.length === 0) return;
    const graph = this.graph();
    const turningOn = this.groupState(g) !== 'all';
    let ticked: Set<string> = new Set(this.ticked());
    const also = new Set<string>();
    const skipped: string[] = [];

    for (const key of targets) {
      if (ticked.has(key) === turningOn) continue;
      const blockers = turningOn
        ? tickBlockers(key, ticked, graph, this.mayChange)
        : untickBlockers(key, ticked, graph, this.mayChange);
      if (blockers.length) {
        skipped.push(key);
        continue;
      }
      const change = turningOn ? tick(key, ticked, graph) : untick(key, ticked, graph);
      ticked = change.next;
      change.also.forEach((k) => also.add(k));
    }
    this.commit(ticked);

    const outside = [...also].filter((k) => !targets.includes(k));
    const parts: string[] = [];
    if (outside.length) parts.push(`${turningOn ? 'Also ticked' : 'Also unticked'}: ${this.names(outside)}.`);
    if (skipped.length) {
      parts.push(turningOn
        ? `Not ticked: ${this.names(skipped)}, because they need permissions you can't give.`
        : `Kept: ${this.names(skipped)}, because permissions you can't take away need them.`);
    }
    this.note.set(parts.length ? { area: g.area, text: parts.join(' ') } : null);
  }

  /** Replaces the ticks with a template's, as far as this person may. Nothing is saved yet. */
  applyTemplate(template: PermissionTemplate): void {
    const { next, skipped } = applyTemplateTicks(
      this.ticked(), template.permissions, this.known(), this.graph(), this.mayChange);
    this.commit(next);
    this.note.set({
      area: null,
      text: skipped.length
        ? `Applied “${template.name}”. Left out ${skipped.length} you can't give: ${this.names(skipped)}.`
        : `Applied “${template.name}”. Nothing is saved until you save.`,
    });
  }

  /** Stores the ticks in catalogue order, keeping any keys the catalogue doesn't know. */
  private commit(next: Set<string>): void {
    const known = this.known();
    const ordered = this.catalog().map((p) => p.key).filter((k) => next.has(k));
    const unknown = [...next].filter((k) => !known.has(k));
    this.selected.set([...ordered, ...unknown]);
  }

  private names(keys: readonly string[]): string {
    return keys.map((k) => this.perms.label(k)).join(', ');
  }
}
