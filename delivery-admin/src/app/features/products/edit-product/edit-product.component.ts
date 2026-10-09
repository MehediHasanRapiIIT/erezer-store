import { Component, signal, computed, OnInit, inject } from '@angular/core';
import { SizeChartPickerComponent } from '../../../shared/size-chart-picker/size-chart-picker.component';
import { RouterLink, Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { ProductService } from '../../../core/services/product.service';
import { CategoryService } from '../../../core/services/category.service';
import { StockDisplay, CategoryResponse, ProductRequest } from '../../../core/models/api.models';
import { parseApiError } from '../../../core/utils/api-error.util';
import {
  DiscountInputComponent, DiscountMode, discountFields, discountFromProduct, discountProblem,
} from '../shared/discount-input.component';
import { VariantManagerComponent } from '../variant-manager/variant-manager.component';
import { ImageGalleryEditorComponent } from '../image-gallery-editor/image-gallery-editor.component';
import { PermissionService } from '../../../core/services/permission.service';
import { NoticeService } from '../../../core/services/notice.service';

@Component({
  selector: 'app-edit-product',
  standalone: true,
  imports: [
    RouterLink,
    FormsModule,
    SidebarComponent,
    VariantManagerComponent,
    ImageGalleryEditorComponent,
    DiscountInputComponent,
    SizeChartPickerComponent,
  ],
  templateUrl: './edit-product.component.html',
})
export class EditProductComponent implements OnInit {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private productService = inject(ProductService);
  private readonly notices = inject(NoticeService);
  private categoryService = inject(CategoryService);
  protected readonly perms = inject(PermissionService);

  productId = signal<number>(0);

  // Form fields
  productName    = signal('');
  /** Typed by staff; required, and several products may share one. */
  productCode    = signal('');
  description    = signal('');
  basePrice      = signal<number | null>(null);
  /** The sale discount, typed as a percentage or as an amount off in taka. */
  discountMode   = signal<DiscountMode>('PERCENT');
  discountValue  = signal<number | null>(null);
  categoryId     = signal<number | null>(null);
  shopId         = signal(1);
  isAvailable    = signal(true);
  isNewArrival   = signal(false);
  isFeatured     = signal(false);
  /** Keep this product at full price, ignoring every automatic discount. */
  discountExcluded = signal(false);
  /** Stock on the product page: follow the category (default), the quantity, or labels. */
  stockDisplay = signal<StockDisplay>('CATEGORY');
  /** The product's own size chart; 0 for none of its own. */
  sizeChartId = signal(0);
  /** Counts saves of the product's options, so the pictures section reads the choices again. */
  optionsVersion = signal(0);
  /** A different chart for Regular Fit; 0 for the same chart. */
  regularFitSizeChartId = signal(0);
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

  // Clothing / catalog attributes
  unit            = signal('');
  lowStockThreshold = signal<number | null>(null);
  brand           = signal('');
  gender          = signal('');
  material        = signal('');
  careInstructions = signal('');
  // Custom (made-to-order) sizing
  customSizeEnabled  = signal(false);
  customSizeSurcharge = signal<number | null>(null);
  customSizeNote     = signal('');

  // State
  categories   = signal<CategoryResponse[]>([]);
  isLoading    = signal(false);
  isFetching   = signal(true);
  errorMessage = signal('');
  fieldErrors  = signal<Record<string, string>>({});

  readonly descMax = 2000;
  descLength = computed(() => this.description().length);

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.productId.set(id);

    this.categoryService.getCategories().subscribe({
      next: (cats) => this.categories.set(cats),
      error: (err) => this.errorMessage.set(parseApiError(err)),
    });

    this.productService.getProduct(id).subscribe({
      next: (p) => {
        this.productName.set(p.name);
        this.productCode.set(p.productCode ?? '');
        this.description.set(p.description);
        this.basePrice.set(p.price);
        this.categoryId.set(p.categoryId);
        this.isAvailable.set(p.isAvailable);
        this.isNewArrival.set(!!p.isNewArrival);
        this.isFeatured.set(!!p.isFeatured);
        this.discountExcluded.set(!!p.discountExcluded);
        this.stockDisplay.set(p.stockDisplay ?? 'CATEGORY');
        this.sizeChartId.set(p.sizeChartId ?? 0);
        this.regularFitSizeChartId.set(p.regularFitSizeChartId ?? 0);
        this.unit.set(p.unit ?? '');
        this.lowStockThreshold.set(p.lowStockThreshold ?? null);
        this.brand.set(p.brand ?? '');
        this.gender.set(p.gender ?? '');
        this.material.set(p.material ?? '');
        this.careInstructions.set(p.careInstructions ?? '');
        this.customSizeEnabled.set(!!p.customSizeEnabled);
        this.customSizeSurcharge.set(p.customSizeSurcharge ?? null);
        this.customSizeNote.set(p.customSizeNote ?? '');
        // Show the sale discount the product already has, so saving the form
        // keeps its sale price instead of quietly removing it.
        const discount = discountFromProduct(p.price, p.discountPrice);
        this.discountMode.set(discount.mode);
        this.discountValue.set(discount.value);
        this.isFetching.set(false);
      },
      error: (err) => {
        this.errorMessage.set(parseApiError(err));
        this.isFetching.set(false);
      },
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
      sizeChartId: this.sizeChartId(),
      regularFitSizeChartId: this.regularFitSizeChartId(),
      unit: this.unit().trim() || undefined,
      lowStockThreshold: this.lowStockThreshold() ?? undefined,
      brand: this.brand().trim() || undefined,
      gender: this.gender().trim() || undefined,
      material: this.material().trim() || undefined,
      careInstructions: this.careInstructions().trim() || undefined,
      customSizeEnabled: this.customSizeEnabled(),
      customSizeSurcharge: this.customSizeEnabled() ? (this.customSizeSurcharge() ?? 0) : null,
      customSizeNote: this.customSizeEnabled() ? (this.customSizeNote().trim() || null) : null,
    };

    this.isLoading.set(true);
    this.productService.updateProduct(this.productId(), dto).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.notices.success('Product saved', this.productName().trim());
        this.router.navigate(['/products']);
      },
      error: (err) => {
        this.isLoading.set(false);
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
