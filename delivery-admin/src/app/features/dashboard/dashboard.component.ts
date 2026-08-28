import { Component, signal, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardService, DashboardStats } from '../../core/services/dashboard.service';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { HomeResponse } from '../../core/models/api.models';
import { parseApiError } from '../../core/utils/api-error.util';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [SidebarComponent, RouterLink],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  private dashboardService = inject(DashboardService);

  homeData = signal<HomeResponse | null>(null);
  stats = signal<DashboardStats | null>(null);
  isLoading = signal(true);
  errorMessage = signal('');

  // SVG chart data
  readonly chartPoints = '40,120 120,70 200,95 280,80 360,30 440,60 520,90';
  readonly chartArea = '40,120 120,70 200,95 280,80 360,30 440,60 520,90 520,160 40,160';
  readonly chartDots = [{x:40,y:120},{x:120,y:70},{x:200,y:95},{x:280,y:80},{x:360,y:30},{x:440,y:60},{x:520,y:90}];
  readonly chartDays = [{x:40,l:'Mon'},{x:120,l:'Tue'},{x:200,l:'Wed'},{x:280,l:'Thu'},{x:360,l:'Fri'},{x:440,l:'Sat'},{x:520,l:'Sun'}];

  activeTab = signal<'Weekly' | 'Monthly'>('Weekly');
  setTab(tab: 'Weekly' | 'Monthly') { this.activeTab.set(tab); }

  ngOnInit(): void {
    this.dashboardService.getHomeData().subscribe({
      next: (data) => { this.homeData.set(data); this.isLoading.set(false); },
      error: (err) => { this.errorMessage.set(parseApiError(err)); this.isLoading.set(false); },
    });

    this.dashboardService.getStats().subscribe({
      next: (s) => this.stats.set(s),
      error: () => {}, // non-critical
    });
  }

  formatRevenue(amount: number): string {
    if (amount >= 1000000) return '৳ ' + (amount / 1000000).toFixed(1) + 'M';
    if (amount >= 1000) return '৳ ' + (amount / 1000).toFixed(0) + ',000';
    return '৳ ' + amount.toLocaleString();
  }
}
