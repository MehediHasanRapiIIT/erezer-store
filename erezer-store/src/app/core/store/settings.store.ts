import { inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { ApiService } from '../api.service';
import { ApiStoreSettings } from '../api.models';

/**
 * Loads the admin-managed store settings (return policy, support, size chart,
 * brand story, footer) once and shares them across the app. The footer and the
 * home "Our story" band read from here so the admin can change them without a
 * code change.
 */
@Injectable({ providedIn: 'root' })
export class SettingsStore {
  private readonly api = inject(ApiService);

  readonly settings = signal<ApiStoreSettings | null>(null);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.api.getStoreSettings()
      .pipe(catchError(() => of(null)))
      .subscribe((s) => { if (s) this.settings.set(s); });
  }
}
