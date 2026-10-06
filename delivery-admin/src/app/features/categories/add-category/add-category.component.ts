import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { KeyValuePipe } from '@angular/common';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { CategoryService } from '../../../core/services/category.service';
import { UploadService } from '../../../core/services/upload.service';
import { parseApiError } from '../../../core/utils/api-error.util';
import { HttpErrorResponse } from '@angular/common/http';
import { PermissionService } from '../../../core/services/permission.service';
import { NoticeService } from '../../../core/services/notice.service';
import { CategoryResponse } from '../../../core/models/api.models';

@Component({
  selector: 'app-add-category',
  standalone: true,
  imports: [RouterLink, FormsModule, SidebarComponent, KeyValuePipe],
  templateUrl: './add-category.component.html',
})
export class AddCategoryComponent implements OnInit {
  constructor(
    private router: Router,
    private categoryService: CategoryService,
    private uploadService: UploadService,
  ) {}

  protected readonly perms = inject(PermissionService);
  private readonly notices = inject(NoticeService);
  private readonly route = inject(ActivatedRoute);

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

  ngOnInit(): void {
    // Opened from "Add subcategory" on a category: start under that one.
    const from = Number(this.route.snapshot.queryParamMap.get('parentId'));
    if (from > 0) this.parentId.set(from);
    // "Add Subcategory" on the Categories page: a subcategory, its main category still to choose.
    this.isSub.set(from > 0 || this.route.snapshot.queryParamMap.get('kind') === 'sub');
    this.categoryService.getCategories().subscribe({
      next: (all) => this.mainCategories.set(all.filter((c) => c.parentId == null)),
      error: () => {},
    });
  }

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

  onSave() {
    if (!this.categoryName().trim()) return;
    if (this.isSub() && this.parentId() == null) {
      this.parentMissing.set(true);
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set('');
    this.fieldErrors.set({});

    this.categoryService.createCategory({
      name: this.categoryName(),
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
        this.notices.success(this.parentId() != null ? 'Subcategory added' : 'Category added',
          this.parentId() != null ? `${this.categoryName().trim()}, in ${this.parentName()}` : this.categoryName().trim());
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
