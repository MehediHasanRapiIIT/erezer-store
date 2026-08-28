import { Component, signal, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { BannerService } from '../../core/services/banner.service';
import { BannerContent, BannerResponse, BannerSlot } from '../../core/models/api.models';
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
  slot = signal<BannerSlot>('HERO');
  ctaLabel = signal('');
  ctaLink = signal('');
  sortOrder = signal(0);
  isSaving = signal(false);
  formError = signal('');

  // Delete confirm
  deleteConfirmId = signal<string | null>(null);
  isDeleting = signal(false);

  get isEditMode(): boolean { return this.editingId() !== null; }

  /**
   * The landing-page bands a banner can fill, with guidance on what each one
   * expects. Labels are written for a shop owner rather than mirroring the enum.
   */
  readonly slotOptions: { value: BannerSlot; label: string; hint: string }[] = [
    { value: 'HERO', label: 'Hero (top of page)', hint: 'Full-screen opening image. Several rotate as a carousel.' },
    { value: 'SPLIT_LEFT', label: 'Split band — left', hint: 'Left half of the two-up category band.' },
    { value: 'SPLIT_RIGHT', label: 'Split band — right', hint: 'Right half of the two-up category band.' },
    { value: 'GRID_1', label: 'Grid tile 1', hint: 'Top-left tile of the 2×2 grid.' },
    { value: 'GRID_2', label: 'Grid tile 2', hint: 'Top-right tile of the 2×2 grid.' },
    { value: 'GRID_3', label: 'Grid tile 3', hint: 'Bottom-left tile of the 2×2 grid.' },
    { value: 'GRID_4', label: 'Grid tile 4', hint: 'Bottom-right tile of the 2×2 grid.' },
    { value: 'CUSTOM_PROMO', label: 'Custom-design promo', hint: 'Photo beside the “design your own” panel.' },
  ];

  /** Human label for a slot, for the badge on each banner card. */
  slotLabel(slot: BannerSlot | null | undefined): string {
    return this.slotOptions.find((o) => o.value === (slot ?? 'HERO'))?.label ?? 'Hero (top of page)';
  }

  /** Hint shown under the slot picker for the currently-selected band. */
  get selectedSlotHint(): string {
    return this.slotOptions.find((o) => o.value === this.slot())?.hint ?? '';
  }

  /**
   * Slots with no banner yet. Surfaced in the UI because a band with nothing in
   * it silently hides on the storefront - without this you cannot tell an empty
   * band from a broken one.
   */
  get emptySlots(): string[] {
    const filled = new Set(this.banners().map((b) => b.slot ?? 'HERO'));
    return this.slotOptions.filter((o) => !filled.has(o.value)).map((o) => o.label);
  }

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
    this.slot.set(banner.slot ?? 'HERO');
    this.ctaLabel.set(banner.ctaLabel ?? '');
    this.ctaLink.set(banner.ctaLink ?? '');
    this.sortOrder.set(banner.sortOrder ?? 0);
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

    const content: BannerContent = {
      promotionTitle: this.promotionTitle() || undefined,
      promotionDetails: this.promotionDetails() || undefined,
      fromDate: this.fromDate() || undefined,
      toDate: this.toDate() || undefined,
      slot: this.slot(),
      ctaLabel: this.ctaLabel() || undefined,
      ctaLink: this.ctaLink() || undefined,
      sortOrder: this.sortOrder() ?? 0,
    };

    const req$ = editId
      ? this.bannerService.updateBanner(editId, this.imageFile() ?? undefined, content)
      : this.bannerService.uploadBanner(this.imageFile()!, content);

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
    this.slot.set('HERO');
    this.ctaLabel.set('');
    this.ctaLink.set('');
    this.sortOrder.set(0);
    this.formError.set('');
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  }
}
