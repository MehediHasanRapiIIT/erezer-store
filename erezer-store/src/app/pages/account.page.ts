import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { EcommerceStore } from '../core/store/ecommerce.store';
import { ApiAddress, ApiProfile, AddressPayload, AddressType } from '../core/api.models';
import { RevealDirective } from '../core/reveal.directive';

type AuthTab = 'login' | 'register' | 'forgot';

@Component({
  standalone: true,
  imports: [FormsModule, DatePipe, CurrencyPipe, RouterLink, RevealDirective],
  template: `
    <div class="mb-8" appReveal>
      <p class="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-500 dark:text-neutral-400">My account</p>
      <h1 class="app-section-title mt-2 text-3xl md:text-4xl">
        {{ auth.isAuthenticated() ? 'Welcome back' : 'Account' }}
      </h1>
    </div>

    <section class="grid gap-8 lg:grid-cols-[1.1fr_2fr] lg:items-start">

      <!-- ── Auth / Profile card ──────────────────────────────────────────── -->
      <article class="app-card p-6 lg:sticky lg:top-24" appReveal>

        @if (!auth.isAuthenticated()) {
          <!-- Animated tab switcher -->
          <div class="relative mb-5 grid grid-cols-3 rounded-xl bg-neutral-100 p-1 dark:bg-neutral-800">
            <span class="absolute inset-y-1 left-1 w-[calc((100%-0.5rem)/3)] rounded-lg bg-white shadow-sm transition-transform duration-300 ease-out dark:bg-neutral-700"
              [style.transform]="'translateX(' + (tabIndex() * 100) + '%)'"></span>
            <button (click)="setTab('login')" class="relative z-10 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
              [class.text-neutral-900]="tab() === 'login'" [class.dark:text-white]="tab() === 'login'"
              [class.text-neutral-500]="tab() !== 'login'">Sign in</button>
            <button (click)="setTab('register')" class="relative z-10 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
              [class.text-neutral-900]="tab() === 'register'" [class.dark:text-white]="tab() === 'register'"
              [class.text-neutral-500]="tab() !== 'register'">Register</button>
            <button (click)="setTab('forgot')" class="relative z-10 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
              [class.text-neutral-900]="tab() === 'forgot'" [class.dark:text-white]="tab() === 'forgot'"
              [class.text-neutral-500]="tab() !== 'forgot'">Forgot</button>
          </div>

          <!-- Login -->
          @if (tab() === 'login') {
            <form #loginForm="ngForm" (ngSubmit)="signIn()" class="fade-in space-y-3" novalidate>
              <div>
                <div class="relative">
                  <svg class="field-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75"/></svg>
                  <input name="loginEmail" [(ngModel)]="loginEmail" #leEmail="ngModel" type="email" required email
                    placeholder="you@example.com" autocomplete="email"
                    class="field !pl-10" [class.field-error]="invalid(leEmail, loginForm)" />
                </div>
                @if (invalid(leEmail, loginForm)) {
                  <p class="err">{{ leEmail.errors?.['required'] ? 'Email is required.' : 'Enter a valid email address.' }}</p>
                }
              </div>
              <div>
                <div class="relative">
                  <svg class="field-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z"/></svg>
                  <input name="loginPw" [(ngModel)]="loginPassword" #lePw="ngModel" [type]="showLoginPw() ? 'text' : 'password'" required
                    placeholder="Password" autocomplete="current-password"
                    class="field !pl-10 !pr-10" [class.field-error]="invalid(lePw, loginForm)" />
                  <button type="button" (click)="showLoginPw.set(!showLoginPw())" class="pw-toggle" [attr.aria-label]="showLoginPw() ? 'Hide password' : 'Show password'">
                    @if (showLoginPw()) {
                      <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88"/></svg>
                    } @else {
                      <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z"/><path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/></svg>
                    }
                  </button>
                </div>
                @if (invalid(lePw, loginForm)) { <p class="err">Password is required.</p> }
              </div>
              @if (auth.error()) { <p class="err">{{ auth.error() }}</p> }
              <button type="submit" [disabled]="auth.loading()" class="btn-primary w-full !rounded-full">
                {{ auth.loading() ? 'Signing in…' : 'Sign in' }}
              </button>
              <button type="button" (click)="setTab('forgot')" class="block w-full text-center text-xs text-neutral-500 underline-offset-4 hover:underline dark:text-neutral-400">
                Forgot your password?
              </button>
            </form>
          }

          <!-- Register -->
          @if (tab() === 'register') {
            <form #regForm="ngForm" (ngSubmit)="register()" class="fade-in space-y-3" novalidate>
              <div class="grid grid-cols-2 gap-2">
                <div>
                  <input name="regFirst" [(ngModel)]="regFirstName" #rFirst="ngModel" required placeholder="First name" autocomplete="given-name"
                    class="field" [class.field-error]="invalid(rFirst, regForm)" />
                  @if (invalid(rFirst, regForm)) { <p class="err">Required.</p> }
                </div>
                <div>
                  <input name="regLast" [(ngModel)]="regLastName" #rLast="ngModel" required placeholder="Last name" autocomplete="family-name"
                    class="field" [class.field-error]="invalid(rLast, regForm)" />
                  @if (invalid(rLast, regForm)) { <p class="err">Required.</p> }
                </div>
              </div>
              <div>
                <div class="relative">
                  <svg class="field-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75"/></svg>
                  <input name="regEmail" [(ngModel)]="regEmail" #rEmail="ngModel" type="email" required email placeholder="you@example.com" autocomplete="email"
                    class="field !pl-10" [class.field-error]="invalid(rEmail, regForm)" />
                </div>
                @if (invalid(rEmail, regForm)) {
                  <p class="err">{{ rEmail.errors?.['required'] ? 'Email is required.' : 'Enter a valid email address.' }}</p>
                }
              </div>
              <div>
                <div class="relative">
                  <svg class="field-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z"/></svg>
                  <input name="regPw" [(ngModel)]="regPassword" #rPw="ngModel" [type]="showRegPw() ? 'text' : 'password'" required minlength="8"
                    placeholder="Password (min 8 characters)" autocomplete="new-password"
                    class="field !pl-10 !pr-10" [class.field-error]="invalid(rPw, regForm)" />
                  <button type="button" (click)="showRegPw.set(!showRegPw())" class="pw-toggle" [attr.aria-label]="showRegPw() ? 'Hide password' : 'Show password'">
                    @if (showRegPw()) {
                      <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88"/></svg>
                    } @else {
                      <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z"/><path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/></svg>
                    }
                  </button>
                </div>
                @if (invalid(rPw, regForm)) {
                  <p class="err">{{ rPw.errors?.['required'] ? 'Password is required.' : 'Use at least 8 characters.' }}</p>
                }
              </div>
              @if (auth.error()) { <p class="err">{{ auth.error() }}</p> }
              <button type="submit" [disabled]="auth.loading()" class="btn-primary w-full !rounded-full">
                {{ auth.loading() ? 'Creating account…' : 'Create account' }}
              </button>
              <p class="text-xs text-neutral-500 dark:text-neutral-400">We'll email you a link to verify your address.</p>
            </form>
          }

          <!-- Forgot -->
          @if (tab() === 'forgot') {
            <form #forgotForm="ngForm" (ngSubmit)="forgot()" class="fade-in space-y-3" novalidate>
              <p class="text-sm text-neutral-600 dark:text-neutral-300">Enter your email and we'll send a reset link.</p>
              <div>
                <div class="relative">
                  <svg class="field-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75"/></svg>
                  <input name="forgotEmail" [(ngModel)]="forgotEmail" #fEmail="ngModel" type="email" required email placeholder="you@example.com" autocomplete="email"
                    class="field !pl-10" [class.field-error]="invalid(fEmail, forgotForm)" />
                </div>
                @if (invalid(fEmail, forgotForm)) {
                  <p class="err">{{ fEmail.errors?.['required'] ? 'Email is required.' : 'Enter a valid email address.' }}</p>
                }
              </div>
              @if (forgotMsg()) { <p class="text-sm text-emerald-600">{{ forgotMsg() }}</p> }
              @if (auth.error()) { <p class="err">{{ auth.error() }}</p> }
              <button type="submit" [disabled]="auth.loading()" class="btn-primary w-full !rounded-full">
                {{ auth.loading() ? 'Sending…' : 'Send reset link' }}
              </button>
            </form>
          }

        } @else {
          <!-- Signed in -->
          @if (profile(); as p) {
            <div class="fade-in space-y-4">
              <div class="flex items-center gap-4">
                <span class="inline-flex h-14 w-14 items-center justify-center rounded-full bg-neutral-900 text-lg font-semibold text-white dark:bg-white dark:text-black">
                  {{ initials() }}
                </span>
                <div class="min-w-0">
                  <p class="truncate font-semibold">{{ p.firstName }} {{ p.lastName }}</p>
                  <p class="truncate text-sm text-neutral-500 dark:text-neutral-400">{{ p.email || auth.email() }}</p>
                </div>
              </div>

              @if (auth.emailVerified()) {
                <p class="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300">
                  <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/></svg>
                  Email verified
                </p>
              } @else {
                <p class="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">
                  Your email isn't verified yet. Check your inbox for the verification link.
                </p>
              }

              <!-- Edit profile -->
              @if (editingProfile()) {
                <form #profileForm="ngForm" (ngSubmit)="saveProfile()" class="fade-in space-y-2" novalidate>
                  <div class="grid grid-cols-2 gap-2">
                    <div>
                      <input name="editFirst" [(ngModel)]="editFirstName" #eFirst="ngModel" required placeholder="First name"
                        class="field" [class.field-error]="invalid(eFirst, profileForm)" />
                      @if (invalid(eFirst, profileForm)) { <p class="err">Required.</p> }
                    </div>
                    <div>
                      <input name="editLast" [(ngModel)]="editLastName" #eLast="ngModel" required placeholder="Last name"
                        class="field" [class.field-error]="invalid(eLast, profileForm)" />
                      @if (invalid(eLast, profileForm)) { <p class="err">Required.</p> }
                    </div>
                  </div>
                  <div>
                    <input name="editEmail" [(ngModel)]="editEmail" #eEmail="ngModel" type="email" required email placeholder="Email"
                      class="field" [class.field-error]="invalid(eEmail, profileForm)" />
                    @if (invalid(eEmail, profileForm)) {
                      <p class="err">{{ eEmail.errors?.['required'] ? 'Email is required.' : 'Enter a valid email address.' }}</p>
                    }
                  </div>
                  <div class="flex gap-2 pt-1">
                    <button type="submit" [disabled]="profileForm.invalid" class="btn-primary flex-1 !rounded-full">Save</button>
                    <button type="button" (click)="editingProfile.set(false)" class="btn-secondary flex-1 !rounded-full">Cancel</button>
                  </div>
                  @if (profileSaveMsg()) { <p class="text-sm text-emerald-600">{{ profileSaveMsg() }}</p> }
                </form>
              } @else {
                <div class="flex gap-2">
                  <button (click)="startEditProfile(p)" class="btn-secondary flex-1 !rounded-full">Edit profile</button>
                  <button (click)="signOut()" class="btn-secondary flex-1 !rounded-full">Sign out</button>
                </div>
              }
            </div>
          } @else if (profileLoading()) {
            <p class="app-muted text-sm">Loading profile…</p>
          } @else {
            <button (click)="signOut()" class="btn-secondary w-full !rounded-full">Sign out</button>
          }
        }
      </article>

      <!-- ── Right column ──────────────────────────────────────────────────── -->
      <div class="space-y-6">

        @if (auth.isAuthenticated()) {
          <article class="app-card p-6" appReveal>
            <div class="mb-4 flex items-center justify-between">
              <div>
                <h2 class="text-lg font-semibold">Addresses</h2>
                <p class="text-xs app-muted">{{ addresses().length }}/3 saved</p>
              </div>
              @if (addresses().length < 3 && !showAddressForm()) {
                <button (click)="showAddressForm.set(true)" class="btn-secondary !rounded-full text-sm">+ Add address</button>
              }
            </div>

            @if (showAddressForm()) {
              <form #addrForm="ngForm" (ngSubmit)="saveAddress()" class="fade-in mb-4 space-y-2 rounded-2xl border border-neutral-200 p-4 dark:border-neutral-700" novalidate>
                <div>
                  <input name="addrName" [(ngModel)]="addrName" #aName="ngModel" required placeholder="Label (e.g. Home)"
                    class="field" [class.field-error]="invalid(aName, addrForm)" />
                  @if (invalid(aName, addrForm)) { <p class="err">Label is required.</p> }
                </div>
                <div>
                  <input name="addrStreet" [(ngModel)]="addrStreet" #aStreet="ngModel" required placeholder="Street address"
                    class="field" [class.field-error]="invalid(aStreet, addrForm)" />
                  @if (invalid(aStreet, addrForm)) { <p class="err">Street address is required.</p> }
                </div>
                <div class="grid grid-cols-2 gap-2">
                  <input name="addrHouse" [(ngModel)]="addrHouse" type="number" min="0" placeholder="House number" class="field" />
                  <select name="addrType" [(ngModel)]="addrType" class="field cursor-pointer">
                    <option value="HOME">Home</option>
                    <option value="WORK">Work</option>
                    <option value="OTHER">Other</option>
                  </select>
                </div>
                <input name="addrApt" [(ngModel)]="addrApartment" placeholder="Apartment / building name (optional)" class="field" />
                @if (addrError()) { <p class="err">{{ addrError() }}</p> }
                <div class="flex gap-2 pt-1">
                  <button type="submit" [disabled]="addrForm.invalid" class="btn-primary flex-1 !rounded-full text-sm">Save address</button>
                  <button type="button" (click)="showAddressForm.set(false)" class="btn-secondary flex-1 !rounded-full text-sm">Cancel</button>
                </div>
              </form>
            }

            <div class="grid gap-3 sm:grid-cols-2">
              @for (addr of addresses(); track addr.id) {
                <div class="group relative rounded-2xl border border-neutral-200 p-4 transition hover:border-neutral-300 dark:border-neutral-700 dark:hover:border-neutral-600">
                  <div class="mb-1 flex items-center gap-2">
                    <span class="inline-flex h-7 w-7 items-center justify-center rounded-full bg-neutral-100 text-neutral-600 dark:bg-neutral-800 dark:text-neutral-300">
                      @switch (addr.addressType) {
                        @case ('WORK') { <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M20.25 14.15v4.6a1.5 1.5 0 01-1.5 1.5H5.25a1.5 1.5 0 01-1.5-1.5v-4.6m16.5 0a1.5 1.5 0 00-1.5-1.5h-3.75m-7.5 0H5.25a1.5 1.5 0 00-1.5 1.5m0 0V8.25a1.5 1.5 0 011.5-1.5h13.5a1.5 1.5 0 011.5 1.5v5.9m-9-9V4.5a1.5 1.5 0 011.5-1.5h1.5a1.5 1.5 0 011.5 1.5v1.65"/></svg> }
                        @default { <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M2.25 12l8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75"/></svg> }
                      }
                    </span>
                    <p class="text-sm font-medium">{{ addr.name }}</p>
                    <span class="rounded-full bg-neutral-100 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-neutral-500 dark:bg-neutral-800">{{ addr.addressType }}</span>
                  </div>
                  <p class="text-sm text-neutral-600 dark:text-neutral-300">{{ addr.address }}</p>
                  @if (addr.apartmentName) { <p class="text-xs text-neutral-500">{{ addr.apartmentName }}</p> }
                  <button (click)="deleteAddress(addr.id)"
                    class="absolute right-3 top-3 text-neutral-300 opacity-0 transition hover:text-red-500 group-hover:opacity-100"
                    aria-label="Remove address">
                    <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/></svg>
                  </button>
                </div>
              } @empty {
                <p class="app-muted text-sm sm:col-span-2">No addresses saved yet.</p>
              }
            </div>
          </article>
        }

        <!-- Order history -->
        <article class="app-card p-6" appReveal>
          <div class="mb-4 flex items-end justify-between gap-2">
            <h2 class="text-lg font-semibold">Order history</h2>
            <a routerLink="/orders" class="text-sm underline underline-offset-4">View all</a>
          </div>
          <div class="space-y-3">
            @for (order of apiOrders(); track order.id; let i = $index) {
              <a [routerLink]="['/orders', order.id]"
                class="block rounded-2xl border border-neutral-200 p-4 transition hover:border-neutral-300 hover:shadow-sm dark:border-neutral-700 dark:hover:border-neutral-600"
                [appReveal]="i">
                <div class="flex flex-wrap items-center justify-between gap-2">
                  <p class="font-medium">Order #{{ order.id }}</p>
                  <span class="rounded-full px-2.5 py-0.5 text-xs font-semibold" [class]="statusClass(order.orderStatus)">
                    {{ order.orderStatus }}
                  </span>
                </div>
                <div class="mt-2 flex flex-wrap items-center justify-between gap-2 text-sm">
                  <span class="text-neutral-500 dark:text-neutral-400">{{ order.createdAt | date: 'medium' }}</span>
                  <span class="font-semibold">{{ order.totalAmount | currency:'BDT':'৳' }}</span>
                </div>
              </a>
            } @empty {
              <div class="flex flex-col items-center gap-2 py-10 text-center">
                <span class="inline-flex h-12 w-12 items-center justify-center rounded-full bg-neutral-100 text-neutral-400 dark:bg-neutral-800">
                  <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.4"><path stroke-linecap="round" stroke-linejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 00-3 3h15.75m-12.75-3h11.218c1.121-2.3 2.1-4.684 2.924-7.138a60.114 60.114 0 00-16.536-1.84M7.5 14.25L5.106 5.272"/></svg>
                </span>
                <p class="text-sm app-muted">No orders yet.</p>
                <a routerLink="/shop" class="btn-secondary !rounded-full text-sm">Start shopping</a>
              </div>
            }
          </div>
        </article>
      </div>
    </section>
  `,
  styles: [`
    .field {
      width: 100%; border-radius: 0.75rem;
      border: 1px solid rgb(212 212 212); background: white;
      padding: 0.6rem 0.9rem; font-size: 0.875rem; color: rgb(23 23 23);
      outline: none; transition: border-color .2s ease, box-shadow .2s ease;
    }
    .field::placeholder { color: rgb(163 163 163); }
    .field:focus { border-color: rgb(82 82 82); box-shadow: 0 0 0 3px rgba(0,0,0,.06); }
    :host-context(.dark) .field { border-color: rgb(64 64 64); background: rgb(23 23 23); color: rgb(229 229 229); }
    :host-context(.dark) .field:focus { border-color: rgb(163 163 163); box-shadow: 0 0 0 3px rgba(255,255,255,.10); }
    .field-error, .field-error:focus {
      border-color: rgb(248 113 113) !important;
      box-shadow: 0 0 0 3px rgba(248,113,113,.14) !important;
    }
    .field-icon {
      position: absolute; left: 0.85rem; top: 50%; transform: translateY(-50%);
      height: 1.05rem; width: 1.05rem; color: rgb(163 163 163); pointer-events: none;
    }
    .pw-toggle {
      position: absolute; right: 0.6rem; top: 50%; transform: translateY(-50%);
      color: rgb(140 140 140); transition: color .15s ease;
    }
    .pw-toggle:hover { color: rgb(64 64 64); }
    :host-context(.dark) .pw-toggle:hover { color: rgb(212 212 212); }
    .err { margin-top: 0.3rem; font-size: 0.75rem; color: rgb(239 68 68); }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }
    .fade-in { animation: fadeIn .3s ease both; }
  `]
})
export class AccountPage implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly store = inject(EcommerceStore);
  private readonly router = inject(Router);

  // ── auth flow ──────────────────────────────────────────────────────────────
  protected readonly tab = signal<AuthTab>('login');
  protected readonly tabIndex = computed(() =>
    this.tab() === 'login' ? 0 : this.tab() === 'register' ? 1 : 2);
  protected readonly showLoginPw = signal(false);
  protected readonly showRegPw   = signal(false);
  protected loginEmail    = '';
  protected loginPassword = '';
  protected regEmail      = '';
  protected regPassword   = '';
  protected regFirstName  = '';
  protected regLastName   = '';
  protected forgotEmail   = '';
  protected readonly forgotMsg = signal('');

  // ── profile ────────────────────────────────────────────────────────────────
  protected readonly profile        = signal<ApiProfile | null>(null);
  protected readonly profileLoading = signal(false);
  protected readonly editingProfile = signal(false);
  protected readonly profileSaveMsg = signal('');
  protected editFirstName = '';
  protected editLastName  = '';
  protected editEmail     = '';

  protected readonly initials = computed(() => {
    const p = this.profile();
    const a = (p?.firstName ?? '').trim();
    const b = (p?.lastName ?? '').trim();
    const i = (a[0] ?? '') + (b[0] ?? '');
    return (i || (this.auth.email()?.[0] ?? '?')).toUpperCase();
  });

  // ── addresses ──────────────────────────────────────────────────────────────
  protected readonly addresses      = signal<ApiAddress[]>([]);
  protected readonly showAddressForm = signal(false);
  protected readonly addrError      = signal('');
  protected addrName      = '';
  protected addrStreet    = '';
  protected addrHouse     = 0;
  protected addrApartment = '';
  protected addrType: AddressType = 'HOME';

  // ── orders ─────────────────────────────────────────────────────────────────
  protected readonly apiOrders = signal<Array<{ id: string; createdAt: string; orderStatus: string; totalAmount: number }>>([]);

  ngOnInit(): void {
    if (this.auth.isAuthenticated()) {
      this.loadProfile();
      this.loadOrders();
    }
  }

  /** True once a control is invalid AND has been touched/dirtied or the form was submitted. */
  protected invalid(
    ctrl: { invalid: boolean | null; touched: boolean | null; dirty: boolean | null },
    form: { submitted: boolean },
  ): boolean {
    return !!ctrl.invalid && (!!ctrl.touched || !!ctrl.dirty || form.submitted);
  }

  /** Tailwind classes for an order-status badge. */
  protected statusClass(status: string): string {
    switch ((status || '').toUpperCase()) {
      case 'DELIVERED':
      case 'COMPLETED':
        return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300';
      case 'SHIPPED':
      case 'OUT_FOR_DELIVERY':
        return 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300';
      case 'CONFIRMED':
      case 'PROCESSING':
        return 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300';
      case 'CANCELLED':
      case 'RETURNED':
        return 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300';
      default:
        return 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300';
    }
  }

  // ── auth ───────────────────────────────────────────────────────────────────

  protected setTab(t: AuthTab): void {
    this.tab.set(t);
    this.forgotMsg.set('');
  }

  protected async signIn(): Promise<void> {
    if (!this.loginEmail.trim() || !this.loginPassword) return;
    try {
      await this.auth.login({ email: this.loginEmail.trim(), password: this.loginPassword });
      this.loginPassword = '';
      this.afterAuth();
    } catch { /* error surfaced via auth.error() */ }
  }

  protected async register(): Promise<void> {
    if (!this.regEmail.trim() || !this.regPassword || !this.regFirstName.trim() || !this.regLastName.trim()) return;
    try {
      await this.auth.register({
        email:     this.regEmail.trim(),
        password:  this.regPassword,
        firstName: this.regFirstName.trim(),
        lastName:  this.regLastName.trim(),
      });
      this.regPassword = '';
      this.afterAuth();
    } catch { /* error surfaced via auth.error() */ }
  }

  /**
   * Post-login/registration: merge the local guest cart into the account, load
   * the server cart, then refresh profile/orders. If the user arrived here to
   * check out (?redirect=…), send them back once the cart is merged.
   */
  private afterAuth(): void {
    const userId = this.auth.userId();
    const guest = this.store.cart();
    const redirect = new URLSearchParams(window.location.search).get('redirect');

    const finish = () => {
      if (userId) {
        this.api.getCart(userId).pipe(catchError(() => of([]))).subscribe((items) => {
          this.store.loadApiCart(items);
          if (redirect) void this.router.navigateByUrl(redirect);
        });
      }
    };

    if (userId && guest.length) {
      // Replay each guest line through the (server-resolved) add-to-cart endpoint,
      // which dedups by product+variant, then reload the authoritative cart.
      forkJoin(
        guest.map((i) =>
          this.api.addToCart(userId, {
            userId,
            productId: i.productId,
            variantId: i.variantId,
            quantity: i.quantity,
            deliveryInstructions: null,
          }).pipe(catchError(() => of(null)))
        )
      ).subscribe(() => finish());
    } else {
      finish();
    }

    this.loadProfile();
    this.loadOrders();
  }

  protected async forgot(): Promise<void> {
    if (!this.forgotEmail.trim()) return;
    try {
      await this.auth.forgotPassword(this.forgotEmail.trim());
      this.forgotMsg.set('If an account exists for this email, a reset link is on its way.');
    } catch { /* error surfaced via auth.error() */ }
  }

  protected signOut(): void {
    this.auth.signOut();
    this.store.cart.set([]);
    this.profile.set(null);
    this.addresses.set([]);
    this.apiOrders.set([]);
    this.tab.set('login');
  }

  // ── profile ────────────────────────────────────────────────────────────────

  private loadProfile(): void {
    const userId = this.auth.userId();
    if (!userId) return;
    this.profileLoading.set(true);
    this.api.getProfile(userId).pipe(catchError(() => of(null))).subscribe((p) => {
      this.profile.set(p);
      this.profileLoading.set(false);
      if (p) this.addresses.set(p.addresses);
    });
  }

  protected startEditProfile(p: ApiProfile): void {
    this.editFirstName = p.firstName;
    this.editLastName  = p.lastName;
    this.editEmail     = p.email;
    this.editingProfile.set(true);
  }

  protected saveProfile(): void {
    const userId = this.auth.userId();
    if (!userId) return;
    if (!this.editFirstName.trim() || !this.editLastName.trim() || !this.editEmail.trim()) return;
    this.api.updateProfile(userId, {
      firstName: this.editFirstName,
      lastName:  this.editLastName,
      email:     this.editEmail,
      latitude:  this.profile()?.latitude ?? null,
      longitude: this.profile()?.longitude ?? null
    }).pipe(catchError(() => of(null))).subscribe((p) => {
      if (p) {
        this.profile.set(p);
        this.profileSaveMsg.set('Profile updated.');
        setTimeout(() => this.profileSaveMsg.set(''), 2000);
      }
      this.editingProfile.set(false);
    });
  }

  // ── addresses ──────────────────────────────────────────────────────────────

  protected saveAddress(): void {
    const userId = this.auth.userId();
    if (!userId) return;
    if (!this.addrName.trim() || !this.addrStreet.trim()) {
      this.addrError.set('Name and address are required.');
      return;
    }
    const payload: AddressPayload = {
      name:          this.addrName.trim(),
      address:       this.addrStreet.trim(),
      latitude:      0,
      longitude:     0,
      houseNumber:   this.addrHouse || null,
      apartmentName: this.addrApartment.trim() || null,
      addressType:   this.addrType
    };
    this.api.addAddress(userId, payload).pipe(catchError((err) => {
      this.addrError.set(err?.error?.message ?? 'Failed to save address.');
      return of(null);
    })).subscribe((addr) => {
      if (addr) {
        this.addresses.update((list) => [...list, addr]);
        this.showAddressForm.set(false);
        this.resetAddressForm();
      }
    });
  }

  protected deleteAddress(addressId: number): void {
    const userId = this.auth.userId();
    if (!userId) return;
    this.api.deleteAddress(userId, addressId).pipe(catchError(() => of(null))).subscribe(() => {
      this.addresses.update((list) => list.filter((a) => a.id !== addressId));
    });
  }

  private resetAddressForm(): void {
    this.addrName = '';
    this.addrStreet = '';
    this.addrHouse = 0;
    this.addrApartment = '';
    this.addrType = 'HOME';
    this.addrError.set('');
  }

  // ── orders ─────────────────────────────────────────────────────────────────

  private loadOrders(): void {
    const userId = this.auth.userId();
    if (!userId) return;
    this.api.getOrders(userId).pipe(catchError(() => of([]))).subscribe((orders) => {
      this.apiOrders.set(orders.map((o) => ({
        id: o.id,
        createdAt: o.createdAt,
        orderStatus: o.orderStatus,
        totalAmount: o.totalAmount
      })));
    });
  }
}
