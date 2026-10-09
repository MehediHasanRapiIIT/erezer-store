import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CategoryRequest, CategoryResponse, PageResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class CategoryService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  /** One page of the admin Categories page; the server searches name and web address. */
  getCategoriesPage(q: string, page: number, size: number): Observable<PageResponse<CategoryResponse>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (q) params['q'] = q;
    return this.http.get<PageResponse<CategoryResponse>>(`${this.baseUrl}/admin/categories`, { params })
      .pipe(map((p) => ({ ...p, content: p.content.map(labelled) })));
  }

  /**
   * Every category, for a picker. The server sends them as a tree read top to
   * bottom - each category followed at once by everything under it - and a
   * subcategory is labelled with the whole way down: "Men › T-Shirts › Drop Shoulder".
   */
  getCategories(): Observable<CategoryResponse[]> {
    return this.http.get<CategoryResponse[]>(`${this.baseUrl}/api/categories`).pipe(map((all) => all.map(labelled)));
  }

  getCategory(id: number): Observable<CategoryResponse> {
    return this.http.get<CategoryResponse>(`${this.baseUrl}/api/categories/${id}`).pipe(map(labelled));
  }

  createCategory(dto: CategoryRequest): Observable<CategoryResponse> {
    return this.http.post<CategoryResponse>(`${this.baseUrl}/api/categories`, dto);
  }

  updateCategory(id: number, dto: CategoryRequest): Observable<CategoryResponse> {
    return this.http.put<CategoryResponse>(`${this.baseUrl}/api/categories/${id}`, dto);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/categories/${id}`);
  }
}

/**
 * How a category reads in a list. A main category is its name. A subcategory is
 * set in from the left, further for each level down, with an arrow and the
 * whole way down to it, so it reads right both in the open list (under its
 * parent) and once chosen.
 */
function labelled(c: CategoryResponse): CategoryResponse {
  const depth = c.depth ?? (c.parentId != null ? 1 : 0);
  if (depth === 0) return { ...c, label: c.name };
  const path = c.path || (c.parentName ? `${c.parentName} › ${c.name}` : c.name);
  return { ...c, label: `${'\u00A0\u00A0\u00A0\u00A0'.repeat(depth)}↳ ${path}` };
}
