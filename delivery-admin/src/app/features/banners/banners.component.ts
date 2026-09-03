import { Component, signal, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { BannerService } from '../../core/services/banner.service';
import { BannerContent, BannerResponse, BannerSlot, CategoryResponse } from '../../core/models/api.models';
import { CategoryService } from '../../core/services/category.service';
import { parseApiError } from '../../core/utils/api-error.util';
import { catchError, of } from 'rxjs';

/**
 * Where a banner's button sends the shopper, chosen in plain language rather
 * than by typing a URL. Each one maps to a real storefront route.
 */
/** Plain-language guidance for one spot on the home page. */
interface SlotMeta {
  label: string;
  where: string;
  shows: string;
  imageHint: string;
  buttonHint: string;
  titlePlaceholder: string;
  detailsPlaceholder: string;
  /** CSS aspect-ratio for the preview box. */
  aspect: string;
  /** Only one banner is displayed in this spot. */
  single: boolean;
}

type LinkKind =
  | 'NONE'
  | 'SHOP'
  | 'CATEGORY'
  | 'COLLECTIONS'
  | 'CUSTOM_DESIGN'
  | 'FLASH_SALE'
  | 'BUNDLES'
  | 'HOME'
  | 'CUSTOM';

@Component({
  selector: 'app-banners',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  templateUrl: './banners.component.html',
})
export class BannersComponent implements OnInit {
  private bannerService = inject(BannerService);
  private categoryService = inject(CategoryService);

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
  /** Destination picker state. The stored ctaLink is derived from these. */
  linkKind = signal<LinkKind>('NONE');
  linkCategoryId = signal<number | null>(null);
  customLink = signal('');
  categories = signal<CategoryResponse[]>([]);
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
  readonly slotMeta: Record<BannerSlot, SlotMeta> = {
    HERO: {
      label: 'Big picture at the top',
      where: 'The first thing shoppers see, filling the screen. If you add several banners here they take turns as a slideshow.',
      shows: 'Shows the headline, the short line and a button, over the bottom-left of the photo.',
      imageHint: 'A wide photo, at least 1920 x 1080 px. Keep the bottom-left area fairly plain so the words stay readable.',
      buttonHint: 'The big white button on the picture. Without one, shoppers get a standard "Shop the collection" button.',
      titlePlaceholder: 'e.g. Eid Collection 2026',
      detailsPlaceholder: 'e.g. New arrivals for the festive season',
      aspect: '16 / 9',
      single: false,
    },
    SPLIT_LEFT: {
      label: 'Tall picture, left',
      where: 'Just under the top picture: two tall pictures side by side, usually one collection each.',
      shows: 'Shows the headline and short line in the middle of the picture, with an outlined button. The whole picture can be clicked.',
      imageHint: 'A tall (portrait) photo, at least 1000 x 1400 px.',
      buttonHint: 'Where the whole picture takes the shopper. The button words appear as an outlined pill.',
      titlePlaceholder: 'e.g. New arrivals',
      detailsPlaceholder: "e.g. This season's cuts",
      aspect: '3 / 4',
      single: true,
    },
    SPLIT_RIGHT: {
      label: 'Tall picture, right',
      where: 'Just under the top picture: two tall pictures side by side, usually one collection each.',
      shows: 'Shows the headline and short line in the middle of the picture, with an outlined button. The whole picture can be clicked.',
      imageHint: 'A tall (portrait) photo, at least 1000 x 1400 px.',
      buttonHint: 'Where the whole picture takes the shopper. The button words appear as an outlined pill.',
      titlePlaceholder: 'e.g. The Pink Edit',
      detailsPlaceholder: 'e.g. Erezer Pink collection',
      aspect: '3 / 4',
      single: true,
    },
    GRID_1: {
      label: 'Small tile 1 (top left)',
      where: 'Four smaller pictures in a two-by-two block, one collection each.',
      shows: 'Shows only one label in the middle: the button words, or the headline if there are none. The short line is not shown on tiles.',
      imageHint: 'A landscape photo, at least 1200 x 700 px.',
      buttonHint: 'Where the whole tile takes the shopper. The button words are the label on the tile.',
      titlePlaceholder: 'e.g. Bags',
      detailsPlaceholder: '',
      aspect: '16 / 9',
      single: true,
    },
    GRID_2: {
      label: 'Small tile 2 (top right)',
      where: 'Four smaller pictures in a two-by-two block, one collection each.',
      shows: 'Shows only one label in the middle: the button words, or the headline if there are none. The short line is not shown on tiles.',
      imageHint: 'A landscape photo, at least 1200 x 700 px.',
      buttonHint: 'Where the whole tile takes the shopper. The button words are the label on the tile.',
      titlePlaceholder: 'e.g. Caps',
      detailsPlaceholder: '',
      aspect: '16 / 9',
      single: true,
    },
    GRID_3: {
      label: 'Small tile 3 (bottom left)',
      where: 'Four smaller pictures in a two-by-two block, one collection each.',
      shows: 'Shows only one label in the middle: the button words, or the headline if there are none. The short line is not shown on tiles.',
      imageHint: 'A landscape photo, at least 1200 x 700 px.',
      buttonHint: 'Where the whole tile takes the shopper. The button words are the label on the tile.',
      titlePlaceholder: 'e.g. Accessories',
      detailsPlaceholder: '',
      aspect: '16 / 9',
      single: true,
    },
    GRID_4: {
      label: 'Small tile 4 (bottom right)',
      where: 'Four smaller pictures in a two-by-two block, one collection each.',
      shows: 'Shows only one label in the middle: the button words, or the headline if there are none. The short line is not shown on tiles.',
      imageHint: 'A landscape photo, at least 1200 x 700 px.',
      buttonHint: 'Where the whole tile takes the shopper. The button words are the label on the tile.',
      titlePlaceholder: 'e.g. Jackets',
      detailsPlaceholder: '',
      aspect: '16 / 9',
      single: true,
    },
    CUSTOM_PROMO: {
      label: 'Design-your-own photo',
      where: 'Near the bottom of the page: a grey text panel that invites shoppers to design their own garment, with your photo beside it.',
      shows: 'The headline, short line and button appear in the grey panel, not on the photo. Leave them empty to keep the standard wording.',
      imageHint: 'A roughly square photo, at least 1200 x 1200 px, ideally of a plain garment or the studio.',
      buttonHint: 'Normally this goes to the design studio. Change it only if you have a reason.',
      titlePlaceholder: 'Customize your apparel, your way.',
      detailsPlaceholder: 'Design your own t-shirts, hoodies and more in our online studio, no minimum order, even a single piece.',
      aspect: '2 / 1',
      single: true,
    },
  };

  meta(slot: string): SlotMeta {
    return this.slotMeta[(slot in this.slotMeta ? slot : 'HERO') as BannerSlot];
  }

  /** Human label for a slot, for the badge on each banner card. */
  slotLabel(slot: BannerSlot | null | undefined): string {
    return this.meta(slot ?? 'HERO').label;
  }

  selectSlot(slot: string): void {
    this.slot.set(slot as BannerSlot);
    // The design-your-own photo almost always leads to the studio; start there
    // so a new admin does not have to know that.
    if (slot === 'CUSTOM_PROMO' && this.linkKind() === 'NONE') {
      this.linkKind.set('CUSTOM_DESIGN');
      if (!this.ctaLabel().trim()) this.ctaLabel.set('TRY IT NOW');
    }
  }

  /** Tailwind classes for a spot on the home-page map. */
  slotClass(slot: string): string {
    if (this.slot() === slot) return 'border-blue-600 bg-blue-600 text-white shadow';
    if (this.bannerIn(slot)) return 'border-gray-300 bg-white text-gray-800 hover:border-blue-400';
    return 'border-dashed border-gray-300 bg-white/60 text-gray-500 hover:border-blue-400';
  }

  /** The banner currently saved in a spot, ignoring the one being edited. */
  private bannerIn(slot: string): BannerResponse | undefined {
    return this.banners().find((b) => (b.slot ?? 'HERO') === slot && b.id !== this.editingId());
  }

  /** "In use: NEW ARRIVALS", "3 slides" or "Empty", for the map. */
  occupancy(slot: string): string {
    const others = this.banners().filter((b) => (b.slot ?? 'HERO') === slot && b.id !== this.editingId());
    const editing = this.banners().find((b) => b.id === this.editingId());
    const editingHere = !!editing && (editing.slot ?? 'HERO') === slot;
    if (slot === 'HERO') {
      const n = others.length + (editingHere ? 1 : 0);
      return n === 0 ? 'Empty' : n + (n === 1 ? ' slide' : ' slides');
    }
    if (others[0]) return 'In use: ' + (others[0].promotionTitle || 'untitled');
    return editingHere ? 'This banner' : 'Empty';
  }

  /** Another banner already in a one-banner spot, so the admin can be warned. */
  occupant(): BannerResponse | null {
    if (!this.meta(this.slot()).single) return null;
    return this.bannerIn(this.slot()) ?? null;
  }

  usesDetails(): boolean {
    return !this.slot().startsWith('GRID_');
  }

  wordsHint(): string {
    if (this.slot().startsWith('GRID_')) return 'Tiles show one short label only. Two or three words work best.';
    if (this.slot() === 'CUSTOM_PROMO') return 'Optional. Leave empty to keep the standard wording shown in the preview.';
    return 'A short headline reads best. Both are optional, but a picture with no words says nothing.';
  }

  previewImage(): string {
    return this.imagePreview() || this.existingImageUrl();
  }

  /** What clicking the banner does, for the preview notes. */
  clickText(): string {
    const link = this.resolvedCtaLink();
    if (this.slot() === 'HERO') {
      return link && this.ctaLabel().trim()
        ? 'the button opens ' + this.describeLink(link)
        : 'the standard button opens the shop';
    }
    if (this.slot() === 'CUSTOM_PROMO') {
      return 'the button opens ' + (link ? this.describeLink(link) : 'the design studio');
    }
    return 'the whole picture opens ' + (link ? this.describeLink(link) : 'the shop');
  }

  scheduleWarning(): boolean {
    const from = this.fromDate(), to = this.toDate();
    const today = new Date().toISOString().slice(0, 10);
    return (!!to && to < today) || (!!from && !!to && from > to);
  }

  scheduleText(): string {
    const from = this.fromDate(), to = this.toDate();
    if (from && to && from > to) return 'The end date is before the start date, so the banner would never show.';
    const today = new Date().toISOString().slice(0, 10);
    if (to && to < today) return 'The end date (' + this.formatDate(to) + ') has already passed, so the banner will not show.';
    if (from && to) return 'Shows from ' + this.formatDate(from) + ' until ' + this.formatDate(to) + '.';
    if (from) return 'Shows from ' + this.formatDate(from) + ' onwards.';
    if (to) return 'Shows until ' + this.formatDate(to) + '.';
    return 'Shows all the time.';
  }

  /**
   * Slots with no banner yet. Surfaced in the UI because a band with nothing in
   * it silently hides on the storefront - without this you cannot tell an empty
   * band from a broken one.
   */
  get emptySlots(): string[] {
    const filled = new Set(this.banners().map((b) => b.slot ?? 'HERO'));
    return (Object.keys(this.slotMeta) as BannerSlot[])
      .filter((slot) => !filled.has(slot))
      .map((slot) => this.slotMeta[slot].label);
  }

  /**
   * Button destinations, worded for a shop owner. The shopper-facing routes are
   * an implementation detail, so they are resolved in {@link resolvedCtaLink}
   * rather than typed by hand.
   */
  readonly linkOptions: { value: LinkKind; label: string }[] = [
    { value: 'NONE',          label: 'No button' },
    { value: 'SHOP',          label: 'Shop — all products' },
    { value: 'CATEGORY',      label: 'One collection…' },
    { value: 'COLLECTIONS',   label: 'All collections' },
    { value: 'CUSTOM_DESIGN', label: 'Design your own' },
    { value: 'FLASH_SALE',    label: 'Flash sale' },
    { value: 'BUNDLES',       label: 'Bundle offers' },
    { value: 'HOME',          label: 'Home page' },
    { value: 'CUSTOM',        label: 'Somewhere else (type a link)' },
  ];

  ngOnInit(): void {
    this.loadBanners();
    // Needed to offer collections by name, and to recognise a saved collection
    // link when an existing banner is opened for editing.
    this.categoryService.getCategories()
      .pipe(catchError(() => of([] as CategoryResponse[])))
      .subscribe((list) => {
        this.categories.set(list.filter((c) => c.isActive !== false));
        // The form may already be open on a link that could not be matched
        // before the names arrived.
        if (this.showForm() && this.linkKind() === 'CUSTOM') {
          this.applyLink(this.customLink());
        }
      });
  }

  // ── button destination ────────────────────────────────────────────────────

  /** The path actually stored on the banner, built from the picker. */
  resolvedCtaLink(): string {
    switch (this.linkKind()) {
      case 'NONE': return '';
      case 'SHOP': return '/shop';
      case 'COLLECTIONS': return '/categories';
      case 'CUSTOM_DESIGN': return '/custom-design';
      case 'FLASH_SALE': return '/flash-sale';
      case 'BUNDLES': return '/bundles';
      case 'HOME': return '/';
      case 'CUSTOM': return this.customLink().trim();
      case 'CATEGORY': {
        const category = this.categories().find((c) => c.id === this.linkCategoryId());
        if (!category) return '';
        // Prefer the collection's own page; fall back to a filtered shop for a
        // category that has no slug yet.
        return category.slug ? `/${category.slug}` : `/shop?category=${category.id}`;
      }
    }
  }

  /**
   * Plain-English description of where the button goes, shown under the picker.
   * Shares its vocabulary with {@link describeLink} so the form and the banner
   * list describe the same destination the same way.
   */
  linkPreview(): string {
    const kind = this.linkKind();
    if (kind === 'NONE') return 'No button will be shown on this banner.';
    if (kind === 'CATEGORY' && this.linkCategoryId() === null) {
      return 'Choose which collection the button opens.';
    }
    if (kind === 'CUSTOM' && !this.customLink().trim()) {
      return 'Type where the button should go, starting with a slash.';
    }
    return `Opens ${this.describeLink(this.resolvedCtaLink())}.`;
  }

  /** True once the button is fully specified, so the preview can show it. */
  hasButton(): boolean {
    return this.linkKind() !== 'NONE' && !!this.resolvedCtaLink() && !!this.ctaLabel().trim();
  }

  /** Describe a saved link in words, for the banner list. */
  describeLink(link: string | null | undefined): string {
    const raw = (link ?? '').trim();
    if (!raw) return 'nowhere';
    const known: Record<string, string> = {
      '/': 'the home page',
      '/shop': 'the shop',
      '/categories': 'all collections',
      '/custom-design': 'design your own',
      '/flash-sale': 'the flash sale',
      '/bundles': 'bundle offers',
    };
    if (known[raw]) return known[raw];

    const filtered = raw.match(/^\/shop\?category=(\d+)$/);
    const id = filtered ? Number(filtered[1]) : null;
    const category = id !== null
      ? this.categories().find((c) => c.id === id)
      : this.categories().find((c) => c.slug && `/${c.slug}` === raw);
    if (category) return `the ${category.name} collection`;

    return raw;
  }

  onLinkKindChange(kind: LinkKind): void {
    this.linkKind.set(kind);
    if (kind !== 'CATEGORY') this.linkCategoryId.set(null);
    if (kind !== 'CUSTOM') this.customLink.set('');
    // A button needs words on it; offer a sensible default rather than saving
    // a link that silently never renders.
    if (kind !== 'NONE' && !this.ctaLabel().trim()) this.ctaLabel.set('SHOP NOW');
    if (kind === 'CATEGORY' && this.linkCategoryId() === null) {
      this.linkCategoryId.set(this.categories()[0]?.id ?? null);
    }
  }

  /** Turn a stored path back into a picker selection, for the edit form. */
  private applyLink(raw: string | null | undefined): void {
    const link = (raw ?? '').trim();
    if (!link) { this.linkKind.set('NONE'); return; }

    const known: Record<string, LinkKind> = {
      '/': 'HOME',
      '/shop': 'SHOP',
      '/categories': 'COLLECTIONS',
      '/custom-design': 'CUSTOM_DESIGN',
      '/flash-sale': 'FLASH_SALE',
      '/bundles': 'BUNDLES',
    };
    const exact = known[link];
    if (exact) { this.linkKind.set(exact); return; }

    const filtered = link.match(/^\/shop\?category=(\d+)$/);
    if (filtered) {
      this.linkKind.set('CATEGORY');
      this.linkCategoryId.set(Number(filtered[1]));
      return;
    }

    const bySlug = this.categories().find((c) => c.slug && `/${c.slug}` === link);
    if (bySlug) {
      this.linkKind.set('CATEGORY');
      this.linkCategoryId.set(bySlug.id);
      return;
    }

    this.linkKind.set('CUSTOM');
    this.customLink.set(link);
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
    this.applyLink(banner.ctaLink);
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
      ctaLink: this.resolvedCtaLink() || undefined,
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
    this.linkKind.set('NONE');
    this.linkCategoryId.set(null);
    this.customLink.set('');
    this.sortOrder.set(0);
    this.formError.set('');
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  }
}
