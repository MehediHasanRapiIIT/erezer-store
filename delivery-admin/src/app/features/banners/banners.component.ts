import { Component, signal, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { BannerService } from '../../core/services/banner.service';
import { BannerResponse } from '../../core/models/api.models';
import { parseApiError } from '../../core/utils/api-error.util';

@Component({
  selector: 'app-banners',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  templateUrl: './banners.component.html',
})
export class BannersComponent implements OnInit {
  private bannerService = inject(BannerService);

  banners = signal<BannerResponse[]>([]);
  isLoading = signal(true);
  errorMessage = signal('');

  // Form state — shared between create and edit
  showForm = signal(false);
  editingId = signal<string | null>(null); // null = create mode
  dragOver = signal(false);
  imageFile = signal<File | null>(null);
  imagePreview = signal('');
  existingImageUrl = signal('');
  promotionTitle = signal('');
  promotionDetails = signal('');
  fromDate = signal('');
  toDate = signal('');
  isSaving = signal(false);
  formError = signal('');

  // Delete confirm
  deleteConfirmId = signal<string | null>(null);
  isDeleting = signal(false);

  get isEditMode(): boolean { return this.editingId() !== null; }

  ngOnInit(): void {
    this.loadBanners();
  }

  private loadBanners(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.bannerService.getBanners().subscribe({
      next: (data) => { this.banners.set(data); this.isLoading.set(false); },
      error: (err) => { this.errorMessage.set(parseApiError(err)); this.isLoading.set(false); },
    });
  }

  openCreateForm(): void {
    this.resetForm();
    this.editingId.set(null);
    this.showForm.set(true);
  }

  openEditForm(banner: BannerResponse): void {
    this.resetForm();
    this.editingId.set(banner.id);
    this.promotionTitle.set(banner.promotionTitle ?? '');
    this.promotionDetails.set(banner.promotionDetails ?? '');
    this.fromDate.set(banner.fromDate ?? '');
    this.toDate.set(banner.toDate ?? '');
    this.existingImageUrl.set(banner.imageUrl ?? '');
    this.showForm.set(true);
  }

  closeForm(): void {
    this.showForm.set(false);
    this.resetForm();
  }

  onDragOver(e: DragEvent) { e.preventDefault(); this.dragOver.set(true); }
  onDragLeave() { this.dragOver.set(false); }
  onDrop(e: DragEvent) {
    e.preventDefault();
    this.dragOver.set(false);
    const files = e.dataTransfer?.files;
    if (files?.length) this.handleFile(files[0]);
  }
  onFileSelect(e: Event) {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (file) this.handleFile(file);
  }

  private handleFile(file: File): void {
    this.imageFile.set(file);
    const reader = new FileReader();
    reader.onload = (ev) => this.imagePreview.set(ev.target!.result as string);
    reader.readAsDataURL(file);
  }

  removeImage(): void {
    this.imageFile.set(null);
    this.imagePreview.set('');
  }

  onSave(): void {
    const editId = this.editingId();

    if (!editId && !this.imageFile()) {
      this.formError.set('Please select an image.');
      return;
    }

    this.isSaving.set(true);
    this.formError.set('');

    const title = this.promotionTitle() || undefined;
    const details = this.promotionDetails() || undefined;
    const from = this.fromDate() || undefined;
    const to = this.toDate() || undefined;

    const req$ = editId
      ? this.bannerService.updateBanner(editId, this.imageFile() ?? undefined, title, details, from, to)
      : this.bannerService.uploadBanner(this.imageFile()!, title, details, from, to);

    req$.subscribe({
      next: (banner) => {
        if (editId) {
          this.banners.update((list) => list.map((b) => b.id === editId ? banner : b));
        } else {
          this.banners.update((list) => [banner, ...list]);
        }
        this.isSaving.set(false);
        this.closeForm();
      },
      error: (err) => {
        this.formError.set(parseApiError(err));
        this.isSaving.set(false);
      },
    });
  }

  confirmDelete(id: string): void { this.deleteConfirmId.set(id); }
  cancelDelete(): void { this.deleteConfirmId.set(null); }

  deleteBanner(id: string): void {
    this.isDeleting.set(true);
    this.bannerService.deleteBanner(id).subscribe({
      next: () => {
        this.banners.update((list) => list.filter((b) => b.id !== id));
        this.deleteConfirmId.set(null);
        this.isDeleting.set(false);
      },
      error: (err) => {
        this.errorMessage.set(parseApiError(err));
        this.deleteConfirmId.set(null);
        this.isDeleting.set(false);
      },
    });
  }

  private resetForm(): void {
    this.imageFile.set(null);
    this.imagePreview.set('');
    this.existingImageUrl.set('');
    this.promotionTitle.set('');
    this.promotionDetails.set('');
    this.fromDate.set('');
    this.toDate.set('');
    this.formError.set('');
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  }
}
