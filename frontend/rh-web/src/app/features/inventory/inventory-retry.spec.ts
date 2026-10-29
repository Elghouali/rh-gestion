import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
  TestRequest,
} from '@angular/common/http/testing';
import { vi } from 'vitest';
import { InventoryApi } from './inventory-api';
describe('Inventory command error recovery', () => {
  let http: HttpTestingController;
  let api: InventoryApi;
  let serial: number;
  const path = '/api/v1/stock/openings',
    body = { branchId: 'depot', variantId: 'v1', quantity: 0, reason: 'Physical count' };
  beforeEach(() => {
    sessionStorage.clear();
    serial = 0;
    vi.stubGlobal('crypto', {
      randomUUID: () => 'command-key-' + ++serial,
      subtle: {
        digest: async (_algorithm: string, data: Uint8Array) => {
          const hash = new Uint8Array(32);
          data.forEach((byte, index) => {
            hash[index % 32] = (hash[index % 32] * 31 + byte) % 256;
          });
          return hash.buffer;
        },
      },
    });
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(InventoryApi);
  });
  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
    sessionStorage.clear();
  });
  async function send(requestBody = body, requestPath = path) {
    api.command('POST', requestPath, requestBody).subscribe({ error: () => {} });
    let request!: TestRequest;
    await vi.waitFor(() => {
      request = http.expectOne(requestPath);
    });
    return request;
  }
  it('retains the same key after a network failure and stores no business payload', async () => {
    const first = await send(),
      key = first.request.headers.get('Idempotency-Key');
    expect(sessionStorage.length).toBe(1);
    expect(JSON.stringify(Object.entries(sessionStorage))).not.toContain('Physical count');
    first.error(new ProgressEvent('error'));
    const retry = await send();
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush({ id: 'recorded' });
    expect(sessionStorage.length).toBe(0);
  });
  it('rotates the key after a definitive conflict instead of replaying a rejected command', async () => {
    const first = await send(),
      key = first.request.headers.get('Idempotency-Key');
    first.flush({ code: 'OPENING_ALREADY_RECORDED' }, { status: 409, statusText: 'Conflict' });
    expect(sessionStorage.length).toBe(0);
    const next = await send();
    expect(next.request.headers.get('Idempotency-Key')).not.toBe(key);
    next.flush({ id: 'recorded' });
  });
  it('retains an uncertain key across service recreation within the tab', async () => {
    const first = await send(),
      key = first.request.headers.get('Idempotency-Key');
    first.flush('Down', { status: 500, statusText: 'Server Error' });
    api = TestBed.runInInjectionContext(() => new InventoryApi());
    const retry = await send();
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush({ id: 'recorded' });
  });
  it('keeps changed payloads and paths separate from an uncertain original command', async () => {
    const first = await send();
    const originalKey = first.request.headers.get('Idempotency-Key');
    first.flush('Down', { status: 503, statusText: 'Unavailable' });
    const changed = await send({ ...body, quantity: 1 });
    expect(changed.request.headers.get('Idempotency-Key')).not.toBe(originalKey);
    changed.flush({ id: 'different-count' });
    const otherPath = await send(body, '/api/v1/stock/adjustments');
    expect(otherPath.request.headers.get('Idempotency-Key')).not.toBe(originalKey);
    otherPath.flush({ id: 'other-operation' });
    const retry = await send();
    expect(retry.request.headers.get('Idempotency-Key')).toBe(originalKey);
    retry.flush({ id: 'original-count' });
  });
});
