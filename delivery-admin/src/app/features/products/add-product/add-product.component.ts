import { Component, signal, computed, OnInit, inject } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { ProductService } from '../../../core/services/product.service';
import { CategoryService } from '../../../core/services/category.service';
import { StockDisplay, CategoryResponse, ProductRequest } from '../../../core/models/api.models';
import { parseApiError } from '../../../core/utils/api-error.util';
import { PermissionService } from '../../../core/services/permission.service';
import { NoticeService } from '../../../core/services/notice.service';
import {
  DiscountInputComponent, DiscountMode, discountFields, discountProblem,
} from '../shared/discount-input.component';
import { PicturePickerComponent } from '../shared/picture-picker.component';
import { SizeGridComponent, SizeRow, emptySizeRows, pickedSizes } from '../shared/size-grid.component';

@Component({
  selector: 'app-add-product',
  standalone: true,
  imports: [RouterLink, FormsModule, SidebarComponent, DiscountInputComponent, PicturePickerComponent, SizeGridComponent],
  templateUrl: './add-product.component.html',
})
export class AddProductComponent implements OnInit {
  private router = inject(Router);
  private productService = inject(ProductService);
  private categoryService = inject(CategoryService);
  protected readonly perms = inject(PermissionService);
  private readonly notices = inject(NoticeService);

  // Form fields
  productName  = signal('');
  /** Typed by staff; required, and several products may share one. */
  productCode  = signal('');
  description  = signal('');
  basePrice    = signal<number | null>(null);
  /** The sale discount, typed as a percentage or as an amount off in taka. */
  discountMode  = signal<DiscountMode>('PERCENT');
  discountValue = signal<number | null>(null);
  /** Pictures waiting to go up with the product; the first is the main one. */
  pictures     = signal<File[]>([]);
  /** Sizes to add with the product, ticked in the grid. */
  sizeRows     = signal<SizeRow[]>(emptySizeRows());
  categoryId   = signal<number | null>(null);
  shopId       = signal(1);
  isAvailable  = signal(true);
  isNewArrival = signal(false);
  isFeatured   = signal(false);
  /** Keep this product at full price, ignoring every automatic discount. */
  discountExcluded = signal(false);
  /** Stock on the product page: follow the category (default), the quantity, or labels. */
  stockDisplay = signal<StockDisplay>('CATEGORY');
  protected readonly stockDisplayOptions: { value: StockDisplay; label: string }[] = [
    { value: 'CATEGORY', label: 'Same as category' },
    { value: 'QUANTITY', label: 'Show quantity' },
    { value: 'LABEL', label: 'Show labels' },
  ];
  /** What "Same as category" means right now, for the chosen category. */
  protected readonly categoryStockHint = computed(() => {
    const cat = this.categories().find((c) => c.id === this.categoryId());
    if (!cat) return 'choose a category';
    return (cat.effectiveShowStockQuantity ?? cat.showStockQuantity) ? `${cat.name} shows quantities` : `${cat.name} shows labels`;
  });


  // State
  categories   = signal<CategoryResponse[]>([]);
  isLoading    = signal(false);
  /** How much of the save has gone up, 0–100, while it is sending. */
  uploaded     = signal<number | null>(null);
  errorMessage = signal('');
  fieldErrors  = signal<Record<string, string>>({});

  readonly descMax = 500;
  descLength = computed(() => this.description().length);

  isDirty = computed(() =>
    !!this.productName() || !!this.productCode() || !!this.description() || !!this.basePrice()
    || this.pictures().length > 0 || this.sizeRows().some((r) => r.picked)
  );

  /** What the Save button will send, so the footer can say it. */
  readonly saveSummary = computed(() => {
    const pics = this.pictures().length;
    const sizes = this.sizeRows().filter((r) => r.picked).length;
    const parts = [];
    if (pics) parts.push(`${pics} picture${pics === 1 ? '' : 's'}`);
    if (sizes) parts.push(`${sizes} size${sizes === 1 ? '' : 's'}`);
    return parts.length ? `Saves the product with ${parts.join(' and ')}.` : '';
  });

  /** The code last suggested, so a new category replaces it but never a code staff typed. */
  private suggestedCode = '';

  /**
   * Choosing a category fills in its next code (e.g. EP-1003) unless a code has
   * already been typed. Staff can still change it.
   */
  onCategoryChange(id: number | null): void {
    this.categoryId.set(id);
    const current = this.productCode().trim();
    if (!id || (current !== '' && current !== this.suggestedCode)) return;
    this.productService.nextCodes(id, 1).subscribe({
      next: ([code]) => {
        // Typed while the suggestion was on its way: leave it.
        if (this.productCode().trim() !== current) return;
        this.suggestedCode = code ?? '';
        this.productCode.set(this.suggestedCode);
      },
      error: () => { /* a suggestion only */ },
    });
  }

  ngOnInit() {
    this.categoryService.getCategories().subscribe({
      next: (cats) => this.categories.set(cats),
      error: (err) => this.errorMessage.set(parseApiError(err)),
    });
  }

  onSave() {
    this.errorMessage.set('');
    this.fieldErrors.set({});

    if (!this.productName().trim()) {
      this.fieldErrors.set({ name: 'Product name is required.' });
      return;
    }
    if (!this.productCode().trim()) {
      this.fieldErrors.set({ productCode: 'Product code is required.' });
      return;
    }
    if (!this.basePrice() || this.basePrice()! <= 0) {
      this.fieldErrors.set({ price: 'A valid price is required.' });
      return;
    }
    if (!this.categoryId()) {
      this.fieldErrors.set({ categoryId: 'Please select a category.' });
      return;
    }
    const discountIssue = discountProblem(this.basePrice(), this.discountMode(), this.discountValue());
    if (discountIssue) {
      this.fieldErrors.set({ discount: discountIssue });
      return;
    }

    const dto: ProductRequest = {
      name: this.productName().trim(),
      productCode: this.productCode().trim(),
      description: this.description().trim(),
      price: this.basePrice()!,
      ...discountFields(this.discountMode(), this.discountValue()),
      categoryId: this.categoryId()!,
      shopId: this.shopId(),
      isAvailable: this.isAvailable(),
      isNewArrival: this.isNewArrival(),
      isFeatured: this.isFeatured(),
      discountExcluded: this.discountExcluded(),
      stockDisplay: this.stockDisplay(),
    };

    const sizes = this.perms.can('products.variants') ? pickedSizes(this.sizeRows()) : [];
    const pictures = this.perms.can('products.images') ? this.pictures() : [];

    // One request: the server saves the product, its pictures and its sizes
    // together, or none of them, so the form stays filled in if anything is wrong.
    this.isLoading.set(true);
    this.uploaded.set(pictures.length ? 0 : null);
    this.productService.createWithEverything(dto, sizes, pictures,
      (percent) => this.uploaded.set(percent)).subscribe({
      next: (created) => {
        this.isLoading.set(false);
        this.uploaded.set(null);
        const extras = [
          pictures.length ? `${pictures.length} picture${pictures.length === 1 ? '' : 's'}` : '',
          sizes.length ? `${sizes.length} size${sizes.length === 1 ? '' : 's'}` : '',
        ].filter(Boolean).join(' and ');
        this.notices.success('Product added', extras ? `${created.name}, with ${extras}` : created.name);
        this.router.navigate(['/products']);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.uploaded.set(null);
        const body = err?.error;
        if (body?.errors) {
          this.fieldErrors.set(body.errors);
        } else {
          this.errorMessage.set(parseApiError(err));
        }
      },
    });
  }

  onCancel() {
    this.router.navigate(['/products']);
  }
}
