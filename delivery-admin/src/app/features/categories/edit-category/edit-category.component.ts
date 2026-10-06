import { Component, signal, OnInit, inject, computed } from '@angular/core';
import { RouterLink, Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { CategoryService } from '../../../core/services/category.service';
import { UploadService } from '../../../core/services/upload.service';
import { parseApiError } from '../../../core/utils/api-error.util';
import { HttpErrorResponse } from '@angular/common/http';
import { PermissionService } from '../../../core/services/permission.service';
import { CategoryResponse } from '../../../core/models/api.models';
import { NoticeService } from '../../../core/services/notice.service';

@Component({
  selector: 'app-edit-category',
  standalone: true,
  imports: [RouterLink, FormsModule, SidebarComponent],
  templateUrl: './edit-category.component.html',
})
export class EditCategoryComponent implements OnInit {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private categoryService = inject(CategoryService);
  private uploadService = inject(UploadService);
  protected readonly perms = inject(PermissionService);
  private readonly notices = inject(NoticeService);

  /** The main category this one sits under; null for a main category. */
  parentId = signal<number | null>(null);
  /** Main categories it could sit under. */
  mainCategories = signal<CategoryResponse[]>([]);
  /** A category with subcategories of its own can't become one. */
  hasSubcategories = signal(false);
  /** "Subcategory" is chosen, whether or not its main category has been picked yet. */
  isSub = signal(false);
  /** Save was pressed on a subcategory with no main category chosen. */
  parentMissing = signal(false);

  setKind(sub: boolean): void {
    this.isSub.set(sub);
    this.parentMissing.set(false);
    if (!sub) this.parentId.set(null);
  }
  parentName = computed(() => this.mainCategories().find((c) => c.id === this.parentId())?.name ?? 'its main category');

  categoryId   = signal<number>(0);
  categoryName = signal('');
  isActive     = signal(true);
  imageUrl     = signal('');
  showOnHome   = signal(false);
  /** Keep every product in this category at full price. */
  discountExcluded = signal(false);
  showStockQuantity = signal(false);
  homeSortOrder = signal(0);
  uploading    = signal(false);
  isLoading    = signal(false);
  isFetching   = signal(true);
  errorMessage = signal('');
  fieldErrors  = signal<Record<string, string>>({});

  onImageSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploading.set(true);
    this.errorMessage.set('');
    this.uploadService.uploadImage(file).subscribe({
      next: (url) => {
        this.imageUrl.set(url);
        this.uploading.set(false);
        input.value = '';
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(parseApiError(err));
        this.uploading.set(false);
      },
    });
  }

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.categoryId.set(id);

    this.categoryService.getCategories().subscribe({
      next: (all) => this.mainCategories.set(all.filter((c) => c.parentId == null && c.id !== id)),
      error: () => {},
    });

    this.categoryService.getCategory(id).subscribe({
      next: (cat) => {
        this.categoryName.set(cat.name);
        this.isActive.set(cat.isActive);
        this.imageUrl.set(cat.imageUrl ?? '');
        this.showOnHome.set(cat.showOnHome ?? false);
        this.discountExcluded.set(cat.discountExcluded ?? false);
        this.showStockQuantity.set(cat.showStockQuantity ?? false);
        this.homeSortOrder.set(cat.homeSortOrder ?? 0);
        this.parentId.set(cat.parentId ?? null);
        this.isSub.set(cat.parentId != null);
        this.hasSubcategories.set((cat.subcategoryCount ?? 0) > 0);
        this.isFetching.set(false);
      },
      error: (err) => {
        this.errorMessage.set(parseApiError(err));
        this.isFetching.set(false);
      },
    });
  }

  onSave() {
    if (!this.categoryName().trim()) return;
    if (this.isSub() && this.parentId() == null) {
      this.parentMissing.set(true);
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set('');
    this.fieldErrors.set({});

    this.categoryService.updateCategory(this.categoryId(), {
      name: this.categoryName().trim(),
      isActive: this.isActive(),
      imageUrl: this.imageUrl() || null,
      showOnHome: this.showOnHome(),
      discountExcluded: this.discountExcluded(),
      showStockQuantity: this.showStockQuantity(),
      homeSortOrder: this.homeSortOrder(),
      parentId: this.parentId(),
    }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.notices.success('Category saved', this.categoryName().trim());
        this.router.navigate(['/categories']);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        const body = err.error;
        if (body?.errors && typeof body.errors === 'object') {
          this.fieldErrors.set(body.errors as Record<string, string>);
        } else {
          this.errorMessage.set(parseApiError(err));
        }
      },
    });
  }

  onCancel() {
    this.router.navigate(['/categories']);
  }
}
