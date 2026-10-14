import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** The Settings page's "Customer emails" section. Empty text means "not shown". */
export interface CustomerEmailSettings {
  reviewOnWebsite: boolean;
  facebookUrl: string;
  instagramUrl: string;
  whatsappNumber: string;
  businessAddress: string;
  cartReminderEnabled: boolean;
  newsletterEnabled: boolean;
}

@Injectable({ providedIn: 'root' })
export class CustomerEmailsService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  get(): Observable<CustomerEmailSettings> {
    return this.http.get<CustomerEmailSettings>(`${this.baseUrl}/admin/customer-emails`);
  }

  save(settings: CustomerEmailSettings): Observable<CustomerEmailSettings> {
    return this.http.put<CustomerEmailSettings>(`${this.baseUrl}/admin/customer-emails`, settings);
  }
}
