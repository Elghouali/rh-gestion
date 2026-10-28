import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { InventoryComponent } from './inventory';
import { LanguageService } from '../../core/language.service';
describe('Catalogue and branch stock', () => {
  let http: HttpTestingController;
  const site = { id: 'depot', code: 'DEPOT', name: 'Dépôt central', type: 'DEPOT' };
  const variant = {
    id: 'v1',
    productId: 'p1',
    productName: 'Phone Test',
    brand: 'RH',
    category: 'PHONE',
    sku: 'PHONE-BLACK',
    color: 'Noir',
    ramGb: 8,
    storageGb: 256,
    wholesalePrice: 100,
    retailPrice: 120,
    minimumStock: 2,
    active: true,
    version: 0,
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [InventoryComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(LanguageService).set('fr');
  });
  afterEach(() => http.verify());
  function render(owner = true) {
    const fixture = TestBed.createComponent(InventoryComponent);
    fixture.componentRef.setInput('branches', [site]);
    fixture.componentRef.setInput('owner', owner);
    fixture.componentRef.setInput('manager', owner);
    fixture.detectChanges();
    return fixture;
  }
  function flush(initialized = true) {
    for (const req of http.match((r) => r.method === 'GET')) {
      if (req.request.url.endsWith('/destinations')) req.flush([site]);
      else if (req.request.url.endsWith('/products'))
        req.flush({
          items: [
            {
              id: 'p1',
              name: 'Phone Test',
              brand: 'RH',
              category: 'PHONE',
              active: true,
              version: 0,
            },
          ],
          page: 0,
          size: 100,
          total: 1,
        });
      else if (req.request.url.endsWith('/variants'))
        req.flush({ items: [variant], page: 0, size: 20, total: 1 });
      else if (req.request.url.endsWith('/balances'))
        req.flush({
          items: [
            {
              branchId: 'depot',
              variant,
              quantity: 0,
              inTransit: 0,
              incoming: 0,
              initialized,
              lowStock: initialized,
            },
          ],
          page: 0,
          size: 20,
          total: 1,
        });
      else req.flush({ items: [], page: 0, size: 20, total: 0 });
    }
  }
  it('renders distinct RAM/storage and owner catalogue actions', () => {
    const f = render();
    flush();
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('PHONE-BLACK');
    expect(f.nativeElement.textContent).toContain('256');
    expect(f.nativeElement.querySelector('[data-testid="new-product"]')).not.toBeNull();
  });
  it('keeps cashier views read-only', () => {
    const f = render(false);
    flush();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[data-testid="new-product"]')).toBeNull();
    expect(f.nativeElement.querySelector('[data-testid="stock-write"]')).toBeNull();
  });
  it('does not present uncounted stock as a confirmed zero', () => {
    const f = render();
    flush(false);
    f.componentInstance.setTab('stock');
    flush(false);
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Non initialisé');
  });
  it('blocks invalid product creation before any mutation', () => {
    const f = render();
    flush();
    f.componentInstance.openProduct();
    f.componentInstance.saveProduct();
    http.expectNone((r) => r.method === 'POST');
    expect(f.componentInstance.productForm.invalid).toBe(true);
  });
  it('shows an unavailable-data state rather than stale stock', () => {
    const f = render();
    const requests = http.match((r) => r.method === 'GET');
    for (const req of requests.filter((r) => !r.request.url.endsWith('/balances'))) {
      req.flush(
        req.request.url.endsWith('/destinations') ? [] : { items: [], page: 0, size: 20, total: 0 },
      );
    }
    requests
      .find((r) => r.request.url.endsWith('/balances'))!
      .flush('Down', { status: 503, statusText: 'Unavailable' });
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
  it('switches to Arabic without changing the variant identity', () => {
    const f = render();
    flush();
    TestBed.inject(LanguageService).set('ar');
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('PHONE-BLACK');
    expect(f.nativeElement.textContent).toContain('المخزون');
  });
});
