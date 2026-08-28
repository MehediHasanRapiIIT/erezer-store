import { Component, signal, OnInit, inject } from '@angular/core';
import { RouterLink, Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { CategoryService } from '../../../core/services/category.service';
import { UploadService } from '../../../core/services/upload.service';
import { parseApiError } from '../../../core/utils/api-error.util';
import { HttpErrorResponse } from '@angular/common/http';

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

  categoryId   = signal<number>(0);
  categoryName = signal('');
  isActive     = signal(true);
  imageUrl     = signal('');
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

    this.categoryService.getCategory(id).subscribe({
      next: (cat) => {
        this.categoryName.set(cat.name);
        this.isActive.set(cat.isActive);
        this.imageUrl.set(cat.imageUrl ?? '');
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

    this.isLoading.set(true);
    this.errorMessage.set('');
    this.fieldErrors.set({});

    this.categoryService.updateCategory(this.categoryId(), {
      name: this.categoryName().trim(),
      isActive: this.isActive(),
      imageUrl: this.imageUrl() || null,
    }).subscribe({
      next: () => {
        this.isLoading.set(false);
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
