import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type StaffRole = 'ADMIN' | 'MODERATOR';

/** One person who can log in to the admin panel, from GET /admin/staff. */
export interface StaffMember {
  id: string;
  username: string;
  email: string | null;
  fullName: string | null;
  name: string;
  role: StaffRole;
  active: boolean;
  /** Dhaka local time, 'yyyy-MM-dd HH:mm'; null when they have never been seen. */
  lastSeenAt: string | null;
  /** Dhaka local time, 'yyyy-MM-dd HH:mm'. */
  createdAt: string | null;
  createdBy: string | null;
  /** What a moderator was given; always empty for an admin, who can do everything. */
  permissions: string[];
  /** True on the row of the person looking at the page. */
  you: boolean;
}

export interface AddStaffRequest {
  fullName: string;
  username: string;
  email: string | null;
  temporaryPassword: string;
  role?: StaffRole;
  permissions?: string[];
}

export interface StaffDetailsRequest {
  fullName: string;
  email: string | null;
}

/** A named set of ticks that can be applied to anyone's checklist. */
export interface PermissionTemplate {
  id: string;
  name: string;
  permissions: string[];
  createdBy: string | null;
  updatedAt: string | null;
}

export interface PermissionTemplateRequest {
  name: string;
  permissions: string[];
}

@Injectable({ providedIn: 'root' })
export class StaffService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  list(): Observable<StaffMember[]> {
    return this.http.get<StaffMember[]>(`${this.base}/admin/staff`);
  }

  add(payload: AddStaffRequest): Observable<StaffMember> {
    return this.http.post<StaffMember>(`${this.base}/admin/staff`, payload);
  }

  updateDetails(id: string, payload: StaffDetailsRequest): Observable<StaffMember> {
    return this.http.put<StaffMember>(`${this.base}/admin/staff/${id}`, payload);
  }

  /** Admins only. Clears the person's permissions. */
  changeRole(id: string, role: StaffRole): Observable<StaffMember> {
    return this.http.put<StaffMember>(`${this.base}/admin/staff/${id}/role`, { role });
  }

  /** Replaces everything the person was given with exactly these keys. */
  setPermissions(id: string, permissions: string[]): Observable<StaffMember> {
    return this.http.put<StaffMember>(`${this.base}/admin/staff/${id}/permissions`, { permissions });
  }

  /** Also signs them out everywhere. */
  deactivate(id: string): Observable<StaffMember> {
    return this.http.post<StaffMember>(`${this.base}/admin/staff/${id}/deactivate`, {});
  }

  reactivate(id: string): Observable<StaffMember> {
    return this.http.post<StaffMember>(`${this.base}/admin/staff/${id}/reactivate`, {});
  }

  /** Also signs them out everywhere. */
  resetPassword(id: string, temporaryPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/staff/${id}/reset-password`, { temporaryPassword });
  }

  /** Refused by the server unless `username` is exactly theirs. */
  delete(id: string, username: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/staff/${id}`, { params: { confirm: username } });
  }

  // ── permission templates ────────────────────────────────────────────────

  templates(): Observable<PermissionTemplate[]> {
    return this.http.get<PermissionTemplate[]>(`${this.base}/admin/permission-templates`);
  }

  createTemplate(payload: PermissionTemplateRequest): Observable<PermissionTemplate> {
    return this.http.post<PermissionTemplate>(`${this.base}/admin/permission-templates`, payload);
  }

  updateTemplate(id: string, payload: PermissionTemplateRequest): Observable<PermissionTemplate> {
    return this.http.put<PermissionTemplate>(`${this.base}/admin/permission-templates/${id}`, payload);
  }

  deleteTemplate(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/permission-templates/${id}`);
  }
}
