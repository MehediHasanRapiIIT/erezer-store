import { Component, signal, computed, OnInit, inject } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { ProductService } from '../../../core/services/product.service';
import { CategoryService } from '../../../core/services/category.service';
import { CategoryResponse, ProductRequest } from '../../../core/models/api.models';
import { parseApiError } from '../../../core/utils/api-error.util';

@Component({
  selector: 'app-add-product',
  standalone: true,
  imports: [RouterLink, FormsModule, SidebarComponent],
  templateUrl: './add-product.component.html',
})
export class AddProductComponent implements OnInit {
  private router = inject(Router);
  private productService = inject(ProductService);
  private categoryService = inject(CategoryService);

  // Form fields
  productName  = signal('');
  description  = signal('');
  basePrice    = signal<number | null>(null);
  discount     = signal<number>(0);
  categoryId   = signal<number | null>(null);
  shopId       = signal(1);
  isAvailable  = signal(true);
  isNewArrival = signal(false);
  isFeatured   = signal(false);

  // State
  categories   = signal<CategoryResponse[]>([]);
  isLoading    = signal(false);
  errorMessage = signal('');
  fieldErrors  = signal<Record<string, string>>({});

  readonly descMax = 500;
  descLength = computed(() => this.description().length);

  isDirty = computed(() =>
    !!this.productName() || !!this.description() || !!this.basePrice()
  );

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
    if (!this.basePrice() || this.basePrice()! <= 0) {
      this.fieldErrors.set({ price: 'A valid price is required.' });
      return;
    }
    if (!this.categoryId()) {
      this.fieldErrors.set({ categoryId: 'Please select a category.' });
      return;
    }

    const dto: ProductRequest = {
      name: this.productName().trim(),
      description: this.description().trim(),
      price: this.basePrice()!,
      discountPercentage: this.discount() || undefined,
      categoryId: this.categoryId()!,
      shopId: this.shopId(),
      isAvailable: this.isAvailable(),
      isNewArrival: this.isNewArrival(),
      isFeatured: this.isFeatured(),
    };

    this.isLoading.set(true);
    this.productService.createProduct(dto).subscribe({
      next: (created) => {
        this.isLoading.set(false);
        // Land on the editor so images (gallery) and variants can be added next.
        this.router.navigate(['/products', created.id, 'edit']);
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
