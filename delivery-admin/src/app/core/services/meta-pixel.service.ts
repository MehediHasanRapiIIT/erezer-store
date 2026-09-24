import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Meta Pixel settings as the admin panel sees them; the token is never sent back. */
export interface MetaPixelSettings {
  enabled: boolean;
  pixelId: string | null;
  /** True when an access token is saved on the server. */
  tokenSaved: boolean;
  /** Its last few characters, e.g. "…a91F". */
  tokenHint: string | null;
  testEventCode: string | null;
  /** "admin", "server file" or "not set". */
  source: string;
  /** True when sales are also reported from the server. */
  serverReporting: boolean;
}

export interface MetaPixelChange {
  enabled?: boolean;
  pixelId?: string;
  /** A new token; leave out to keep the saved one. */
  accessToken?: string;
  removeToken?: boolean;
  testEventCode?: string;
}

@Injectable({ providedIn: 'root' })
export class MetaPixelService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  get(): Observable<MetaPixelSettings> {
    return this.http.get<MetaPixelSettings>(`${this.base}/admin/meta-pixel`);
  }

  update(change: MetaPixelChange): Observable<MetaPixelSettings> {
    return this.http.put<MetaPixelSettings>(`${this.base}/admin/meta-pixel`, change);
  }

  /** Asks Meta to accept one event, to prove the settings work. */
  sendTestEvent(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.base}/admin/meta-pixel/test-event`, {});
  }
}
