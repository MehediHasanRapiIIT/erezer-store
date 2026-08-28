import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CustomDesignColorAdmin {
  id?: string | null;
  name: string;
  hex: string;
  frontImageUrl?: string | null;
  backImageUrl?: string | null;
  leftSleeveImageUrl?: string | null;
  rightSleeveImageUrl?: string | null;
  sortOrder?: number | null;
}

export interface CustomDesignItemAdmin {
  id?: string | null;
  name: string;
  category?: string | null;
  sortOrder?: number | null;
  active?: boolean | null;
  sizes: string[];
  printTechniques: string[];
  colors: CustomDesignColorAdmin[];
}

export interface CustomDesignLogoAdmin {
  id?: string | null;
  name: string;
  url: string;
  sortOrder?: number | null;
  active?: boolean | null;
}

/** Admin management of custom-design studio assets: garments + logo library. */
@Injectable({ providedIn: 'root' })
export class CustomDesignAssetService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  // Garments
  listItems(): Observable<CustomDesignItemAdmin[]> {
    return this.http.get<CustomDesignItemAdmin[]>(`${this.base}/admin/custom-design/items`);
  }

  upsertItem(item: CustomDesignItemAdmin): Observable<CustomDesignItemAdmin> {
    return this.http.post<CustomDesignItemAdmin>(`${this.base}/admin/custom-design/items`, item);
  }

  deleteItem(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/custom-design/items/${id}`);
  }

  // Logo library
  listLogos(): Observable<CustomDesignLogoAdmin[]> {
    return this.http.get<CustomDesignLogoAdmin[]>(`${this.base}/admin/custom-design/logos`);
  }

  upsertLogo(logo: CustomDesignLogoAdmin): Observable<CustomDesignLogoAdmin> {
    return this.http.post<CustomDesignLogoAdmin>(`${this.base}/admin/custom-design/logos`, logo);
  }

  deleteLogo(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/custom-design/logos/${id}`);
  }

  /** Upload a garment mockup or logo image; returns its stored URL. */
  uploadImage(file: File): Observable<string> {
    const form = new FormData();
    form.append('file', file);
    return this.http
      .post<{ url: string }>(`${this.base}/admin/custom-design/uploads/image`, form)
      .pipe(map((res) => res.url));
  }
}
