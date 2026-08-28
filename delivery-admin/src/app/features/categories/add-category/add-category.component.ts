import { Component, signal } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { KeyValuePipe } from '@angular/common';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { CategoryService } from '../../../core/services/category.service';
import { UploadService } from '../../../core/services/upload.service';
import { parseApiError } from '../../../core/utils/api-error.util';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-add-category',
  standalone: true,
  imports: [RouterLink, FormsModule, SidebarComponent, KeyValuePipe],
  templateUrl: './add-category.component.html',
})
export class AddCategoryComponent {
  constructor(
    private router: Router,
    private categoryService: CategoryService,
    private uploadService: UploadService,
  ) {}

  categoryName = signal('');
  isActive     = signal(true);
  imageUrl     = signal('');
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

    this.isLoading.set(true);
    this.errorMessage.set('');
    this.fieldErrors.set({});

    this.categoryService.createCategory({
      name: this.categoryName(),
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
