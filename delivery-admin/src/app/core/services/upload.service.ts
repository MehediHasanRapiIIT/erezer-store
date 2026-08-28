import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Generic admin image upload — returns the stored file's public URL. */
@Injectable({ providedIn: 'root' })
export class UploadService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  uploadImage(file: File): Observable<string> {
    const form = new FormData();
    form.append('file', file);
    return this.http
      .post<{ url: string }>(`${this.base}/admin/uploads/image`, form)
      .pipe(map((res) => res.url));
  }
}
