import { Component, inject } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  template: '',
})
export class LoginComponent {
  constructor() {
    inject(AuthService).login();
  }
}
