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
   * Every category, for a picker: each main category followed by its
   * subcategories, which are labelled "Hoodies › Zip Hoodies".
   */
  getCategories(): Observable<CategoryResponse[]> {
    return this.http.get<CategoryResponse[]>(`${this.baseUrl}/api/categories`).pipe(map(asTree));
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
 * set in from the left with an arrow and names its main category, so it reads
 * right both in the open list (under its main category) and once chosen.
 */
function labelled(c: CategoryResponse): CategoryResponse {
  const indent = '\u00A0\u00A0\u00A0\u00A0';
  return { ...c, label: c.parentId != null && c.parentName ? `${indent}↳ ${c.parentName} › ${c.name}` : c.name };
}

/** Main categories by name, each followed by its subcategories by name. */
function asTree(all: CategoryResponse[]): CategoryResponse[] {
  const byName = (a: CategoryResponse, b: CategoryResponse) => a.name.localeCompare(b.name);
  const ids = new Set(all.map((c) => c.id));
  // A subcategory whose parent isn't in the list is shown on its own rather than lost.
  const mains = all.filter((c) => c.parentId == null || !ids.has(c.parentId)).sort(byName);
  const tree: CategoryResponse[] = [];
  for (const main of mains) {
    tree.push(main);
    tree.push(...all.filter((c) => c.parentId === main.id && c.id !== main.id).sort(byName));
  }
  return tree.map(labelled);
}
