import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import {
  AboutPage,
  BrandStory,
  Footer,
  Highlight,
  Marquee,
  SizeChart,
  StoreSettings,
  StoreSettingsService,
} from '../../core/services/store-settings.service';
import { UploadService } from '../../core/services/upload.service';
import { PermissionService } from '../../core/services/permission.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

/** Preset icons selectable for "Our promise" footer items. */
const PROMISE_ICONS = [
  { value: 'quality', label: 'Quality / Shield' },
  { value: 'support', label: 'Support / Chat' },
  { value: 'delivery', label: 'Delivery / Truck' },
  { value: 'globe', label: 'Globe / International' },
  { value: 'star', label: 'Star' },
] as const;

/** Preset icons selectable for home-page highlight stats. */
const HIGHLIGHT_ICONS = [
  { value: 'star', label: 'Star / Rating' },
  { value: 'truck', label: 'Truck / Delivery' },
  { value: 'refresh', label: 'Refresh / Exchange' },
  { value: 'shield', label: 'Shield / Secure' },
] as const;

const EMPTY_CHART: SizeChart = { columns: ['Chest', 'Length'], rows: [] };

const EMPTY_BRAND: BrandStory = {
  eyebrow: 'Our story', heading: '', body: '', ctaLabel: 'Explore the collection',
  ctaLink: '/shop', socialHandle: '', socialUrl: '', images: [],
};

const EMPTY_ABOUT: AboutPage = {
  title: '', intro: '', heroImageUrl: '', sections: [], ctaLabel: '', ctaLink: '',
};

const EMPTY_FOOTER: Footer = {
  brandName: 'EREZER', blurb: '', columns: [], promises: [], outlets: [], copyright: '', tagline: '',
};

const EMPTY_MARQUEE: Marquee = { enabled: true, items: [] };

@Component({
  selector: 'app-store-settings',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <h1 class="text-lg font-bold text-gray-900">Store settings</h1>
          @if (perms.canAny('settings.store', 'settings.homepage', 'settings.footer', 'settings.sizechart', 'settings.payments', 'settings.about')) {
            <button (click)="save()" [disabled]="saving() || loading()"
              class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
              {{ saving() ? 'Saving…' : 'Save changes' }}
            </button>
          }
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-4xl mx-auto space-y-6">

            @if (errorMessage()) {
              <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ errorMessage() }}</p>
            }
            @if (savedMessage()) {
              <p class="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{{ savedMessage() }}</p>
            }
            @if (loading()) {
              <p class="text-sm text-gray-400">Loading…</p>
            }

            <!-- Return / exchange policy -->
            <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-3">
              <h2 class="text-base font-semibold">Return &amp; exchange policy</h2>
              <p class="text-xs text-gray-500">Shown on every product page.</p>
              @if (!perms.can('settings.store')) {
                <p class="text-xs text-gray-400">Needs the “Edit policies and support contacts” permission.</p>
              }
              <fieldset [disabled]="!perms.can('settings.store')" class="min-w-0 space-y-3 disabled:opacity-60">
              <textarea [(ngModel)]="model.returnPolicyText" rows="4"
                placeholder="Tell us within 3 days of delivery…"
                class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"></textarea>
              <label class="block text-xs font-medium text-gray-600">
                Notify-us window (days)
                <input type="number" min="0" [(ngModel)]="model.exchangeWindowDays"
                  class="mt-1 w-32 rounded-lg border border-gray-200 px-3 py-2 text-sm" />
              </label>
              </fieldset>
            </section>

            <!-- Support contact -->
            <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-3">
              <h2 class="text-base font-semibold">Customer support</h2>
              @if (!perms.can('settings.store')) {
                <p class="text-xs text-gray-400">Needs the “Edit policies and support contacts” permission.</p>
              }
              <fieldset [disabled]="!perms.can('settings.store')" class="min-w-0 disabled:opacity-60">
              <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <label class="text-xs font-medium text-gray-600">
                  Phone
                  <input [(ngModel)]="model.supportPhone" placeholder="+880 1700-000000"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
                <label class="text-xs font-medium text-gray-600">
                  Email
                  <input [(ngModel)]="model.supportEmail" placeholder="support@erezer.com"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
                <label class="text-xs font-medium text-gray-600">
                  Hours
                  <input [(ngModel)]="model.supportHours" placeholder="Sat–Thu, 10:00–19:00"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
              </div>
              </fieldset>
            </section>

            <!-- Payment methods -->
            <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-3">
              <h2 class="text-base font-semibold">Payment methods</h2>
              <p class="text-xs text-gray-500">Choose which options customers can pick at checkout. At least one must stay on.</p>
              @if (!perms.can('settings.payments')) {
                <p class="text-xs text-gray-400">Needs the “Turn payment methods on or off” permission.</p>
              }
              <fieldset [disabled]="!perms.can('settings.payments')" class="min-w-0 disabled:opacity-60">
              <div class="space-y-2">
                <label class="flex items-center gap-3 rounded-lg border border-gray-200 px-3 py-2.5 text-sm">
                  <input type="checkbox" [(ngModel)]="model.paymentCodEnabled" class="h-4 w-4" />
                  <span class="font-medium text-gray-800">Cash on Delivery</span>
                </label>
                <label class="flex items-center gap-3 rounded-lg border border-gray-200 px-3 py-2.5 text-sm">
                  <input type="checkbox" [(ngModel)]="model.paymentBkashEnabled" class="h-4 w-4" />
                  <span class="font-medium text-gray-800">bKash</span>
                </label>
                <label class="flex items-center gap-3 rounded-lg border border-gray-200 px-3 py-2.5 text-sm">
                  <input type="checkbox" [(ngModel)]="model.paymentCardEnabled" class="h-4 w-4" />
                  <span class="font-medium text-gray-800">Card</span>
                </label>
              </div>
              </fieldset>
            </section>

            <!-- Size chart -->
            <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-3">
              <div class="flex items-center justify-between">
                <div>
                  <h2 class="text-base font-semibold">Size chart</h2>
                  <p class="text-xs text-gray-500">Each measurement holds both cm and inch.</p>
                  @if (!perms.can('settings.sizechart')) {
                    <p class="text-xs text-gray-400">Needs the “Edit the size chart” permission.</p>
                  }
                </div>
                @if (perms.can('settings.sizechart')) {
                <div class="flex gap-2">
                  <button type="button" (click)="addColumn()"
                    class="px-2.5 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50">+ Column</button>
                  <button type="button" (click)="addRow()"
                    class="px-2.5 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50">+ Size row</button>
                </div>
                }
              </div>

              <fieldset [disabled]="!perms.can('settings.sizechart')" class="min-w-0 disabled:opacity-60">
              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-gray-100 bg-gray-50 text-xs text-gray-500">
                      <th class="px-2 py-2 text-left">Size</th>
                      @for (col of chart.columns; track $index) {
                        <th class="px-2 py-2 text-left">
                          <div class="flex items-center gap-1">
                            <input [(ngModel)]="chart.columns[$index]"
                              class="w-28 rounded border border-gray-200 px-2 py-1 text-xs" />
                            @if (perms.can('settings.sizechart')) {
                            <button type="button" (click)="removeColumn($index)"
                              class="act-btn-icon" title="Remove column">
                              <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/></svg>
                            </button>
                            }
                          </div>
                        </th>
                      }
                      <th class="px-2 py-2"></th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-50">
                    @for (row of chart.rows; track $index; let ri = $index) {
                      <tr>
                        <td class="px-2 py-2">
                          <input [(ngModel)]="row.size" placeholder="M"
                            class="w-16 rounded border border-gray-200 px-2 py-1 text-xs font-semibold" />
                        </td>
                        @for (col of chart.columns; track $index; let ci = $index) {
                          <td class="px-2 py-2">
                            <div class="flex items-center gap-1">
                              <input type="number" step="0.1" [(ngModel)]="row.cells[ci].cm"
                                placeholder="cm" class="w-16 rounded border border-gray-200 px-2 py-1 text-xs" />
                              <span class="text-[10px] text-gray-400">cm</span>
                              <input type="number" step="0.1" [(ngModel)]="row.cells[ci].inch"
                                placeholder="in" class="w-16 rounded border border-gray-200 px-2 py-1 text-xs" />
                              <span class="text-[10px] text-gray-400">in</span>
                            </div>
                          </td>
                        }
                        <td class="px-2 py-2 text-right">
                          @if (perms.can('settings.sizechart')) {
                          <button type="button" (click)="removeRow(ri)" class="act-btn act-btn-delete" title="Remove row">
                            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                            Remove
                          </button>
                          }
                        </td>
                      </tr>
                    } @empty {
                      <tr><td [attr.colspan]="chart.columns.length + 2"
                        class="px-2 py-4 text-center text-gray-400 text-xs">No size rows yet.</td></tr>
                    }
                  </tbody>
                </table>
              </div>
              </fieldset>
            </section>

            <!-- Brand story (landing "Our story" band) -->
            <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-3">
              <div>
                <h2 class="text-base font-semibold">Brand story</h2>
                <p class="text-xs text-gray-500">The "Our story" band on the home page.</p>
                @if (!perms.can('settings.homepage')) {
                  <p class="text-xs text-gray-400">Needs the “Edit home page content” permission.</p>
                }
              </div>
              <fieldset [disabled]="!perms.can('settings.homepage')" class="min-w-0 space-y-3 disabled:opacity-60">
              <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <label class="text-xs font-medium text-gray-600">
                  Eyebrow
                  <input [(ngModel)]="brand.eyebrow" placeholder="Our story"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
                <label class="text-xs font-medium text-gray-600">
                  Heading
                  <input [(ngModel)]="brand.heading" placeholder="Considered clothing, made to last."
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
                <label class="text-xs font-medium text-gray-600 sm:col-span-2">
                  Body
                  <textarea [(ngModel)]="brand.body" rows="3"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"></textarea>
                </label>
                <label class="text-xs font-medium text-gray-600">
                  CTA label
                  <input [(ngModel)]="brand.ctaLabel" placeholder="Explore the collection"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
                <label class="text-xs font-medium text-gray-600">
                  CTA link
                  <input [(ngModel)]="brand.ctaLink" placeholder="/shop"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
                <label class="text-xs font-medium text-gray-600">
                  Social handle
                  <input [(ngModel)]="brand.socialHandle" placeholder="@erezer"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
                <label class="text-xs font-medium text-gray-600">
                  Social URL
                  <input [(ngModel)]="brand.socialUrl" placeholder="https://instagram.com/erezer"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
              </div>
              <div class="space-y-2">
                <div class="flex items-center justify-between">
                  <div>
                    <p class="text-xs font-medium text-gray-600">Gallery photos</p>
                    <p class="text-[11px] text-gray-400">
                      Upload photos from your computer or phone. An Instagram link can't be used here —
                      Instagram doesn't let other sites show its photos, and stories disappear after a day.
                    </p>
                  </div>
                  @if (perms.can('settings.homepage')) {
                    <label class="shrink-0 cursor-pointer rounded-lg border border-blue-200 px-2.5 py-1 text-xs font-medium text-blue-600 hover:bg-blue-50"
                      [class.opacity-50]="uploadingBrand() !== null">
                      {{ uploadingBrand() === 'new' ? 'Uploading…' : '+ Add photo' }}
                      <input type="file" accept="image/*" class="hidden" aria-label="Add a gallery photo"
                        (change)="onBrandImage($event, 'new')" [disabled]="uploadingBrand() !== null" />
                    </label>
                  }
                </div>
                <div class="grid grid-cols-2 gap-2 sm:grid-cols-3">
                  @for (img of brand.images; track $index; let i = $index) {
                    <div class="rounded-lg border border-gray-200 p-2">
                      <div class="aspect-[4/5] overflow-hidden rounded-md bg-gray-100">
                        @if (img) {
                          <img [src]="img" [alt]="'Gallery photo ' + (i + 1)" class="h-full w-full object-cover" />
                        }
                      </div>
                      @if (perms.can('settings.homepage')) {
                        <div class="mt-2 flex items-center justify-between gap-2">
                          <label class="cursor-pointer text-xs font-medium text-blue-600 hover:underline"
                            [class.opacity-50]="uploadingBrand() !== null">
                            {{ uploadingBrand() === i ? 'Uploading…' : 'Replace' }}
                            <input type="file" accept="image/*" class="hidden" [attr.aria-label]="'Replace gallery photo ' + (i + 1)"
                              (change)="onBrandImage($event, i)" [disabled]="uploadingBrand() !== null" />
                          </label>
                          <button type="button" (click)="removeBrandImage(i)" class="act-btn-icon shrink-0"
                            [attr.title]="'Remove photo ' + (i + 1)" [attr.aria-label]="'Remove photo ' + (i + 1)">
                            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                          </button>
                        </div>
                      }
                    </div>
                  } @empty {
                    <p class="text-xs text-gray-400">No photos yet.</p>
                  }
                </div>
                <p class="text-[11px] text-gray-400">The shop shuffles these into its photo wall, so the band looks different each visit.</p>
              </div>
              </fieldset>
            </section>

            <!-- About page -->
            <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-4" aria-labelledby="about-page-heading">
              <div class="flex items-start justify-between gap-3">
                <div>
                  <h2 id="about-page-heading" class="text-base font-semibold">About page</h2>
                  <p class="text-xs text-gray-500">The shop's About Us page (/about). Parts you leave empty are not shown.</p>
                  @if (!perms.can('settings.about')) {
                    <p class="text-xs text-gray-400">Needs the “Edit the About page” permission.</p>
                  }
                </div>
              </div>
              <fieldset [disabled]="!perms.can('settings.about')" class="min-w-0 space-y-4 disabled:opacity-60">
                <div class="grid grid-cols-1 gap-3">
                  <label class="text-xs font-medium text-gray-600">
                    Title
                    <input [(ngModel)]="about.title" name="aboutTitle" maxlength="150" placeholder="About Erezer"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Intro
                    <textarea [(ngModel)]="about.intro" name="aboutIntro" rows="3" maxlength="1000"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"></textarea>
                  </label>
                </div>

                <!-- Main photo -->
                <div class="space-y-2">
                  <p class="text-xs font-medium text-gray-600">Main photo</p>
                  <div class="flex flex-wrap items-center gap-3">
                    @if (about.heroImageUrl) {
                      <img [src]="about.heroImageUrl" alt="Main photo" class="h-20 w-32 rounded-lg object-cover border border-gray-200" />
                    }
                    <label class="px-2.5 py-1.5 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50 cursor-pointer">
                      {{ uploadingAbout() === 'hero' ? 'Uploading…' : (about.heroImageUrl ? 'Replace photo' : 'Upload photo') }}
                      <input type="file" accept="image/*" class="hidden" aria-label="Upload main photo"
                        (change)="onAboutImage($event, 'hero')" [disabled]="uploadingAbout() !== null" />
                    </label>
                    @if (about.heroImageUrl) {
                      <button type="button" (click)="about.heroImageUrl = ''" class="text-xs text-red-600 hover:underline">Remove photo</button>
                    }
                  </div>
                </div>

                <!-- Sections -->
                <div class="space-y-3">
                  <div class="flex items-center justify-between">
                    <p class="text-xs font-medium text-gray-600">Sections</p>
                    @if (perms.can('settings.about') && (about.sections?.length ?? 0) < 12) {
                      <button type="button" (click)="addAboutSection()"
                        class="px-2.5 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50">+ Section</button>
                    }
                  </div>
                  @for (sec of about.sections ?? []; track $index; let i = $index; let first = $first; let last = $last) {
                    <div class="rounded-lg border border-gray-200 p-3 space-y-2" [attr.aria-label]="'Section ' + (i + 1)">
                      <div class="flex items-center justify-between gap-2">
                        <span class="text-xs font-semibold text-gray-400">Section {{ i + 1 }}</span>
                        @if (perms.can('settings.about')) {
                          <div class="flex items-center gap-1">
                            <button type="button" (click)="moveAboutSection(i, -1)" [disabled]="first" class="px-2 py-0.5 text-xs text-gray-600 border border-gray-200 rounded disabled:opacity-30" [attr.aria-label]="'Move section ' + (i + 1) + ' up'">↑</button>
                            <button type="button" (click)="moveAboutSection(i, 1)" [disabled]="last" class="px-2 py-0.5 text-xs text-gray-600 border border-gray-200 rounded disabled:opacity-30" [attr.aria-label]="'Move section ' + (i + 1) + ' down'">↓</button>
                            <button type="button" (click)="removeAboutSection(i)" class="px-2 py-0.5 text-xs text-red-600 border border-red-200 rounded" [attr.aria-label]="'Remove section ' + (i + 1)">Remove</button>
                          </div>
                        }
                      </div>
                      <input [(ngModel)]="sec.heading" [name]="'aboutHeading' + i" maxlength="150" placeholder="Heading"
                        [attr.aria-label]="'Section ' + (i + 1) + ' heading'"
                        class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                      <textarea [(ngModel)]="sec.body" [name]="'aboutBody' + i" rows="4" maxlength="5000" placeholder="Text"
                        [attr.aria-label]="'Section ' + (i + 1) + ' text'"
                        class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"></textarea>
                      <div class="flex flex-wrap items-center gap-3">
                        @if (sec.imageUrl) {
                          <img [src]="sec.imageUrl" [alt]="'Section ' + (i + 1) + ' photo'" class="h-14 w-20 rounded object-cover border border-gray-200" />
                        }
                        <label class="px-2.5 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50 cursor-pointer">
                          {{ uploadingAbout() === i ? 'Uploading…' : (sec.imageUrl ? 'Replace photo' : 'Add photo (optional)') }}
                          <input type="file" accept="image/*" class="hidden" [attr.aria-label]="'Upload section ' + (i + 1) + ' photo'"
                            (change)="onAboutImage($event, i)" [disabled]="uploadingAbout() !== null" />
                        </label>
                        @if (sec.imageUrl) {
                          <button type="button" (click)="sec.imageUrl = ''" class="text-xs text-red-600 hover:underline">Remove photo</button>
                        }
                      </div>
                    </div>
                  } @empty {
                    <p class="text-xs text-gray-400">No sections yet.</p>
                  }
                </div>

                <!-- Closing button -->
                <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <label class="text-xs font-medium text-gray-600">
                    Button label
                    <input [(ngModel)]="about.ctaLabel" name="aboutCtaLabel" maxlength="150" placeholder="Shop the collection"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Button link
                    <input [(ngModel)]="about.ctaLink" name="aboutCtaLink" placeholder="/shop"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <p class="text-xs text-gray-400 sm:col-span-2">Leave both empty for no button. A link can be a shop page like /shop, or a full web address.</p>
                </div>
              </fieldset>
            </section>

            <!-- Highlights band -->
            <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-3">
              <div class="flex items-center justify-between">
                <div>
                  <h2 class="text-base font-semibold">Home highlights</h2>
                  <p class="text-xs text-gray-500">The stat band under "Shop by category" on the home page.</p>
                  @if (!perms.can('settings.homepage')) {
                    <p class="text-xs text-gray-400">Needs the “Edit home page content” permission.</p>
                  }
                </div>
                @if (perms.can('settings.homepage')) {
                <button type="button" (click)="addHighlight()"
                  class="px-2.5 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50">+ Highlight</button>
                }
              </div>
              <fieldset [disabled]="!perms.can('settings.homepage')" class="min-w-0 disabled:opacity-60">
              <div class="space-y-2">
                @for (h of highlights; track $index; let hi = $index) {
                  <div class="flex flex-wrap items-center gap-2 rounded-lg border border-gray-100 bg-gray-50/60 p-2">
                    <select [(ngModel)]="h.icon" class="rounded border border-gray-200 px-2 py-1 text-xs">
                      @for (opt of highlightIcons; track opt.value) {
                        <option [value]="opt.value">{{ opt.label }}</option>
                      }
                    </select>
                    <input [(ngModel)]="h.value" placeholder="Value (e.g. 4.9 / 5)"
                      class="w-28 rounded border border-gray-200 px-2 py-1 text-xs font-semibold" />
                    <input [(ngModel)]="h.label" placeholder="Label (e.g. Customer rating)"
                      class="w-40 rounded border border-gray-200 px-2 py-1 text-xs" />
                    <input [(ngModel)]="h.description" placeholder="Description"
                      class="flex-1 rounded border border-gray-200 px-2 py-1 text-xs" />
                    @if (perms.can('settings.homepage')) {
                      <button type="button" (click)="removeHighlight(hi)" class="text-red-400 hover:text-red-600" title="Remove">×</button>
                    }
                  </div>
                } @empty {
                  <p class="text-xs text-gray-400">No highlights yet.</p>
                }
              </div>
              </fieldset>
            </section>

            <!-- Footer -->
            <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-3">
              <div class="flex items-center justify-between">
                <div>
                  <h2 class="text-base font-semibold">Footer</h2>
                  <p class="text-xs text-gray-500">Shown on every storefront page.</p>
                  @if (!perms.can('settings.footer')) {
                    <p class="text-xs text-gray-400">Needs the “Edit the footer” permission.</p>
                  }
                </div>
                @if (perms.can('settings.footer')) {
                <button type="button" (click)="addFooterColumn()"
                  class="px-2.5 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50">+ Column</button>
                }
              </div>
              <fieldset [disabled]="!perms.can('settings.footer')" class="min-w-0 space-y-3 disabled:opacity-60">
              <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <label class="text-xs font-medium text-gray-600">
                  Brand name
                  <input [(ngModel)]="footer.brandName" placeholder="EREZER"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
                <label class="text-xs font-medium text-gray-600 sm:col-span-2">
                  Blurb
                  <textarea [(ngModel)]="footer.blurb" rows="2"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"></textarea>
                </label>
              </div>

              <div class="grid gap-4 sm:grid-cols-2">
                @for (col of footer.columns; track $index; let ci = $index) {
                  <div class="rounded-lg border border-gray-100 bg-gray-50/60 p-3 space-y-2">
                    <div class="flex items-center gap-2">
                      <input [(ngModel)]="col.title" placeholder="Column title"
                        class="flex-1 rounded border border-gray-200 px-2 py-1 text-xs font-semibold" />
                      @if (perms.can('settings.footer')) {
                      <button type="button" (click)="removeFooterColumn(ci)" class="act-btn-icon shrink-0" title="Delete column">
                        <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                      </button>
                      }
                    </div>
                    @for (link of col.links; track $index; let li = $index) {
                      <div class="flex items-center gap-1">
                        <input [(ngModel)]="link.label" placeholder="Label"
                          class="w-28 rounded border border-gray-200 px-2 py-1 text-xs" />
                        <input [(ngModel)]="link.url" placeholder="/path or https://…"
                          class="flex-1 rounded border border-gray-200 px-2 py-1 text-xs" />
                        @if (perms.can('settings.footer')) {
                          <button type="button" (click)="removeFooterLink(ci, li)" class="text-red-400 hover:text-red-600" title="Remove">×</button>
                        }
                      </div>
                    }
                    @if (perms.can('settings.footer')) {
                    <button type="button" (click)="addFooterLink(ci)"
                      class="text-xs font-medium text-blue-600 hover:underline">+ Link</button>
                    }
                  </div>
                } @empty {
                  <p class="text-xs text-gray-400">No columns yet.</p>
                }
              </div>

              <!-- Our promise items -->
              <div class="space-y-2 border-t border-gray-100 pt-4">
                <div class="flex items-center justify-between">
                  <div>
                    <p class="text-sm font-semibold text-gray-800">Our promise</p>
                    <p class="text-xs text-gray-500">Feature strip at the top of the footer.</p>
                  </div>
                  @if (perms.can('settings.footer')) {
                  <button type="button" (click)="addPromise()"
                    class="px-2.5 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50">+ Promise</button>
                  }
                </div>
                @for (p of footer.promises; track $index; let pi = $index) {
                  <div class="flex flex-wrap items-center gap-2 rounded-lg border border-gray-100 bg-gray-50/60 p-2">
                    <select [(ngModel)]="p.icon"
                      class="rounded border border-gray-200 px-2 py-1 text-xs">
                      @for (opt of promiseIcons; track opt.value) {
                        <option [value]="opt.value">{{ opt.label }}</option>
                      }
                    </select>
                    <input [(ngModel)]="p.title" placeholder="Title (e.g. Nationwide Delivery)"
                      class="w-48 rounded border border-gray-200 px-2 py-1 text-xs font-medium" />
                    <input [(ngModel)]="p.description" placeholder="Short description"
                      class="flex-1 rounded border border-gray-200 px-2 py-1 text-xs" />
                    @if (perms.can('settings.footer')) {
                      <button type="button" (click)="removePromise(pi)" class="text-red-400 hover:text-red-600" title="Remove">×</button>
                    }
                  </div>
                } @empty {
                  <p class="text-xs text-gray-400">No promise items yet.</p>
                }
              </div>

              <!-- Our outlets / store locations -->
              <div class="space-y-2 border-t border-gray-100 pt-4">
                <div class="flex items-center justify-between">
                  <div>
                    <p class="text-sm font-semibold text-gray-800">Our outlets</p>
                    <p class="text-xs text-gray-500">Store locations with photo, name, address and phone.</p>
                  </div>
                  @if (perms.can('settings.footer')) {
                  <button type="button" (click)="addOutlet()"
                    class="px-2.5 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50">+ Outlet</button>
                  }
                </div>
                <div class="grid gap-3 sm:grid-cols-2">
                  @for (o of footer.outlets; track $index; let oi = $index) {
                    <div class="rounded-lg border border-gray-100 bg-gray-50/60 p-3 space-y-2">
                      <div class="flex items-start gap-3">
                        <div class="h-20 w-20 flex-shrink-0 overflow-hidden rounded-lg border border-gray-200 bg-gray-100">
                          @if (o.imageUrl) {
                            <img [src]="o.imageUrl" alt="Outlet" class="h-full w-full object-cover" />
                          } @else {
                            <div class="flex h-full w-full items-center justify-center text-[10px] text-gray-400">No image</div>
                          }
                        </div>
                        <div class="flex-1 space-y-1">
                          <input [(ngModel)]="o.name" placeholder="Outlet name (e.g. Mirpur 12)"
                            class="w-full rounded border border-gray-200 px-2 py-1 text-xs font-semibold" />
                          @if (perms.can('settings.footer')) {
                          <label class="block">
                            <span class="text-[11px] text-blue-600 underline cursor-pointer">
                              {{ uploadingOutlet() === oi ? 'Uploading…' : 'Upload image' }}
                            </span>
                            <input type="file" accept="image/*" class="hidden"
                              [disabled]="uploadingOutlet() === oi"
                              (change)="onOutletImage($event, oi)" />
                          </label>
                          }
                        </div>
                        @if (perms.can('settings.footer')) {
                        <button type="button" (click)="removeOutlet(oi)" class="act-btn act-btn-delete shrink-0" title="Delete outlet">
                          <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                          Delete
                        </button>
                        }
                      </div>
                      <input [(ngModel)]="o.address" placeholder="Address"
                        class="w-full rounded border border-gray-200 px-2 py-1 text-xs" />
                      <input [(ngModel)]="o.phone" placeholder="Phone"
                        class="w-full rounded border border-gray-200 px-2 py-1 text-xs" />
                    </div>
                  } @empty {
                    <p class="text-xs text-gray-400">No outlets yet.</p>
                  }
                </div>
              </div>

              <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <label class="text-xs font-medium text-gray-600">
                  Copyright line
                  <input [(ngModel)]="footer.copyright" placeholder="© 2026 EREZER. All rights reserved."
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
                <label class="text-xs font-medium text-gray-600">
                  Tagline (bottom-right)
                  <input [(ngModel)]="footer.tagline" placeholder="Secure payments • Nationwide shipping"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
              </div>
              </fieldset>
            </section>

            <!-- Marquee trust strip -->
            <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-3">
              <div class="flex items-center justify-between">
                <div>
                  <h2 class="text-base font-semibold">Trust strip (marquee)</h2>
                  <p class="text-xs text-gray-500">Scrolling phrases under the featured products.</p>
                  @if (!perms.can('settings.homepage')) {
                    <p class="text-xs text-gray-400">Needs the “Edit home page content” permission.</p>
                  }
                </div>
                <div class="flex items-center gap-3">
                  <label class="flex items-center gap-2 text-xs font-medium text-gray-600">
                    <input type="checkbox" [(ngModel)]="marquee.enabled" [disabled]="!perms.can('settings.homepage')" /> Enabled
                  </label>
                  @if (perms.can('settings.homepage')) {
                  <button type="button" (click)="addMarqueeItem()"
                    class="px-2.5 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50">+ Phrase</button>
                  }
                </div>
              </div>
              <fieldset [disabled]="!perms.can('settings.homepage')" class="min-w-0 disabled:opacity-60">
              <div class="space-y-2">
                @for (item of marquee.items; track $index; let i = $index) {
                  <div class="flex items-center gap-2">
                    <input [(ngModel)]="marquee.items[i]" placeholder="Free shipping over ৳2000"
                      class="flex-1 rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                    @if (perms.can('settings.homepage')) {
                    <button type="button" (click)="removeMarqueeItem(i)" class="act-btn-icon shrink-0" title="Remove phrase">
                      <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                    </button>
                    }
                  </div>
                } @empty {
                  <p class="text-xs text-gray-400">No phrases yet.</p>
                }
              </div>
              </fieldset>
            </section>
          </div>
        </main>
      </div>
    </div>
  `,
})
export class StoreSettingsComponent implements OnInit {
  private readonly api = inject(StoreSettingsService);
  private readonly uploads = inject(UploadService);
  protected readonly perms = inject(PermissionService);
  private readonly notices = inject(NoticeService);

  protected readonly promiseIcons = PROMISE_ICONS;
  protected readonly highlightIcons = HIGHLIGHT_ICONS;

  readonly loading      = signal(false);
  readonly saving       = signal(false);
  readonly errorMessage = signal<string>('');
  readonly savedMessage = signal<string>('');
  /** Index of the outlet whose image is currently uploading (-1 = none). */
  readonly uploadingOutlet = signal(-1);
  /** Gallery photo uploading: 'new', the photo's place in the list, or null. */
  readonly uploadingBrand = signal<'new' | number | null>(null);
  /** About page photo uploading: 'hero', a section index, or null. */
  readonly uploadingAbout = signal<'hero' | number | null>(null);

  protected model: StoreSettings = {
    returnPolicyText: '',
    exchangeWindowDays: 3,
    supportPhone: '',
    supportEmail: '',
    supportHours: '',
    sizeChart: null,
    brandStory: null,
    footer: null,
    marquee: null,
    highlights: null,
    paymentCodEnabled: true,
    paymentBkashEnabled: true,
    paymentCardEnabled: true,
    // Discount switches are owned by the Discounts screen, which has its own
    // endpoint; the settings save ignores them. Carried only to fit the type.
    discountsEnabled: true,
    discountsGlobalEnabled: true,
    discountsCategoryEnabled: true,
    discountsProductEnabled: true,
  };
  protected chart: SizeChart = { columns: [...EMPTY_CHART.columns], rows: [] };
  protected brand: BrandStory = structuredClone(EMPTY_BRAND);
  protected about: AboutPage = structuredClone(EMPTY_ABOUT);
  protected footer: Footer = structuredClone(EMPTY_FOOTER);
  protected marquee: Marquee = structuredClone(EMPTY_MARQUEE);
  protected highlights: Highlight[] = [];

  ngOnInit(): void {
    this.loading.set(true);
    this.api.get().pipe(catchError((err) => {
      this.errorMessage.set(parseApiError(err));
      return of(null);
    })).subscribe((s) => {
      this.loading.set(false);
      if (!s) return;
      this.apply(s);
    });
  }

  /** Copy a loaded/saved settings payload into the editable working models. */
  private apply(s: StoreSettings): void {
    this.model = s;
    this.chart = this.normalizeChart(s.sizeChart);
    this.brand = this.normalizeBrand(s.brandStory);
    this.about = {
      title: s.aboutPage?.title ?? '',
      intro: s.aboutPage?.intro ?? '',
      heroImageUrl: s.aboutPage?.heroImageUrl ?? '',
      sections: (s.aboutPage?.sections ?? []).map((x) => ({
        heading: x.heading ?? '', body: x.body ?? '', imageUrl: x.imageUrl ?? '',
      })),
      ctaLabel: s.aboutPage?.ctaLabel ?? '',
      ctaLink: s.aboutPage?.ctaLink ?? '',
    };
    this.footer = this.normalizeFooter(s.footer);
    this.marquee = {
      enabled: s.marquee?.enabled ?? true,
      items: [...(s.marquee?.items ?? [])],
    };
    this.highlights = (s.highlights ?? []).map((h) => ({
      icon: h.icon ?? 'star',
      value: h.value ?? '',
      label: h.label ?? '',
      description: h.description ?? '',
    }));
  }

  protected addHighlight(): void {
    this.highlights.push({ icon: 'star', value: '', label: '', description: '' });
  }
  protected removeHighlight(i: number): void { this.highlights.splice(i, 1); }

  protected addMarqueeItem(): void { this.marquee.items.push(''); }
  protected removeMarqueeItem(i: number): void { this.marquee.items.splice(i, 1); }

  private normalizeBrand(input: BrandStory | null): BrandStory {
    return {
      eyebrow: input?.eyebrow ?? '',
      heading: input?.heading ?? '',
      body: input?.body ?? '',
      ctaLabel: input?.ctaLabel ?? '',
      ctaLink: input?.ctaLink ?? '/shop',
      socialHandle: input?.socialHandle ?? '',
      socialUrl: input?.socialUrl ?? '',
      images: [...(input?.images ?? [])],
    };
  }

  private normalizeFooter(input: Footer | null): Footer {
    return {
      brandName: input?.brandName ?? 'EREZER',
      blurb: input?.blurb ?? '',
      columns: (input?.columns ?? []).map((c) => ({
        title: c.title ?? '',
        links: (c.links ?? []).map((l) => ({ label: l.label ?? '', url: l.url ?? '' })),
      })),
      promises: (input?.promises ?? []).map((p) => ({
        icon: p.icon ?? 'quality',
        title: p.title ?? '',
        description: p.description ?? '',
      })),
      outlets: (input?.outlets ?? []).map((o) => ({
        imageUrl: o.imageUrl ?? '',
        name: o.name ?? '',
        address: o.address ?? '',
        phone: o.phone ?? '',
      })),
      copyright: input?.copyright ?? '',
      tagline: input?.tagline ?? '',
    };
  }

  protected removeBrandImage(i: number): void { this.brand.images.splice(i, 1); }

  /**
   * Adds or replaces a gallery photo. The file is uploaded to the shop's own
   * media store, so the picture keeps loading whatever happens on social media.
   */
  protected onBrandImage(event: Event, target: 'new' | number): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploadingBrand.set(target);
    this.errorMessage.set('');
    this.uploads.uploadImage(file).pipe(catchError((err) => {
      this.errorMessage.set(parseApiError(err));
      this.uploadingBrand.set(null);
      return of(null);
    })).subscribe((url) => {
      this.uploadingBrand.set(null);
      if (url) {
        if (target === 'new') this.brand.images.push(url);
        else this.brand.images[target] = url;
      }
      input.value = '';
    });
  }
  protected addFooterColumn(): void { this.footer.columns.push({ title: 'New', links: [] }); }
  protected removeFooterColumn(i: number): void { this.footer.columns.splice(i, 1); }
  protected addFooterLink(ci: number): void { this.footer.columns[ci].links.push({ label: '', url: '' }); }
  protected removeFooterLink(ci: number, li: number): void { this.footer.columns[ci].links.splice(li, 1); }

  // ── "Our promise" items ────────────────────────────────────────────────────
  protected addPromise(): void {
    this.footer.promises.push({ icon: 'quality', title: '', description: '' });
  }
  protected removePromise(i: number): void { this.footer.promises.splice(i, 1); }

  // ── "Our outlets" / store locations ────────────────────────────────────────
  protected addOutlet(): void {
    this.footer.outlets.push({ imageUrl: '', name: '', address: '', phone: '' });
  }
  protected removeOutlet(i: number): void { this.footer.outlets.splice(i, 1); }

  protected addAboutSection(): void {
    (this.about.sections ??= []).push({ heading: '', body: '', imageUrl: '' });
  }

  protected removeAboutSection(i: number): void {
    this.about.sections?.splice(i, 1);
  }

  protected moveAboutSection(i: number, step: -1 | 1): void {
    const list = this.about.sections ?? [];
    const j = i + step;
    if (j < 0 || j >= list.length) return;
    [list[i], list[j]] = [list[j], list[i]];
  }

  protected onAboutImage(event: Event, target: 'hero' | number): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploadingAbout.set(target);
    this.errorMessage.set('');
    this.uploads.uploadImage(file).pipe(catchError((err) => {
      this.errorMessage.set(parseApiError(err));
      this.uploadingAbout.set(null);
      return of(null);
    })).subscribe((url) => {
      this.uploadingAbout.set(null);
      if (url) {
        if (target === 'hero') this.about.heroImageUrl = url;
        else if (this.about.sections?.[target]) this.about.sections[target].imageUrl = url;
      }
      input.value = '';
    });
  }

  /** The About page as the server stores it: empty text as null, so an untouched page counts as unchanged. */
  private aboutForSave(): AboutPage {
    const t = (v: string | null | undefined) => (v && v.trim() ? v.trim() : null);
    return {
      title: t(this.about.title),
      intro: t(this.about.intro),
      heroImageUrl: t(this.about.heroImageUrl),
      sections: (this.about.sections ?? [])
        .map((x) => ({ heading: t(x.heading), body: t(x.body), imageUrl: t(x.imageUrl) }))
        .filter((x) => x.heading || x.body || x.imageUrl),
      ctaLabel: t(this.about.ctaLabel),
      ctaLink: t(this.about.ctaLink),
    };
  }

  protected onOutletImage(event: Event, index: number): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploadingOutlet.set(index);
    this.errorMessage.set('');
    this.uploads.uploadImage(file).pipe(catchError((err) => {
      this.errorMessage.set(parseApiError(err));
      this.uploadingOutlet.set(-1);
      return of(null);
    })).subscribe((url) => {
      this.uploadingOutlet.set(-1);
      if (url) this.footer.outlets[index].imageUrl = url;
      input.value = '';
    });
  }

  /** Ensures every row has exactly one cell per column. */
  private normalizeChart(input: SizeChart | null): SizeChart {
    const columns = input?.columns?.length ? [...input.columns] : [...EMPTY_CHART.columns];
    const rows = (input?.rows ?? []).map((r) => ({
      size: r.size,
      cells: columns.map((_, i) => r.cells?.[i] ?? { cm: null, inch: null }),
    }));
    return { columns, rows };
  }

  protected addColumn(): void {
    this.chart.columns.push('New');
    for (const row of this.chart.rows) row.cells.push({ cm: null, inch: null });
  }

  protected removeColumn(index: number): void {
    this.chart.columns.splice(index, 1);
    for (const row of this.chart.rows) row.cells.splice(index, 1);
  }

  protected addRow(): void {
    this.chart.rows.push({
      size: '',
      cells: this.chart.columns.map(() => ({ cm: null, inch: null })),
    });
  }

  protected removeRow(index: number): void {
    this.chart.rows.splice(index, 1);
  }

  protected save(): void {
    this.saving.set(true);
    this.errorMessage.set('');
    this.savedMessage.set('');
    // The server refuses a save that changes a section without its permission,
    // so a read-only section goes back exactly as loaded, not the normalised
    // working copy. Policy, support and payment fields sit on `model` itself
    // and cannot change while their inputs are disabled.
    const homepage = this.perms.can('settings.homepage');
    const payload: StoreSettings = {
      ...this.model,
      sizeChart: this.perms.can('settings.sizechart') ? this.chart : this.model.sizeChart,
      brandStory: homepage ? this.brand : this.model.brandStory,
      footer: this.perms.can('settings.footer') ? this.footer : this.model.footer,
      marquee: homepage ? this.marquee : this.model.marquee,
      highlights: homepage ? this.highlights : this.model.highlights,
      aboutPage: this.perms.can('settings.about') ? this.aboutForSave() : this.model.aboutPage,
    };
    this.api.update(payload).pipe(catchError((err) => {
      this.errorMessage.set(parseApiError(err));
      this.saving.set(false);
      return of(null);
    })).subscribe((s) => {
      this.saving.set(false);
      if (!s) return;
      this.apply(s);
      this.notices.success('Settings saved');
      this.savedMessage.set('Saved.');
      setTimeout(() => this.savedMessage.set(''), 2500);
    });
  }
}
