import { TestBed } from '@angular/core/testing';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { of, throwError, Subject } from 'rxjs';
import { vi } from 'vitest';
import { InventoryComponent } from './inventory';
import { InventoryApi } from './inventory-api';
import { LanguageService } from '../../core/language.service';
import { Product, Variant, Transfer, StockRow } from './inventory-models';

describe('Inventory operator commands', () => {
  const site = { id: 'depot', code: 'DEPOT', name: 'Dépôt', type: 'DEPOT' };
  const product: Product = {
    id: 'p1',
    name: 'Phone',
    brand: 'RH',
    category: 'PHONE',
    active: true,
    version: 3,
  };
  const variant: Variant = {
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
    version: 2,
  };
  const transfer: Transfer = {
    id: 't1',
    sourceId: 'depot',
    destinationId: 'shop',
    sourceName: 'Dépôt',
    destinationName: 'Shop',
    status: 'PARTIAL',
    createdAt: '2026-10-29T09:00:00Z',
    items: [
      {
        variantId: 'v1',
        sku: 'RH-8-128',
        label: 'Phone Noir',
        quantity: 4,
        received: 1,
        lost: 0,
        remaining: 3,
      },
    ],
  };
  let http: HttpTestingController;
  const command = vi.fn();
  beforeEach(() => {
    command.mockReset().mockReturnValue(of({ id: 'done' }));
    TestBed.configureTestingModule({
      imports: [InventoryComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: InventoryApi, useValue: { command } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(LanguageService).set('fr');
  });
  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });
  function setup(owner = true, manager = true) {
    const f = TestBed.createComponent(InventoryComponent);
    f.componentRef.setInput('branches', [site]);
    f.componentRef.setInput('owner', owner);
    f.componentRef.setInput('manager', manager);
    f.detectChanges();
    flush();
    f.componentInstance.products.set([product]);
    f.componentInstance.variants.set({ items: [variant], page: 0, size: 20, total: 1 });
    return f;
  }
  function flush() {
    for (const r of http.match((r) => r.method === 'GET'))
      r.flush(
        r.request.url.endsWith('/destinations')
          ? [site]
          : { items: [], page: 0, size: 20, total: 0 },
      );
  }
  it('creates and edits a model with optimistic version and trimmed names', () => {
    const f = setup(),
      c = f.componentInstance;
    c.openProduct();
    c.productForm.patchValue({ name: ' Phone ', brand: ' RH ' });
    c.saveProduct();
    expect(command).toHaveBeenLastCalledWith('POST', '/api/v1/catalog/products', {
      name: 'Phone',
      brand: 'RH',
      category: 'PHONE',
    });
    flush();
    c.openProduct(product);
    c.productForm.patchValue({ name: 'Renamed', active: false });
    c.saveProduct();
    expect(command).toHaveBeenLastCalledWith('PUT', '/api/v1/catalog/products/p1', {
      name: 'Renamed',
      brand: 'RH',
      active: false,
      version: 3,
    });
    flush();
  });
  it('requires both phone attributes and converts decimal comma prices', () => {
    const c = setup().componentInstance;
    c.openVariant();
    c.variantForm.patchValue({
      productId: 'p1',
      sku: 'RH-8-128',
      color: 'Noir',
      wholesalePrice: '100,50',
      retailPrice: '120,00',
    });
    c.saveVariant();
    expect(command).not.toHaveBeenCalled();
    expect(c.formError()).toBe('invalidForm');
    c.variantForm.patchValue({ ramGb: 8, storageGb: 128 });
    c.saveVariant();
    expect(command.mock.calls[0][2]).toMatchObject({
      wholesalePrice: '100.50',
      retailPrice: '120.00',
      ramGb: 8,
      storageGb: 128,
    });
    flush();
  });
  it('edits price and minimum threshold with the current variant version', () => {
    const c = setup().componentInstance;
    c.openVariant(variant);
    c.variantForm.patchValue({ retailPrice: '150.00', minimumStock: 5 });
    c.saveVariant();
    expect(command).toHaveBeenCalledWith(
      'PUT',
      '/api/v1/catalog/variants/v1',
      expect.objectContaining({ version: 2, retailPrice: '150.00', minimumStock: 5 }),
    );
    flush();
  });
  it('supports explicitly counted zero with unknown cost', () => {
    const c = setup().componentInstance;
    c.openStock('opening');
    flush();
    c.stockForm.patchValue({
      branchId: 'depot',
      variantId: 'v1',
      quantity: 0,
      reason: 'Counted empty',
    });
    c.saveStock();
    expect(command).toHaveBeenCalledWith('POST', '/api/v1/stock/openings', {
      branchId: 'depot',
      variantId: 'v1',
      quantity: 0,
      unitCost: null,
      reason: 'Counted empty',
    });
    flush();
  });
  it('keeps four decimal places for a known opening cost', () => {
    const c = setup().componentInstance;
    c.openStock('opening');
    flush();
    c.stockForm.patchValue({
      branchId: 'depot',
      variantId: 'v1',
      quantity: 1,
      unitCost: '70,1250',
      reason: 'Count',
    });
    c.saveStock();
    expect(command.mock.calls[0][2]).toMatchObject({ unitCost: '70.1250' });
    flush();
  });
  it('rejects fractional quantities and zero adjustments, then accepts a signed correction', () => {
    const c = setup().componentInstance;
    c.openStock('adjustment');
    flush();
    c.stockForm.patchValue({ branchId: 'depot', variantId: 'v1', delta: 1.5, reason: 'Count' });
    c.saveStock();
    expect(command).not.toHaveBeenCalled();
    c.stockForm.patchValue({ delta: 0 });
    c.saveStock();
    expect(command).not.toHaveBeenCalled();
    c.stockForm.patchValue({ delta: -2 });
    c.saveStock();
    expect(command.mock.calls[0][2]).toEqual({
      branchId: 'depot',
      variantId: 'v1',
      delta: -2,
      reason: 'Count',
    });
    flush();
  });
  it('does not dispatch to the same site and sends all valid lines together', () => {
    const c = setup().componentInstance;
    c.openTransfer();
    flush();
    c.transferForm.patchValue({
      sourceId: 'depot',
      destinationId: 'depot',
      reason: 'Move',
      items: [{ variantId: 'v1', quantity: 2 }],
    });
    c.sendTransfer();
    expect(command).not.toHaveBeenCalled();
    c.transferForm.controls.destinationId.setValue('shop');
    c.addTransferLine();
    c.transferForm.controls.items.at(1).patchValue({ variantId: 'v2', quantity: 1 });
    c.sendTransfer();
    expect(command.mock.calls[0][2].items).toEqual([
      { variantId: 'v1', quantity: 2 },
      { variantId: 'v2', quantity: 1 },
    ]);
    flush();
  });
  it('limits receipt to remaining quantity and supports partial receipt', () => {
    const c = setup().componentInstance;
    c.openReceipt(transfer);
    c.receiptForm.controls.reason.setValue('Counted');
    c.receiptForm.controls.items.at(0).controls.quantity.setValue(4);
    c.saveReceipt();
    expect(command).not.toHaveBeenCalled();
    c.receiptForm.controls.items.at(0).controls.quantity.setValue(2);
    c.saveReceipt();
    expect(command).toHaveBeenCalledWith('POST', '/api/v1/stock/transfers/t1/receipts', {
      reason: 'Counted',
      items: [{ variantId: 'v1', quantity: 2 }],
    });
    flush();
  });
  it('restricts transit loss commands to owner', () => {
    const f = setup(false, true),
      c = f.componentInstance;
    c.openReceipt(transfer, true);
    c.receiptForm.controls.reason.setValue('Damage');
    c.saveReceipt();
    expect(command).not.toHaveBeenCalled();
    f.componentRef.setInput('owner', true);
    c.saveReceipt();
    expect(command.mock.calls[0][1]).toBe('/api/v1/stock/transfers/t1/losses');
    flush();
  });
  it('only enables receive for an authorized destination with remaining stock', () => {
    const c = setup(false, true).componentInstance;
    expect(c.canReceive(transfer)).toBe(false);
    expect(c.canReceive({ ...transfer, destinationId: 'depot' })).toBe(true);
    expect(
      c.canReceive({
        ...transfer,
        destinationId: 'depot',
        items: transfer.items.map((i) => ({ ...i, remaining: 0 })),
      }),
    ).toBe(false);
  });
  it('keeps the editor open and shows uncertainty after server failure', () => {
    command.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const c = setup().componentInstance;
    c.openProduct();
    c.productForm.patchValue({ name: 'Phone', brand: 'RH' });
    c.saveProduct();
    expect(c.formError()).toBe('uncertain');
    expect(c.editor()).toBe('product');
    expect(c.saving()).toBe(false);
  });
  it('maps insufficient stock and blocks a double submit while saving', () => {
    const result = new Subject();
    command.mockReturnValue(result);
    const c = setup().componentInstance;
    c.openStock('adjustment');
    flush();
    c.stockForm.patchValue({ branchId: 'depot', variantId: 'v1', delta: -5, reason: 'Correction' });
    c.saveStock();
    c.saveStock();
    c.close();
    expect(command).toHaveBeenCalledTimes(1);
    expect(c.editor()).toBe('stock');
    result.error(new HttpErrorResponse({ status: 409, error: { code: 'INSUFFICIENT_STOCK' } }));
    expect(c.formError()).toBe('insufficient');
  });
  it('requires archive confirmation and allows reactivation', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false),
      c = setup().componentInstance;
    c.toggleVariant(variant);
    expect(command).not.toHaveBeenCalled();
    confirm.mockReturnValue(true);
    c.toggleVariant(variant);
    expect(command.mock.calls[0][2].active).toBe(false);
    flush();
    c.toggleVariant({ ...variant, active: false });
    expect(command.mock.calls[1][2].active).toBe(true);
    flush();
  });
  it('retains a selected off-page stock variant after searching', () => {
    const c = setup().componentInstance;
    const row: StockRow = {
      branchId: 'depot',
      variant,
      quantity: 2,
      inTransit: 0,
      incoming: 0,
      initialized: true,
      lowStock: true,
    };
    c.openStock('opening', row);
    flush();
    expect(c.pickerVariants().map((v) => v.id)).toContain('v1');
    c.showHistory(row);
    flush();
    expect(c.historyVariant()).toBe('v1');
    expect(c.tab()).toBe('history');
    expect(c.siteName('depot')).toBe('Dépôt');
    expect(c.siteName('missing')).toBe('missing');
    expect(c.price(10)).toContain('10');
    expect(c.date('2026-10-29T09:00:00Z')).toBeTruthy();
    expect(c.lineLabel('missing')).toBe('missing');
    expect(c.state('custom')).toBe('custom');
  });
  it('prevents cashier mutation calls even when methods are invoked directly', () => {
    const c = setup(false, false).componentInstance;
    c.saveProduct();
    c.saveVariant();
    c.saveStock();
    c.sendTransfer();
    c.toggleVariant(variant);
    expect(command).not.toHaveBeenCalled();
  });
});
