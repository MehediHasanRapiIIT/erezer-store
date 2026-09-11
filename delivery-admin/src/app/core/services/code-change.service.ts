import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** What every ticked product gets: the same code, or a prefix with numbers. */
export type CodeMode = 'SAME' | 'NUMBERED';

/**
 * `code` is the code itself for SAME ("EP-1001" for every product), or the
 * prefix for NUMBERED ("EP" → EP-001, EP-002…). `excludedProductIds` are the
 * products left alone; they keep the codes they have.
 */
export interface CodeChangeRequest {
  categoryId: number;
  mode: CodeMode;
  code: string;
  excludedProductIds: number[];
}

export interface CodeChangeRow {
  productId: number;
  name: string;
  imageUrl: string | null;
  oldCode: string;
  newCode: string;
  changed: boolean;
  problem: string | null;
}

/** The counts cover the whole category; `rows` is one page of the products matching the search. */
export interface CodeChangePreview {
  categoryId: number;
  categoryName: string;
  productCount: number;
  changedCount: number;
  problemCount: number;
  page: number;
  size: number;
  totalRows: number;
  totalPages: number;
  rows: CodeChangeRow[];
}

/** Giving a whole category its product codes (PRODUCT-CODE-PLAN.md, part 2). */
@Injectable({ providedIn: 'root' })
export class CodeChangeService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/admin/products/code-change`;

  /** One page of what the change would do; `q` searches product name and code. Saves nothing. */
  preview(r: CodeChangeRequest, q: string, page: number, size: number): Observable<CodeChangePreview> {
    let params = new HttpParams()
      .set('categoryId', r.categoryId)
      .set('mode', r.mode)
      .set('code', r.code)
      .set('page', page)
      .set('size', size);
    if (q) params = params.set('q', q);
    // With numbers, the numbering depends on which products are left alone.
    for (const id of r.excludedProductIds) params = params.append('excluded', id);
    return this.http.get<CodeChangePreview>(`${this.base}/preview`, { params });
  }

  /** Gives the ticked products their new codes, all together or not at all. */
  apply(r: CodeChangeRequest): Observable<CodeChangePreview> {
    return this.http.post<CodeChangePreview>(this.base, r);
  }
}
