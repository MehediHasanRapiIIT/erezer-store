import { Component, computed, effect, input, model, OnDestroy, signal } from '@angular/core';

/** The same limits the server keeps, checked here first so nobody waits for a refusal. */
export const MAX_PICTURES = 10;
const MAX_PICTURE_BYTES = 15 * 1024 * 1024;
/** What one save may carry; photos go up at their original size. */
const MAX_TOTAL_BYTES = 240 * 1024 * 1024;

/**
 * Pictures for a product that hasn't been saved yet. They stay in the browser
 * until "Save product", and go up with everything else in one request.
 *
 * Several can be chosen or dropped at once. The first is the main picture —
 * the one on the shop's product cards — and the arrows change the order.
 */
@Component({
  selector: 'app-picture-picker',
  standalone: true,
  template: `
    <div>
      <!-- "relative": the hidden file input inside is placed absolutely, and has
           to be placed against this box, not the whole page (which it would stretch). -->
      <label
        class="relative flex cursor-pointer flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed px-4 py-6 text-center transition-colors"
        [class.border-blue-400]="dragging()" [class.bg-blue-50]="dragging()"
        [class.border-gray-200]="!dragging()" [class.hover:border-blue-300]="!dragging()"
        [class.opacity-50]="disabled() || files().length >= max" [class.cursor-not-allowed]="disabled() || files().length >= max"
        (dragover)="onDragOver($event)" (dragleave)="dragging.set(false)" (drop)="onDrop($event)">
        <input type="file" accept="image/*,.heic,.heif,.avif" multiple class="sr-only" data-testid="picture-input"
          [disabled]="disabled() || files().length >= max" (change)="onChosen($event)" />
        <svg class="h-7 w-7 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 16V4m0 0l-4 4m4-4l4 4M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2"/>
        </svg>
        <span class="text-sm font-medium text-gray-700">Drop pictures here, or click to choose</span>
        <span class="text-xs text-gray-400">Choose several at once · up to {{ max }} · 15 MB each</span>
      </label>
      <p class="picture-hint text-xs text-gray-500 mt-2" data-testid="picture-hint"><span class="font-semibold text-gray-700">Best size:</span> 1200 × 1500 px, upright. Any shape works and the whole picture is always shown, but upright pictures fill the product card best.</p>

      @if (problem()) {
        <p class="mt-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs font-medium text-red-600" data-testid="picture-problem">{{ problem() }}</p>
      }

      @if (files().length > 0) {
        <ul class="mt-3 grid grid-cols-3 gap-3 sm:grid-cols-4" data-testid="picture-list">
          @for (file of files(); track file; let i = $index; let first = $first; let last = $last) {
            <li class="group relative overflow-hidden rounded-lg border bg-gray-50"
              [class.border-blue-500]="first" [class.ring-2]="first" [class.ring-blue-200]="first"
              [class.border-gray-200]="!first">
              <img [src]="previews()[i]" [alt]="file.name" class="aspect-square w-full object-cover" />
              @if (first) {
                <span class="absolute left-1.5 top-1.5 rounded bg-blue-600 px-1.5 py-0.5 text-[10px] font-bold uppercase text-white">Main</span>
              }
              <button type="button" (click)="remove(i)" [disabled]="disabled()"
                [attr.aria-label]="'Remove ' + file.name"
                class="absolute right-1.5 top-1.5 flex h-6 w-6 items-center justify-center rounded-full bg-black/60 text-sm text-white hover:bg-black/80">×</button>
              <div class="flex items-center justify-between gap-1 border-t border-gray-200 bg-white px-1.5 py-1">
                <button type="button" (click)="move(i, -1)" [disabled]="first || disabled()"
                  [attr.aria-label]="'Move ' + file.name + ' earlier'"
                  class="rounded px-1.5 text-gray-500 hover:bg-gray-100 disabled:opacity-30">‹</button>
                @if (!first) {
                  <button type="button" (click)="makeMain(i)" [disabled]="disabled()"
                    class="truncate text-[11px] font-medium text-blue-600 hover:underline">Make main</button>
                } @else {
                  <span class="text-[11px] text-gray-400">Shown first</span>
                }
                <button type="button" (click)="move(i, 1)" [disabled]="last || disabled()"
                  [attr.aria-label]="'Move ' + file.name + ' later'"
                  class="rounded px-1.5 text-gray-500 hover:bg-gray-100 disabled:opacity-30">›</button>
              </div>
            </li>
          }
        </ul>
      }
    </div>
  `,
})
export class PicturePickerComponent implements OnDestroy {
  readonly files = model<File[]>([]);
  readonly disabled = input(false);
  readonly max = MAX_PICTURES;

  readonly dragging = signal(false);
  readonly problem = signal('');

  /** One preview address per picture, made once and released when no longer shown. */
  private readonly urls = new Map<File, string>();
  readonly previews = computed(() => this.files().map((f) => this.previewOf(f)));

  constructor() {
    // Release the previews of pictures that were removed.
    effect(() => {
      const kept = new Set(this.files());
      for (const [file, url] of this.urls) {
        if (!kept.has(file)) {
          URL.revokeObjectURL(url);
          this.urls.delete(file);
        }
      }
    });
  }

  ngOnDestroy(): void {
    for (const url of this.urls.values()) URL.revokeObjectURL(url);
    this.urls.clear();
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    if (!this.disabled()) this.dragging.set(true);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    if (this.disabled()) return;
    this.add(Array.from(event.dataTransfer?.files ?? []));
  }

  onChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.add(Array.from(input.files ?? []));
    input.value = '';   // so choosing the same picture again still counts
  }

  remove(index: number): void {
    this.files.update((list) => list.filter((_, i) => i !== index));
    this.problem.set('');
  }

  move(index: number, by: -1 | 1): void {
    const target = index + by;
    this.files.update((list) => {
      if (target < 0 || target >= list.length) return list;
      const next = [...list];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  makeMain(index: number): void {
    this.files.update((list) => [list[index], ...list.filter((_, i) => i !== index)]);
  }

  /** Adds what it can and says plainly what it left out, and why. */
  private add(chosen: File[]): void {
    const refused: string[] = [];
    const next = [...this.files()];
    let total = next.reduce((sum, f) => sum + f.size, 0);

    for (const file of chosen) {
      if (!looksLikePicture(file)) {
        refused.push(`${file.name} isn't a picture`);
      } else if (file.size > MAX_PICTURE_BYTES) {
        refused.push(`${file.name} is over 15 MB`);
      } else if (next.length >= MAX_PICTURES) {
        refused.push(`${file.name} — a product can have ${MAX_PICTURES} pictures`);
      } else if (total + file.size > MAX_TOTAL_BYTES) {
        refused.push(`${file.name} — all the pictures together are too large to send at once`);
      } else {
        next.push(file);
        total += file.size;
      }
    }
    this.files.set(next);
    this.problem.set(refused.length ? `Left out: ${refused.join('; ')}.` : '');
  }

  private previewOf(file: File): string {
    let url = this.urls.get(file);
    if (!url) {
      url = URL.createObjectURL(file);
      this.urls.set(file, url);
    }
    return url;
  }
}

/** Phones send HEIC with an empty type, so the name counts too. The server checks the bytes. */
function looksLikePicture(file: File): boolean {
  return file.type.startsWith('image/') || /\.(jpe?g|png|gif|webp|heic|heif|avif)$/i.test(file.name);
}
