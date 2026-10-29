import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { InventoryComponent } from './inventory';
import { LanguageService } from '../../core/language.service';
import { Variant, Transfer } from './inventory-models';
describe('Inventory form and journal views', () => {
  const site = { id: 'depot', code: 'DEPOT', name: 'Dépôt', type: 'DEPOT' };
  const v: Variant = {
    id: 'v1',
    productId: 'p1',
    productName: 'Phone',
    brand: 'RH',
    category: 'PHONE',
    sku: 'RH-8-128',
    color: 'Noir',
    ramGb: 8,
    storageGb: 128,
    wholesalePrice: 100,
    retailPrice: 120,
    minimumStock: 2,
    active: true,
    version: 1,
  };
  const transfer: Transfer = {
    id: 't1',
    sourceId: 'depot',
    destinationId: 'shop',
    sourceName: 'Dépôt',
    destinationName: 'M01',
    status: 'PARTIAL',
    createdAt: '2026-10-29T09:00:00Z',
    items: [
      {
        variantId: 'v1',
        sku: v.sku,
        label: 'Phone Noir',
        quantity: 4,
        received: 1,
        lost: 0,
        remaining: 3,
      },
    ],
  };
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [InventoryComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(LanguageService).set('fr');
  });
  afterEach(() => http.verify());
  function flush() {
    for (const r of http.match((r) => r.method === 'GET')) {
      const url = r.request.url;
      const items = url.endsWith('/variants')
        ? [v]
        : url.endsWith('/products')
          ? [{ id: 'p1', name: 'Phone', brand: 'RH', category: 'PHONE', active: true, version: 1 }]
          : url.endsWith('/balances')
            ? [
                {
                  branchId: 'depot',
                  variant: v,
                  quantity: 1,
                  inTransit: 2,
                  incoming: 0,
                  initialized: true,
                  lowStock: true,
                },
              ]
            : url.endsWith('/transfers')
              ? [transfer]
              : [
                  {
                    id: 'm1',
                    entryId: 'e1',
                    kind: 'OPENING',
                    reason: 'Count',
                    actor: 'owner',
                    createdAt: '2026-10-29T09:00:00Z',
                    branchId: 'depot',
                    variantId: 'v1',
                    sku: v.sku,
                    label: 'Phone Noir',
                    bucket: 'SELLABLE',
                    delta: 1,
                    quantityAfter: 1,
                    unitCost: 70,
                  },
                ];
      r.flush(
        url.endsWith('/destinations')
          ? [site, { ...site, id: 'shop', name: 'M01' }]
          : { items, page: 0, size: 20, total: 1 },
      );
    }
  }
  function setup(owner = true) {
    const f = TestBed.createComponent(InventoryComponent);
    f.componentRef.setInput('branches', [site]);
    f.componentRef.setInput('owner', owner);
    f.componentRef.setInput('manager', owner);
    f.detectChanges();
    flush();
    f.detectChanges();
    return f;
  }
  it('renders new and existing model and variant forms with saved values', () => {
    const f = setup(),
      c = f.componentInstance;
    c.openProduct();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[formControlName="name"]')).not.toBeNull();
    c.openProduct(c.products()[0]);
    f.detectChanges();
    expect(f.nativeElement.querySelector('input[type=checkbox]')).not.toBeNull();
    c.openVariant();
    f.detectChanges();
    expect(f.nativeElement.querySelector('select[formControlName="productId"]')).not.toBeNull();
    c.openVariant(v);
    f.detectChanges();
    expect(f.nativeElement.querySelector('[formControlName="sku"]').value).toBe(v.sku);
    c.formError.set('identityLocked');
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role=alert]')).not.toBeNull();
  });
  it('renders counted stock and both stock entry forms', () => {
    const f = setup(),
      c = f.componentInstance;
    c.setTab('stock');
    flush();
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('RH-8-128');
    expect(f.nativeElement.querySelector('[data-testid="stock-write"]')).not.toBeNull();
    c.openStock('opening', c.balances().items[0]);
    flush();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[formControlName="unitCost"]')).not.toBeNull();
    c.openStock('adjustment', c.balances().items[0]);
    flush();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[formControlName="delta"]')).not.toBeNull();
    c.close();
    f.detectChanges();
    expect(f.nativeElement.querySelector('.editor')).toBeNull();
  });
  it('renders transfer lines and partial-receipt or loss form', () => {
    const f = setup(),
      c = f.componentInstance;
    c.setTab('transfers');
    flush();
    f.detectChanges();
    expect(f.nativeElement.querySelector('.transfer-card')).not.toBeNull();
    expect(f.nativeElement.textContent).toContain(c.t('resolveLoss'));
    expect(f.nativeElement.textContent).toContain(c.t('PARTIAL'));
    c.openTransfer();
    flush();
    c.addTransferLine();
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('[formControlName="quantity"]').length).toBe(2);
    c.openReceipt(transfer);
    f.detectChanges();
    expect(f.nativeElement.querySelector('[formControlName="quantity"]').value).toBe('3');
    c.openReceipt(transfer, true);
    f.detectChanges();
    expect(f.nativeElement.querySelector('.editor').textContent).toContain(c.t('resolveLoss'));
    expect(c.lineLabel('v1')).toBe('Phone Noir');
  });
  it('renders owner journal with costs and cashier journal without a cost column', () => {
    const f = setup(),
      c = f.componentInstance;
    c.showHistory();
    flush();
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Count');
    expect(f.nativeElement.querySelector('table').textContent).toContain('70');
    f.componentRef.setInput('owner', false);
    f.detectChanges();
    expect(f.nativeElement.querySelector('table').textContent).not.toContain('70');
    TestBed.inject(LanguageService).set('ar');
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('RH-8-128');
  });
  it('offers independent model and variant page loading and retries after search failure', () => {
    const f = setup(),
      c = f.componentInstance;
    c.openVariant();
    c.modelTotal.set(200);
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain(c.t('moreModels'));
    c.searchModels(true);
    flush();
    c.openTransfer();
    flush();
    c.pickerTotal.set(200);
    f.detectChanges();
    c.searchVariants(true);
    flush();
    c.variantSearch.setValue('Missing');
    c.searchVariants();
    http
      .expectOne((r) => r.url.endsWith('/variants'))
      .flush('Unavailable', { status: 503, statusText: 'Unavailable' });
    expect(c.formError()).toBe('unavailable');
    c.close();
    c.paginate(1);
    flush();
    f.detectChanges();
    expect(c.page()).toBe(1);
    c.apply();
    flush();
    expect(c.page()).toBe(0);
  });
});
