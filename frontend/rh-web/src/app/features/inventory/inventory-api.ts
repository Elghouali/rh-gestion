import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { defer, switchMap, tap, catchError, throwError, Observable } from 'rxjs';
@Injectable({ providedIn: 'root' })
export class InventoryApi {
  private readonly http = inject(HttpClient);
  command<T>(method: 'POST' | 'PUT', path: string, payload: unknown): Observable<T> {
    return defer(async () => {
      const encoded = new TextEncoder().encode(method + '|' + path + '|' + JSON.stringify(payload));
      const digest = await crypto.subtle.digest('SHA-256', encoded);
      const fingerprint = Array.from(new Uint8Array(digest))
        .map((b) => b.toString(16).padStart(2, '0'))
        .join('');
      const slot = 'rh-command-' + fingerprint;
      let key = sessionStorage.getItem(slot);
      if (!key) {
        key = crypto.randomUUID();
        sessionStorage.setItem(slot, key);
      }
      return { slot, key };
    }).pipe(
      switchMap(({ slot, key }) =>
        this.http
          .request<T>(method, path, { body: payload, headers: { 'Idempotency-Key': key } })
          .pipe(
            tap(() => sessionStorage.removeItem(slot)),
            catchError((error: HttpErrorResponse) => {
              if (error.status >= 400 && error.status < 500) sessionStorage.removeItem(slot);
              return throwError(() => error);
            }),
          ),
      ),
    );
  }
}
