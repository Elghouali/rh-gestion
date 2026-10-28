import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { InventoryApi } from './inventory-api';
describe('Safe inventory command retries', () => {
  let http: HttpTestingController;
  let api: InventoryApi;
  beforeEach(() => {
    sessionStorage.clear();
    let serial = 0;
    vi.stubGlobal('crypto', {
      randomUUID: () => '00000000-0000-0000-0000-' + String(++serial).padStart(12, '0'),
      subtle: { digest: async () => new Uint8Array([1, 2, 3, 4]).buffer },
    });
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), InventoryApi],
    });
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(InventoryApi);
  });
  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
    sessionStorage.clear();
  });
  it('reuses a command key after an unknown server result and rotates after success', async () => {
    const path = '/api/v1/stock/adjustments',
      payload = { delta: 1 };
    api.command('POST', path, payload).subscribe({ error: () => {} });
    let first: any;
    await vi.waitFor(() => {
      first = http.expectOne(path);
    });
    const key = first.request.headers.get('Idempotency-Key');
    expect(key).toBeTruthy();
    first.flush('Down', { status: 503, statusText: 'Unavailable' });
    api.command('POST', path, payload).subscribe();
    let retry: any;
    await vi.waitFor(() => {
      retry = http.expectOne(path);
    });
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush({ id: 'posted' });
    api.command('POST', path, payload).subscribe();
    let next: any;
    await vi.waitFor(() => {
      next = http.expectOne(path);
    });
    expect(next.request.headers.get('Idempotency-Key')).not.toBe(key);
    next.flush({ id: 'new' });
  });
});
