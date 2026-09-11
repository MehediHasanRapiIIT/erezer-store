import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './core/services/theme.service';
import { NoticeComponent } from './shared/notice/notice.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NoticeComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('delivery-admin');
  private readonly theme = inject(ThemeService);

  constructor() {
    this.theme.initializeTheme();
  }
}
