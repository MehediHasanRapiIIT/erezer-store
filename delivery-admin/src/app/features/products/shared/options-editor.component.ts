import { Component, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProductOption } from '../../../core/services/variant.service';

/** A few colours a clothing shop reaches for first, so a swatch starts sensible. */
const STARTER_COLOURS: [string, string][] = [
  ['Black', '#111111'], ['White', '#FFFFFF'], ['Navy', '#1E3A5F'], ['Grey', '#9CA3AF'],
  ['Red', '#DC2626'], ['Green', '#15803D'], ['Blue', '#2563EB'], ['Maroon', '#7F1D1D'],
];

/** What is wrong with these options, in plain words, or '' when they can be saved. */
export function optionsProblem(options: ProductOption[]): string {
  const names = new Set<string>();
  for (const o of options) {
    const name = o.name.trim();
    if (!name) return 'Give every option a name, such as Colour.';
    if (['size', 'sizes', 'fit', 'fits'].includes(name.toLowerCase())) {
      return `Sizes and fits have their own sections below. Use those, not an option called ${name}.`;
    }
    if (names.has(name.toLowerCase())) return `Two options are called ${name}. Keep one.`;
    names.add(name.toLowerCase());
    const values = o.values.map((v) => v.value.trim()).filter(Boolean);
    if (values.length === 0) return `Give ${name} at least one choice.`;
    if (new Set(values.map((v) => v.toLowerCase())).size !== values.length) return `${name} has the same choice twice. Keep one.`;
  }
  return '';
}

/** The options as they are sent: names trimmed, empty choices left out. */
export function cleanOptions(options: ProductOption[]): ProductOption[] {
  return options.map((o) => ({
    ...o,
    name: o.name.trim(),
    values: o.values.filter((v) => v.value.trim()).map((v) => ({ ...v, value: v.value.trim(), hex: o.kind === 'COLOUR' ? v.hex ?? null : null })),
  }));
}

/** How many combinations these options make: 3 colours × 2 sleeves = 6. */
export function combinationCount(options: ProductOption[]): number {
  return options.reduce((n, o) => n * Math.max(1, o.values.filter((v) => v.value.trim()).length), 1);
}

/**
 * Edits a product's own options - colour, and anything else the shop defines -
 * and the choices of each. It only edits the list it is given; saving is up to
 * the page it sits in.
 */
@Component({
  selector: 'app-options-editor',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="space-y-3" data-testid="options-editor">
      @for (option of options(); track $index; let oi = $index) {
        <div class="rounded-lg border border-gray-200 p-3" data-testid="option-card">
          <div class="flex flex-wrap items-end gap-2">
            <label class="text-xs font-medium text-gray-600">
              Option
              <input [ngModel]="option.name" (ngModelChange)="rename(oi, $event)" [disabled]="disabled()" maxlength="40"
                placeholder="e.g. Colour" data-testid="option-name"
                class="mt-1 block w-44 rounded-lg border border-gray-200 px-3 py-1.5 text-sm font-normal" />
            </label>
            <label class="flex items-center gap-1.5 pb-2 text-xs text-gray-600">
              <input type="checkbox" [checked]="option.kind === 'COLOUR'" (change)="toggleColour(oi)" [disabled]="disabled()"
                data-testid="option-is-colour" class="h-3.5 w-3.5 rounded border-gray-300 text-blue-600" />
              Show a colour swatch for each choice
            </label>
            @if (!disabled()) {
              <button type="button" (click)="removeOption(oi)" data-testid="option-remove"
                class="ml-auto pb-2 text-xs font-medium text-red-500 hover:underline">Remove option</button>
            }
          </div>

          <p class="mt-2 text-xs font-medium text-gray-600">Choices</p>
          <div class="mt-1 flex flex-wrap gap-2">
            @for (value of option.values; track $index; let vi = $index) {
              <span class="inline-flex items-center gap-1 rounded-lg border border-gray-200 bg-white py-1 pl-1.5 pr-1" data-testid="option-value">
                @if (option.kind === 'COLOUR') {
                  <input type="color" [ngModel]="value.hex || '#111111'" (ngModelChange)="setHex(oi, vi, $event)" [disabled]="disabled()"
                    [attr.aria-label]="'Swatch for ' + (value.value || 'this choice')"
                    class="h-6 w-6 cursor-pointer rounded border border-gray-200 bg-white p-0" />
                }
                <input [ngModel]="value.value" (ngModelChange)="setValue(oi, vi, $event)" [disabled]="disabled()" maxlength="40"
                  [placeholder]="option.kind === 'COLOUR' ? 'Black' : 'Choice'" [attr.aria-label]="'Choice ' + (vi + 1) + ' of ' + (option.name || 'this option')"
                  data-testid="option-value-name"
                  class="w-24 rounded border-0 px-1 py-0.5 text-sm outline-none focus:ring-1 focus:ring-blue-300" />
                @if (!disabled()) {
                  <button type="button" (click)="removeValue(oi, vi)" [attr.aria-label]="'Remove ' + (value.value || 'this choice')"
                    class="flex h-5 w-5 items-center justify-center rounded text-gray-400 hover:bg-red-50 hover:text-red-500">×</button>
                }
              </span>
            }
            @if (!disabled()) {
              <button type="button" (click)="addValue(oi)" data-testid="option-add-value"
                class="rounded-lg border border-dashed border-blue-300 px-2.5 py-1 text-xs font-medium text-blue-600 hover:bg-blue-50">+ Add choice</button>
            }
          </div>
        </div>
      } @empty {
        <p class="rounded-lg bg-gray-50 px-3 py-2 text-xs text-gray-500" data-testid="options-none">
          No options. This product is sold in its sizes only. Add Colour, or any option of your own, if it comes in more than one.
        </p>
      }

      @if (!disabled()) {
        <div class="flex flex-wrap gap-2">
          @if (!hasColour()) {
            <button type="button" (click)="addOption('Colour', true)" data-testid="option-add-colour"
              class="rounded-lg border border-blue-200 px-3 py-1.5 text-xs font-semibold text-blue-600 hover:bg-blue-50">+ Colour</button>
          }
          <button type="button" (click)="addOption('', false)" data-testid="option-add-other"
            class="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50">+ Another option</button>
        </div>
      }
    </div>
  `,
})
export class OptionsEditorComponent {
  readonly options = model<ProductOption[]>([]);
  readonly disabled = input(false);

  protected hasColour(): boolean {
    return this.options().some((o) => o.kind === 'COLOUR');
  }

  private change(index: number, edit: (o: ProductOption) => ProductOption): void {
    this.options.set(this.options().map((o, i) => (i === index ? edit(o) : o)));
  }

  protected addOption(name: string, colour: boolean): void {
    this.options.set([...this.options(), {
      id: null, name, kind: colour ? 'COLOUR' : 'TEXT',
      values: colour ? [{ id: null, value: 'Black', hex: '#111111' }, { id: null, value: 'White', hex: '#FFFFFF' }] : [{ id: null, value: '', hex: null }],
    }]);
  }

  protected removeOption(index: number): void {
    this.options.set(this.options().filter((_, i) => i !== index));
  }

  protected rename(index: number, name: string): void {
    this.change(index, (o) => ({ ...o, name }));
  }

  protected toggleColour(index: number): void {
    this.change(index, (o) => {
      const colour = o.kind !== 'COLOUR';
      return { ...o, kind: colour ? 'COLOUR' : 'TEXT', values: o.values.map((v) => ({ ...v, hex: colour ? v.hex ?? this.guessHex(v.value) : null })) };
    });
  }

  protected addValue(index: number): void {
    this.change(index, (o) => {
      // The next colour not used yet, so a new swatch isn't black again.
      const next = STARTER_COLOURS.find(([name]) => !o.values.some((v) => v.value.trim().toLowerCase() === name.toLowerCase()));
      const value = o.kind === 'COLOUR' && next ? { id: null, value: next[0], hex: next[1] } : { id: null, value: '', hex: o.kind === 'COLOUR' ? '#111111' : null };
      return { ...o, values: [...o.values, value] };
    });
  }

  protected removeValue(index: number, valueIndex: number): void {
    this.change(index, (o) => ({ ...o, values: o.values.filter((_, i) => i !== valueIndex) }));
  }

  protected setValue(index: number, valueIndex: number, text: string): void {
    this.change(index, (o) => ({ ...o, values: o.values.map((v, i) => (i === valueIndex ? { ...v, value: text } : v)) }));
  }

  protected setHex(index: number, valueIndex: number, hex: string): void {
    this.change(index, (o) => ({ ...o, values: o.values.map((v, i) => (i === valueIndex ? { ...v, hex: hex.toUpperCase() } : v)) }));
  }

  private guessHex(name: string): string {
    return STARTER_COLOURS.find(([n]) => n.toLowerCase() === name.trim().toLowerCase())?.[1] ?? '#111111';
  }
}
