import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { PixelService } from './pixel.service';

/**
 * Passes Meta's browser cookies to our own API so the backend's Conversions
 * API copy of a purchase can be matched to the same shopper and the same ad
 * click as the browser pixel's copy. Without these the server event still
 * counts, but Meta cannot tie it to the ad.
 *
 * The cookies live on the storefront's domain and the API is on another, so
 * the browser would never send them on its own.
 */
export const metaInterceptor: HttpInterceptorFn = (req, next) => {
  const pixel = inject(PixelService);
  const fbp = pixel.fbp();
  const fbc = pixel.fbc();
  if (!fbp && !fbc) return next(req);

  const headers: Record<string, string> = {};
  if (fbp) headers['X-Meta-Fbp'] = fbp;
  if (fbc) headers['X-Meta-Fbc'] = fbc;
  return next(req.clone({ setHeaders: headers }));
};
