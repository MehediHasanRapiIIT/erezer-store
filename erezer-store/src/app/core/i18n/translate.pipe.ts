import { inject, Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from './translate.service';

/**
 * Translate a key. Marked impure (default for signal-aware pipes) so it
 * re-runs whenever the language signal flips.
 */
@Pipe({
  name: 't',
  standalone: true,
  pure: false,
})
export class TranslatePipe implements PipeTransform {
  private readonly translate = inject(TranslateService);

  transform(key: string): string {
    // Reading the signal here ties the pipe to language changes.
    this.translate.lang();
    return this.translate.t(key);
  }
}
