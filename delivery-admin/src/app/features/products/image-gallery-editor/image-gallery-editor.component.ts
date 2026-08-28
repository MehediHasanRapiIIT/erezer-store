import { Component, inject, input, OnChanges, signal, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import {
  ProductImageResponse,
  ProductImageService,
} from '../../../core/services/product-image.service';
import { parseApiError } from '../../../core/utils/api-error.util';

@Component({
  selector: 'app-image-gallery-editor',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="bg-white rounded-xl border border-gray-200 p-5">
      <header class="mb-3 flex items-center justify-between">
        <div>
          <h2 class="font-bold text-gray-900">Product images</h2>
          <p class="text-xs text-gray-500">Upload multiple images. Set one as primary; reorder by changing sort index.</p>
        </div>

        <label class="cursor-pointer px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg">
          <input type="file" accept="image/*" class="hidden" (change)="onFileSelected($event)" />
          {{ uploading() ? 'Uploading…' : '+ Upload image' }}
        </label>
      </header>

      @if (error()) {
        <p class="mb-3 rounded-md bg-red-50 px-3 py-2 text-xs text-red-700">{{ error() }}</p>
      }

      @if (loading()) {
        <p class="text-sm text-gray-400">Loading images…</p>
      } @else if (images().length === 0) {
        <p class="rounded-md bg-gray-50 px-3 py-2 text-sm text-gray-500">
          No images uploaded yet. Drop the first one here — it'll be marked primary automatically.
        </p>
      } @else {
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          @for (img of images(); track img.id) {
            <article class="rounded-xl border border-gray-200 overflow-hidden">
              <div class="relative h-48 bg-gray-100">
                <img [src]="img.url" [alt]="img.altText ?? ''" class="h-full w-full object-cover" />
                @if (img.isPrimary) {
                  <span class="absolute left-2 top-2 rounded-full bg-emerald-500 px-2 py-0.5 text-xs font-medium text-white">
                    Primary
                  </span>
                }
              </div>
              <div class="space-y-2 p-3">
                <div>
                  <label class="text-xs font-medium text-gray-500">Alt text</label>
                  <input
                    [ngModel]="img.altText"
                    (ngModelChange)="updateAlt(img, $event)"
                    (blur)="saveAlt(img)"
                    placeholder="Description for screen readers"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-2 py-1.5 text-xs"
                  />
                </div>
                <div class="flex items-center justify-between">
                  <label class="flex items-center gap-2 text-xs font-medium text-gray-500">
                    Sort
                    <input
                      type="number"
                      min="0"
                      [ngModel]="img.sortOrder"
                      (ngModelChange)="updateSort(img, $event)"
                      (blur)="saveSort(img)"
                      class="w-16 rounded-lg border border-gray-200 px-2 py-1 text-xs"
                    />
                  </label>
                  @if (!img.isPrimary) {
                    <button (click)="makePrimary(img)" class="act-btn act-btn-edit" title="Make primary">
                      <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.562.562 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.562.562 0 00-.182-.557l-4.204-3.602a.563.563 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z"/></svg>
                      Make primary
                    </button>
                  }
                </div>
                <button (click)="remove(img)" class="act-btn act-btn-delete" title="Remove image">
                  <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                  Remove
                </button>
              </div>
            </article>
          }
        </div>
      }
    </section>
  `,
})
export class ImageGalleryEditorComponent implements OnChanges {
  readonly productId = input.required<number>();

  private readonly api = inject(ProductImageService);

  readonly images    = signal<ProductImageResponse[]>([]);
  readonly loading   = signal(false);
  readonly uploading = signal(false);
  readonly error     = signal<string>('');

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['productId']) this.reload();
  }

  reload(): void {
    const id = this.productId();
    if (!id) return;
    this.loading.set(true);
    this.api.list(id).pipe(catchError(() => of([] as ProductImageResponse[]))).subscribe((list) => {
      this.images.set(list);
      this.loading.set(false);
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploading.set(true);
    this.error.set('');
    this.api.upload(this.productId(), file)
      .pipe(catchError((err) => {
        this.error.set(parseApiError(err));
        this.uploading.set(false);
        return of(null);
      }))
      .subscribe((created) => {
        this.uploading.set(false);
        input.value = '';
        if (created) this.reload();
      });
  }

  // ── inline metadata edits ───────────────────────────────────────────────────

  protected updateAlt(img: ProductImageResponse, value: string): void {
    img.altText = value;
  }

  protected saveAlt(img: ProductImageResponse): void {
    this.api.updateMetadata(this.productId(), img.id, { altText: img.altText ?? null })
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); return of(null); }))
      .subscribe();
  }

  protected updateSort(img: ProductImageResponse, value: number): void {
    img.sortOrder = value;
  }

  protected saveSort(img: ProductImageResponse): void {
    this.api.updateMetadata(this.productId(), img.id, { sortOrder: img.sortOrder })
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); return of(null); }))
      .subscribe(() => this.reload());
  }

  makePrimary(img: ProductImageResponse): void {
    this.api.updateMetadata(this.productId(), img.id, { isPrimary: true })
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); return of(null); }))
      .subscribe(() => this.reload());
  }

  remove(img: ProductImageResponse): void {
    if (!confirm('Delete this image?')) return;
    this.api.delete(this.productId(), img.id)
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); return of(null); }))
      .subscribe(() => this.reload());
  }
}
