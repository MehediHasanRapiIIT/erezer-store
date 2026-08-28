import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import {
  CustomDesignAssetService,
  CustomDesignColorAdmin,
  CustomDesignItemAdmin,
  CustomDesignLogoAdmin,
} from '../../core/services/custom-design-asset.service';
import { parseApiError } from '../../core/utils/api-error.util';

type ColorView = 'front' | 'back' | 'leftSleeve' | 'rightSleeve';
const COLOR_VIEWS: { key: ColorView; label: string }[] = [
  { key: 'front', label: 'Front' },
  { key: 'back', label: 'Back' },
  { key: 'leftSleeve', label: 'Left' },
  { key: 'rightSleeve', label: 'Right' },
];

@Component({
  selector: 'app-custom-design',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <h1 class="text-lg font-bold text-gray-900">Custom design studio</h1>
          <button (click)="newItem()" class="rounded-lg bg-blue-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-blue-700">
            + New garment
          </button>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-5xl mx-auto space-y-8">

            @if (error()) {
              <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
            }

            <!-- ── Editor ─────────────────────────────────────────────────── -->
            @if (editing; as it) {
              <section class="rounded-xl border border-blue-200 bg-white p-5 space-y-4">
                <h2 class="text-base font-semibold">{{ it.id ? 'Edit garment' : 'New garment' }}</h2>

                <div class="grid gap-3 sm:grid-cols-2">
                  <label class="text-xs font-semibold uppercase text-gray-400">Name
                    <input [(ngModel)]="it.name" class="mt-1 block w-full rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-800" />
                  </label>
                  <label class="text-xs font-semibold uppercase text-gray-400">Category
                    <input [(ngModel)]="it.category" placeholder="Men / Women / Accessories" class="mt-1 block w-full rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-800" />
                  </label>
                  <label class="text-xs font-semibold uppercase text-gray-400">Sizes (comma-separated)
                    <input [(ngModel)]="sizesText" placeholder="S, M, L, XL" class="mt-1 block w-full rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-800" />
                  </label>
                  <label class="text-xs font-semibold uppercase text-gray-400">Print techniques (comma-separated)
                    <input [(ngModel)]="techText" placeholder="Screen print, Embroidery, DTG" class="mt-1 block w-full rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-800" />
                  </label>
                  <label class="flex items-center gap-2 text-sm text-gray-700">
                    <input type="checkbox" [(ngModel)]="it.active" /> Active
                  </label>
                  <label class="text-xs font-semibold uppercase text-gray-400">Sort order
                    <input type="number" [(ngModel)]="it.sortOrder" class="mt-1 block w-24 rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-800" />
                  </label>
                </div>

                <!-- Colours -->
                <div class="space-y-3">
                  <div class="flex items-center justify-between">
                    <h3 class="text-xs font-semibold uppercase text-gray-400">Colours &amp; mockups</h3>
                    <button (click)="addColor(it)" class="text-xs font-medium text-blue-600 hover:underline">+ Add colour</button>
                  </div>
                  @for (color of it.colors; track $index) {
                    <div class="rounded-lg border border-gray-200 p-3">
                      <div class="mb-2 flex items-center gap-2">
                        <input type="color" [(ngModel)]="color.hex" class="h-8 w-10 cursor-pointer rounded" />
                        <input [(ngModel)]="color.name" placeholder="Colour name" class="flex-1 rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-800" />
                        <button (click)="removeColor(it, $index)" class="text-xs font-medium text-red-500 hover:text-red-600">Remove</button>
                      </div>
                      <div class="grid grid-cols-4 gap-2">
                        @for (v of colorViews; track v.key) {
                          <label class="flex cursor-pointer flex-col items-center gap-1 rounded-lg border border-dashed border-gray-200 p-2 text-center text-xs text-gray-400 hover:bg-gray-50">
                            <input type="file" accept="image/*" class="hidden" (change)="onColorImage(color, v.key, $event)" />
                            @if (colorImage(color, v.key); as url) {
                              <img [src]="url" [alt]="v.label" class="h-14 w-14 object-contain" />
                            } @else {
                              <span class="text-lg">＋</span>
                            }
                            {{ v.label }}
                          </label>
                        }
                      </div>
                    </div>
                  } @empty {
                    <p class="text-xs text-gray-400">No colours yet. Add at least one so the studio has a mockup.</p>
                  }
                </div>

                <div class="flex gap-2 border-t border-gray-100 pt-3">
                  <button (click)="saveItem(it)" [disabled]="acting() || !it.name.trim()"
                    class="rounded-lg bg-blue-600 px-4 py-1.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                    {{ acting() ? 'Saving…' : 'Save garment' }}
                  </button>
                  <button (click)="cancelEdit()" class="rounded-lg border border-gray-200 px-4 py-1.5 text-sm text-gray-700 hover:bg-gray-50">Cancel</button>
                </div>
              </section>
            }

            <!-- ── Garment list ───────────────────────────────────────────── -->
            <section>
              <h2 class="mb-3 text-sm font-semibold uppercase text-gray-400">Garments</h2>
              @if (loading()) {
                <p class="text-sm text-gray-400">Loading…</p>
              } @else {
                <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  @for (it of items(); track it.id) {
                    <article class="rounded-xl border border-gray-200 bg-white p-4">
                      <div class="flex items-start justify-between">
                        <div>
                          <h3 class="font-medium text-gray-900">{{ it.name }}</h3>
                          <p class="text-xs text-gray-400">{{ it.category || '—' }} · {{ it.colors.length }} colour(s)</p>
                        </div>
                        @if (!it.active) { <span class="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-500">Hidden</span> }
                      </div>
                      <div class="mt-2 flex gap-1">
                        @for (c of it.colors; track c.id) {
                          <span class="h-5 w-5 rounded-full border border-gray-200" [style.background-color]="c.hex" [title]="c.name"></span>
                        }
                      </div>
                      <div class="mt-3 flex gap-2 border-t border-gray-100 pt-2">
                        <button (click)="editItem(it)" class="text-xs font-medium text-blue-600 hover:underline">Edit</button>
                        <button (click)="deleteItem(it)" class="ml-auto text-xs font-medium text-red-500 hover:text-red-600">Delete</button>
                      </div>
                    </article>
                  } @empty {
                    <p class="text-sm text-gray-400">No garments yet.</p>
                  }
                </div>
              }
            </section>

            <!-- ── Logo library ───────────────────────────────────────────── -->
            <section>
              <h2 class="mb-3 text-sm font-semibold uppercase text-gray-400">Logo library</h2>
              <div class="rounded-xl border border-gray-200 bg-white p-4">
                <div class="mb-4 flex flex-wrap items-end gap-2">
                  <label class="text-xs font-semibold uppercase text-gray-400">Name
                    <input [(ngModel)]="newLogoName" placeholder="Logo name" class="mt-1 block rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-800" />
                  </label>
                  <label class="flex cursor-pointer items-center gap-2 rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50">
                    <input type="file" accept="image/*" class="hidden" (change)="onLogoFile($event)" />
                    {{ newLogoUrl ? 'Change image' : 'Choose image' }}
                  </label>
                  @if (newLogoUrl) { <img [src]="newLogoUrl" alt="" class="h-10 w-10 rounded object-contain border border-gray-100" /> }
                  <button (click)="addLogo()" [disabled]="acting() || !newLogoName.trim() || !newLogoUrl"
                    class="rounded-lg bg-blue-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">Add logo</button>
                </div>
                <div class="grid grid-cols-3 gap-3 sm:grid-cols-6">
                  @for (logo of logos(); track logo.id) {
                    <figure class="group relative rounded-lg border border-gray-200 p-2 text-center">
                      <img [src]="logo.url" [alt]="logo.name" class="mx-auto h-14 w-14 object-contain" />
                      <figcaption class="mt-1 truncate text-xs text-gray-500">{{ logo.name }}</figcaption>
                      <button (click)="deleteLogo(logo)" class="absolute right-1 top-1 hidden rounded-full bg-red-500 px-1.5 text-xs text-white group-hover:block">✕</button>
                    </figure>
                  } @empty {
                    <p class="col-span-full text-sm text-gray-400">No logos yet.</p>
                  }
                </div>
              </div>
            </section>
          </div>
        </main>
      </div>
    </div>
  `,
})
export class CustomDesignComponent implements OnInit {
  private readonly api = inject(CustomDesignAssetService);

  readonly colorViews = COLOR_VIEWS;
  readonly items = signal<CustomDesignItemAdmin[]>([]);
  readonly logos = signal<CustomDesignLogoAdmin[]>([]);
  readonly loading = signal(false);
  readonly acting = signal(false);
  readonly error = signal<string>('');

  protected editing: CustomDesignItemAdmin | null = null;
  protected sizesText = '';
  protected techText = '';

  protected newLogoName = '';
  protected newLogoUrl = '';

  ngOnInit(): void {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.listItems().pipe(catchError((err) => { this.error.set(parseApiError(err)); return of([]); }))
      .subscribe((list) => { this.items.set(list); this.loading.set(false); });
    this.api.listLogos().pipe(catchError(() => of([]))).subscribe((list) => this.logos.set(list));
  }

  // ── Garment editor ──────────────────────────────────────────────────────

  protected newItem(): void {
    this.editing = { name: '', category: '', active: true, sortOrder: 0, sizes: [], printTechniques: [], colors: [] };
    this.sizesText = '';
    this.techText = '';
  }

  protected editItem(it: CustomDesignItemAdmin): void {
    this.editing = structuredClone(it);
    this.sizesText = it.sizes.join(', ');
    this.techText = it.printTechniques.join(', ');
  }

  protected cancelEdit(): void {
    this.editing = null;
  }

  protected addColor(it: CustomDesignItemAdmin): void {
    it.colors.push({ name: '', hex: '#ffffff', frontImageUrl: null, backImageUrl: null, leftSleeveImageUrl: null, rightSleeveImageUrl: null });
  }

  protected removeColor(it: CustomDesignItemAdmin, index: number): void {
    it.colors.splice(index, 1);
  }

  protected colorImage(color: CustomDesignColorAdmin, view: ColorView): string | null {
    return color[`${view}ImageUrl`] ?? null;
  }

  protected onColorImage(color: CustomDesignColorAdmin, view: ColorView, event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.api.uploadImage(file).pipe(catchError((err) => { this.error.set(parseApiError(err)); return of(''); }))
      .subscribe((url) => { if (url) color[`${view}ImageUrl`] = url; });
  }

  protected saveItem(it: CustomDesignItemAdmin): void {
    this.acting.set(true);
    this.error.set('');
    const payload: CustomDesignItemAdmin = {
      ...it,
      sizes: this.splitList(this.sizesText),
      printTechniques: this.splitList(this.techText),
    };
    this.api.upsertItem(payload)
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); this.acting.set(false); return of(null); }))
      .subscribe((saved) => {
        this.acting.set(false);
        if (saved) { this.editing = null; this.reload(); }
      });
  }

  protected deleteItem(it: CustomDesignItemAdmin): void {
    if (!it.id || !confirm(`Delete "${it.name}"?`)) return;
    this.api.deleteItem(it.id).pipe(catchError((err) => { this.error.set(parseApiError(err)); return of(null); }))
      .subscribe(() => this.items.update((list) => list.filter((x) => x.id !== it.id)));
  }

  // ── Logo library ────────────────────────────────────────────────────────

  protected onLogoFile(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!this.newLogoName.trim()) this.newLogoName = file.name.replace(/\.[^.]+$/, '');
    this.api.uploadImage(file).pipe(catchError((err) => { this.error.set(parseApiError(err)); return of(''); }))
      .subscribe((url) => { if (url) this.newLogoUrl = url; });
  }

  protected addLogo(): void {
    if (!this.newLogoName.trim() || !this.newLogoUrl) return;
    this.acting.set(true);
    this.api.upsertLogo({ name: this.newLogoName.trim(), url: this.newLogoUrl, active: true })
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); this.acting.set(false); return of(null); }))
      .subscribe((saved) => {
        this.acting.set(false);
        if (saved) { this.logos.update((list) => [...list, saved]); this.newLogoName = ''; this.newLogoUrl = ''; }
      });
  }

  protected deleteLogo(logo: CustomDesignLogoAdmin): void {
    if (!logo.id || !confirm(`Delete "${logo.name}"?`)) return;
    this.api.deleteLogo(logo.id).pipe(catchError((err) => { this.error.set(parseApiError(err)); return of(null); }))
      .subscribe(() => this.logos.update((list) => list.filter((x) => x.id !== logo.id)));
  }

  private splitList(text: string): string[] {
    return text.split(',').map((s) => s.trim()).filter(Boolean);
  }
}
