import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { ProductService, BatchItem } from '../../../core/services/product.service';
import { CategoryService } from '../../../core/services/category.service';
import { CategoryResponse, ProductRequest } from '../../../core/models/api.models';
import { parseApiError } from '../../../core/utils/api-error.util';
import { PermissionService } from '../../../core/services/permission.service';
import { NoticeService } from '../../../core/services/notice.service';
import {
  DiscountInputComponent, DiscountMode, discountFields, discountProblem,
} from '../shared/discount-input.component';
import { SizeGridComponent, SizeRow, emptySizeRows, pickedSizes } from '../shared/size-grid.component';

/** The server's limits, checked here first. */
const MAX_PRODUCTS = 20;
const MAX_PICTURES = 10;
/**
 * What one save may carry: photos go up at their original size, never shrunk,
 * and the server takes up to 240 MB for adding products.
 */
const MAX_TOTAL_BYTES = 240 * 1024 * 1024;
const MAX_PICTURE_BYTES = 15 * 1024 * 1024;

/** One product in the batch. */
interface BatchRow {
  /** Stable identity for the list; rows move and disappear. */
  key: number;
  name: string;
  code: string;
  /** Typed by staff, so suggestions leave it alone. */
  codeTyped: boolean;
  /** Its own price; null to use the shared one. */
  price: number | null;
  pictures: File[];
}

/**
 * Products → Add several products.
 *
 * Several products that share a category, description, price, discount and
 * sizes, each with its own name, code and pictures. Drop a shoot's photos and
 * they are dealt out into products, a few photos each, the first of each being
 * the main picture. Codes are suggested from the category and can be changed.
 * One click saves all of them, or none.
 */
@Component({
  selector: 'app-add-batch',
  standalone: true,
  imports: [RouterLink, FormsModule, SidebarComponent, DiscountInputComponent, SizeGridComponent],
  templateUrl: './add-batch.component.html',
})
export class AddBatchComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly productService = inject(ProductService);
  private readonly categoryService = inject(CategoryService);
  protected readonly perms = inject(PermissionService);
  private readonly notices = inject(NoticeService);

  // ── shared by every product ───────────────────────────────────────────────
  readonly categories = signal<CategoryResponse[]>([]);
  readonly categoryId = signal<number | null>(null);
  readonly description = signal('');
  readonly price = signal<number | null>(null);
  readonly discountMode = signal<DiscountMode>('PERCENT');
  readonly discountValue = signal<number | null>(null);
  readonly sizeRows = signal<SizeRow[]>(emptySizeRows());
  readonly isAvailable = signal(true);

  // ── the products ──────────────────────────────────────────────────────────
  readonly rows = signal<BatchRow[]>([]);
  /** How many dropped photos make one product. */
  readonly photosPerProduct = signal(4);
  readonly maxProducts = MAX_PRODUCTS;

  // ── state ─────────────────────────────────────────────────────────────────
  readonly preparing = signal('');
  readonly saving = signal(false);
  /** How much of the save has gone up, 0–100, while it is sending. */
  readonly uploaded = signal<number | null>(null);
  readonly dragging = signal(false);
  readonly errorMessage = signal('');
  /** Row numbers (from 1) a message is about, to mark them in the list. */
  readonly badRows = signal<Set<number>>(new Set());

  private nextKey = 1;
  private latestCodeRequest = 0;
  private readonly previewUrls = new Map<File, string>();

  readonly categoryName = computed(() =>
    this.categories().find((c) => c.id === this.categoryId())?.name ?? '');
  readonly totalPictures = computed(() => this.rows().reduce((n, r) => n + r.pictures.length, 0));
  readonly totalBytes = computed(() =>
    this.rows().reduce((n, r) => n + r.pictures.reduce((m, f) => m + f.size, 0), 0));
  readonly canAddPictures = computed(() => this.perms.can('products.images'));
  readonly canAddSizes = computed(() => this.perms.can('products.variants'));

  readonly saveLabel = computed(() => {
    const n = this.rows().length;
    const sent = this.uploaded();
    if (this.saving()) {
      return sent != null && sent < 100 ? `Uploading ${sent}%…` : 'Saving…';
    }
    return n === 0 ? 'Save products' : `Save ${n} product${n === 1 ? '' : 's'}`;
  });

  readonly summary = computed(() => {
    const n = this.rows().length;
    if (n === 0) return '';
    const pics = this.totalPictures();
    const sizes = this.sizeRows().filter((r) => r.picked).length;
    const parts = [`${n} product${n === 1 ? '' : 's'}`];
    if (pics) parts.push(`${pics} picture${pics === 1 ? '' : 's'}`);
    if (sizes) parts.push(`${sizes} size${sizes === 1 ? '' : 's'} each`);
    return parts.join(' · ') + ` · ${(this.totalBytes() / 1024 / 1024).toFixed(1)} MB to send`;
  });

  ngOnInit(): void {
    // Opened from a category on the Products list: start in that category.
    const fromList = Number(this.route.snapshot.queryParamMap.get('categoryId'));
    if (fromList > 0) this.categoryId.set(fromList);
    this.categoryService.getCategories().subscribe({
      next: (cats) => this.categories.set(cats),
      error: (err) => this.errorMessage.set(parseApiError(err)),
    });
  }

  ngOnDestroy(): void {
    for (const url of this.previewUrls.values()) URL.revokeObjectURL(url);
    this.previewUrls.clear();
  }

  // ── category and codes ────────────────────────────────────────────────────

  onCategoryChange(id: number | null): void {
    this.categoryId.set(id);
    this.refreshCodes();
  }

  /**
   * Fills in a suggested code for every row whose code staff haven't typed,
   * in row order, carrying on from the highest code the category already has.
   */
  refreshCodes(): void {
    const categoryId = this.categoryId();
    const open = this.rows().filter((r) => !r.codeTyped);
    if (open.length === 0) return;
    if (!categoryId) {
      this.rows.update((rows) => rows.map((r) => (r.codeTyped ? r : { ...r, code: '' })));
      return;
    }
    const request = ++this.latestCodeRequest;
    this.productService.nextCodes(categoryId, open.length).subscribe({
      next: (codes) => {
        if (request !== this.latestCodeRequest) return;
        let next = 0;
        this.rows.update((rows) => rows.map((r) => (r.codeTyped ? r : { ...r, code: codes[next++] ?? '' })));
      },
      error: () => { /* suggestions only: staff can still type codes */ },
    });
  }

  onCodeTyped(index: number, value: string): void {
    const code = value ?? '';
    // Cleared: hand the row back to the suggestions.
    this.patchRow(index, { code, codeTyped: code.trim() !== '' });
    if (code.trim() === '') this.refreshCodes();
  }

  // ── photos ────────────────────────────────────────────────────────────────

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  async onDrop(event: DragEvent): Promise<void> {
    event.preventDefault();
    this.dragging.set(false);
    await this.dealOut(Array.from(event.dataTransfer?.files ?? []));
  }

  async onChosen(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    await this.dealOut(files);
  }

  /**
   * Photos in, products out: sorted by name (a camera numbers its shots in
   * order), then dealt out a few at a time, the first of each being the main
   * picture, and each product named after its first photo.
   */
  private async dealOut(chosen: File[]): Promise<void> {
    this.errorMessage.set('');
    const { pictures, refused } = await this.prepare(chosen);
    const per = Math.max(1, Math.min(MAX_PICTURES, Math.trunc(this.photosPerProduct() || 1)));
    const room = MAX_PRODUCTS - this.rows().length;

    const fresh: BatchRow[] = [];
    for (let i = 0; i < pictures.length && fresh.length < room; i += per) {
      const group = pictures.slice(i, i + per);
      fresh.push(this.newRow(nameFromFile(group[0].name), group));
    }
    const leftOver = pictures.length - fresh.reduce((n, r) => n + r.pictures.length, 0);
    if (leftOver > 0) refused.push(`${leftOver} photo${leftOver === 1 ? '' : 's'} — a batch can have ${MAX_PRODUCTS} products`);

    this.rows.update((rows) => [...rows, ...fresh]);
    if (refused.length) this.errorMessage.set(`Left out: ${refused.join('; ')}.`);
    this.refreshCodes();
  }

  /** Adds photos to one product. */
  async addPicturesTo(index: number, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    const { pictures, refused } = await this.prepare(files);
    const row = this.rows()[index];
    const room = MAX_PICTURES - row.pictures.length;
    if (pictures.length > room) refused.push(`${pictures.length - room} — a product can have ${MAX_PICTURES} pictures`);
    this.patchRow(index, { pictures: [...row.pictures, ...pictures.slice(0, room)] });
    this.errorMessage.set(refused.length ? `Left out: ${refused.join('; ')}.` : '');
  }

  /**
   * Puts photos in name order and turns away what isn't a picture or is too
   * big, saying why. The photos themselves are never changed: they are stored
   * exactly as chosen.
   */
  private async prepare(files: File[]): Promise<{ pictures: File[]; refused: string[] }> {
    const refused: string[] = [];
    const sorted = [...files].sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
    const pictures: File[] = [];
    for (const file of sorted) {
      if (!looksLikePicture(file)) {
        refused.push(`${file.name} isn't a picture`);
      } else if (file.size > MAX_PICTURE_BYTES) {
        refused.push(`${file.name} is over 15 MB`);
      } else {
        pictures.push(file);
      }
    }
    return { pictures, refused };
  }

  removePicture(rowIndex: number, pictureIndex: number): void {
    const row = this.rows()[rowIndex];
    this.patchRow(rowIndex, { pictures: row.pictures.filter((_, i) => i !== pictureIndex) });
  }

  makeMain(rowIndex: number, pictureIndex: number): void {
    const row = this.rows()[rowIndex];
    const pictures = [row.pictures[pictureIndex], ...row.pictures.filter((_, i) => i !== pictureIndex)];
    this.patchRow(rowIndex, { pictures });
  }

  preview(file: File): string {
    let url = this.previewUrls.get(file);
    if (!url) {
      url = URL.createObjectURL(file);
      this.previewUrls.set(file, url);
    }
    return url;
  }

  // ── rows ──────────────────────────────────────────────────────────────────

  addRow(): void {
    if (this.rows().length >= MAX_PRODUCTS) return;
    this.rows.update((rows) => [...rows, this.newRow('', [])]);
    this.refreshCodes();
  }

  removeRow(index: number): void {
    const gone = this.rows()[index];
    for (const f of gone.pictures) {
      const url = this.previewUrls.get(f);
      if (url) URL.revokeObjectURL(url);
      this.previewUrls.delete(f);
    }
    this.rows.update((rows) => rows.filter((_, i) => i !== index));
    this.badRows.set(new Set());
    // Codes nobody typed close up, so the batch stays EP-1003, EP-1004, ...
    this.rows.update((rows) => rows.map((r) => (r.codeTyped ? r : { ...r, code: '' })));
    this.refreshCodes();
  }

  patchRow(index: number, change: Partial<BatchRow>): void {
    this.rows.update((rows) => rows.map((r, i) => (i === index ? { ...r, ...change } : r)));
  }

  setRowPrice(index: number, value: unknown): void {
    this.patchRow(index, { price: value === '' || value == null ? null : +value });
  }

  private newRow(name: string, pictures: File[]): BatchRow {
    return { key: this.nextKey++, name, code: '', codeTyped: false, price: null, pictures };
  }

  // ── save ──────────────────────────────────────────────────────────────────

  /** Everything wrong, with row numbers, so it can all be fixed in one go. */
  private problems(): { messages: string[]; rows: Set<number> } {
    const messages: string[] = [];
    const rows = new Set<number>();
    if (!this.categoryId()) messages.push('Choose the category.');
    if (!this.description().trim()) messages.push('Write the description they share.');
    const price = this.price();
    if (!price || price <= 0) messages.push('Type the price.');
    const shared = discountProblem(price, this.discountMode(), this.discountValue());
    if (shared) messages.push(shared);
    if (this.rows().length === 0) messages.push('Add at least one product: drop photos, or press “Add a row”.');
    if (this.totalBytes() > MAX_TOTAL_BYTES) {
      messages.push(`The pictures add up to ${mb(this.totalBytes())}, and one save can carry ${mb(MAX_TOTAL_BYTES)}. `
        + 'Remove a few rows, save, then add them in a second batch.');
    }

    this.rows().forEach((r, i) => {
      const n = i + 1;
      const name = r.name.trim();
      if (name.length < 2 || name.length > 100) {
        messages.push(`Row ${n}: the name needs 2 to 100 letters.`);
        rows.add(n);
      }
      if (!r.code.trim()) {
        messages.push(`Row ${n}: the product code is missing.`);
        rows.add(n);
      } else if (r.code.trim().length > 40) {
        messages.push(`Row ${n}: the product code is longer than 40 characters.`);
        rows.add(n);
      }
      if (r.price != null && !(r.price > 0)) {
        messages.push(`Row ${n}: its own price has to be more than 0.`);
        rows.add(n);
      }
      if (r.price != null && r.price > 0) {
        const own = discountProblem(r.price, this.discountMode(), this.discountValue());
        if (own) {
          messages.push(`Row ${n}: ${own}`);
          rows.add(n);
        }
      }
    });
    return { messages, rows };
  }

  save(): void {
    if (this.saving() || this.preparing()) return;
    const { messages, rows } = this.problems();
    this.badRows.set(rows);
    if (messages.length) {
      this.errorMessage.set(messages.join(' '));
      return;
    }
    this.errorMessage.set('');

    const shared: ProductRequest = {
      // Per row, not shared; the server fills them in from each row.
      name: '',
      productCode: '',
      categoryId: this.categoryId()!,
      description: this.description().trim(),
      price: this.price()!,
      ...discountFields(this.discountMode(), this.discountValue()),
      shopId: 1,
      isAvailable: this.isAvailable(),
    };
    const sizes = this.canAddSizes() ? pickedSizes(this.sizeRows()) : [];
    const items: BatchItem[] = this.rows().map((r) => ({
      name: r.name.trim(),
      productCode: r.code.trim(),
      price: r.price,
      pictures: this.canAddPictures() ? r.pictures : [],
    }));

    this.saving.set(true);
    this.uploaded.set(0);
    this.productService.createBatch(shared, sizes, items, (percent) => this.uploaded.set(percent)).subscribe({
      next: (made) => {
        this.saving.set(false);
        this.uploaded.set(null);
        const codes = made.length > 1
          ? `${made[0].productCode} to ${made[made.length - 1].productCode}`
          : made[0]?.productCode ?? '';
        this.notices.success(`${made.length} product${made.length === 1 ? '' : 's'} added`,
          `${this.categoryName()}: ${codes}`);
        this.router.navigate(['/products'], { queryParams: { categoryId: this.categoryId() } });
      },
      error: (err) => {
        this.saving.set(false);
        this.uploaded.set(null);
        const message = parseApiError(err);
        this.errorMessage.set(message);
        const row = /^Row (\d+):/.exec(message);
        this.badRows.set(row ? new Set([Number(row[1])]) : new Set());
      },
    });
  }

  cancel(): void {
    this.router.navigate(['/products']);
  }
}

/**
 * "pink-floral_dress.jpg" → "Pink Floral Dress", and the numbers that put a
 * shoot in order are dropped: "01-pink-floral.jpg" and "pink-floral-2.jpg" are
 * both "Pink Floral". Camera names like IMG_2041 are left blank to type.
 */
function nameFromFile(fileName: string): string {
  const base = fileName.replace(/\.[^.]+$/, '');
  if (/^(img|dsc|dscn|pxl|photo|image)[_\-\s]?\d+$/i.test(base) || /^\d+$/.test(base)) return '';
  return base
    .replace(/^\d+[\s_\-.]*/, '')
    .replace(/[\s_\-.]*\d+$/, '')
    .replace(/[_\-.]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, (c) => c.toUpperCase())
    .slice(0, 100);
}

function mb(bytes: number): string {
  return `${(bytes / 1024 / 1024).toFixed(0)} MB`;
}

/** Phones send HEIC with an empty type, so the name counts too. The server checks the bytes. */
function looksLikePicture(file: File): boolean {
  return file.type.startsWith('image/') || /\.(jpe?g|png|gif|webp|heic|heif|avif)$/i.test(file.name);
}
