import { TestBed } from '@angular/core/testing';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PurchasingComponent } from './purchasing';
import { InventoryApi } from '../inventory/inventory-api';
import { LanguageService } from '../../core/language.service';
describe('Purchasing operator workflows', () => {
  let http: HttpTestingController;
  const command = vi.fn();
  const order: any = {
    id: 'o1',
    supplierId: 's1',
    supplierName: 'Supplier',
    branchId: 'depot',
    branchName: 'Depot',
    reference: 'PO1',
    note: 'Approved',
    status: 'PARTIAL',
    version: 1,
    createdAt: '2026-10-29T09:00:00Z',
    items: [
      {
        variantId: 'v1',
        sku: 'SKU1',
        label: 'Phone',
        quantity: 5,
        received: 3,
        remaining: 2,
        unitCost: 70,
      },
    ],
    receipts: [
      {
        id: 'r1',
        reference: 'BL1',
        reason: 'Count',
        actor: 'stock',
        createdAt: '2026-10-29T09:00:00Z',
        items: [
          {
            variantId: 'v1',
            sku: 'SKU1',
            label: 'Phone',
            quantity: 3,
            accepted: 0,
            rejected: 0,
            pending: 3,
          },
        ],
      },
    ],
  };
  beforeEach(() => {
    command.mockReset().mockReturnValue(of({ id: 'done' }));
    TestBed.configureTestingModule({
      imports: [PurchasingComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: InventoryApi, useValue: { command } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(LanguageService).set('fr');
  });
  afterEach(() => http.verify());
  function flush() {
    for (const r of http.match((r) => r.method === 'GET'))
      r.flush({
        items: r.request.url.endsWith('/purchases')
          ? [order]
          : r.request.url.endsWith('/suppliers')
            ? [{ id: 's1', name: 'Supplier', phone: '', active: true, version: 0 }]
            : [
                {
                  id: 'v1',
                  sku: 'SKU1',
                  productName: 'Phone',
                  color: 'Noir',
                  ramGb: 8,
                  storageGb: 128,
                  active: true,
                },
              ],
        page: 0,
        size: 100,
        total: 1,
      });
  }
  function setup(owner = true) {
    const f = TestBed.createComponent(PurchasingComponent);
    f.componentRef.setInput('branches', [
      { id: 'depot', code: 'DEPOT', name: 'Depot', type: 'DEPOT' },
    ]);
    f.componentRef.setInput('owner', owner);
    f.detectChanges();
    flush();
    f.detectChanges();
    return f;
  }
  it('shows owner purchase and supplier actions and scoped order lines', () => {
    const f = setup();
    expect(f.nativeElement.textContent).toContain('PO1');
    expect(f.nativeElement.querySelector('[data-testid="new-purchase"]')).not.toBeNull();
    expect(f.nativeElement.textContent).toContain('70');
  });
  it('keeps supplier management and purchase prices hidden for stock manager', () => {
    const f = setup(false);
    expect(f.nativeElement.querySelector('[data-testid="new-purchase"]')).toBeNull();
    expect(f.nativeElement.querySelector('[data-testid="suppliers-tab"]')).toBeNull();
    expect(f.nativeElement.textContent).not.toContain('70');
  });
  it('requires valid supplier and order quantities before submission', () => {
    const c = setup().componentInstance;
    c.openSupplier();
    c.saveSupplier();
    expect(command).not.toHaveBeenCalled();
    c.openOrder();
    c.orderForm.patchValue({
      supplierId: 's1',
      branchId: 'depot',
      reference: 'PO1',
      note: 'Approved',
    });
    c.saveOrder();
    expect(command).not.toHaveBeenCalled();
    flush();
  });
  it('records supplier changes with a version and no fixed legal values', () => {
    const c = setup().componentInstance;
    c.openSupplier({ id: 's1', name: 'Supplier', phone: '', active: true, version: 2 });
    c.supplierForm.patchValue({ name: ' Renamed ', active: false });
    c.saveSupplier();
    expect(command).toHaveBeenCalledWith('PUT', '/api/v1/suppliers/s1', {
      name: 'Renamed',
      phone: '',
      active: false,
      version: 2,
    });
    flush();
  });
  it('sends a physical receipt without claiming it is sellable', () => {
    const c = setup(false).componentInstance;
    c.openReceipt(order);
    c.receiptForm.controls.reference.setValue('BL2');
    c.receiptForm.controls.reason.setValue('Count');
    c.receiptForm.controls.items.at(0).controls.quantity.setValue(1);
    c.saveReceipt();
    expect(command).toHaveBeenCalledWith(
      'POST',
      '/api/v1/purchases/o1/receipts',
      expect.objectContaining({ items: [{ variantId: 'v1', quantity: 1 }] }),
    );
    flush();
  });
  it('rejects inspection totals larger than pending and accepts partial inspection', () => {
    const c = setup().componentInstance;
    c.openInspection(order, order.receipts[0]);
    c.inspectionForm.controls.reason.setValue('Checked');
    c.inspectionForm.controls.items.at(0).patchValue({ accepted: 3, rejected: 1 });
    c.saveInspection();
    expect(command).not.toHaveBeenCalled();
    c.inspectionForm.controls.items.at(0).patchValue({ accepted: 2, rejected: 1 });
    c.saveInspection();
    expect(command.mock.calls[0][1]).toBe('/api/v1/purchases/o1/receipts/r1/inspections');
    flush();
  });
  it('preserves the form and same payload after uncertain server result', () => {
    const c = setup().componentInstance;
    command.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    c.openSupplier();
    c.supplierForm.patchValue({ name: 'Supplier' });
    c.saveSupplier();
    expect(c.error()).toBe('uncertain');
    expect(c.editor()).toBe('supplier');
  });
  it('closes only the unreceived remainder, with owner and reason', () => {
    const f = setup(false),
      c = f.componentInstance;
    c.openClose(order);
    c.closeForm.controls.reason.setValue('Cancelled remainder');
    c.saveClose();
    expect(command).not.toHaveBeenCalled();
    f.componentRef.setInput('owner', true);
    c.saveClose();
    expect(command.mock.calls[0][1]).toBe('/api/v1/purchases/o1/close');
    flush();
  });
  it('renders receipt and inspection quantities in Arabic', () => {
    const f = setup(),
      c = f.componentInstance;
    TestBed.inject(LanguageService).set('ar');
    c.openInspection(order, order.receipts[0]);
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('SKU1');
    expect(f.nativeElement.querySelector('[formControlName="accepted"]')).not.toBeNull();
  });
  it('does not show an empty order list after a failed read', () => {
    const f = TestBed.createComponent(PurchasingComponent);
    f.componentRef.setInput('branches', []); f.componentRef.setInput('owner', true); f.detectChanges();
    for (const r of http.match(r => r.method === 'GET')) {
      if (r.request.url.endsWith('/purchases')) r.flush('Down', {status:503,statusText:'Unavailable'});
      else if (!r.cancelled) r.flush({items:[],page:0,size:100,total:0});
    }
    f.detectChanges();
    expect(f.nativeElement.textContent).not.toContain('Aucune commande pour cette recherche.');
  });
  it('does not offer stock release to a manager or submit it through direct invocation', () => {
    const f=setup(false),c=f.componentInstance;
    const buttons=Array.from(f.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    expect(buttons.some(b=>b.textContent?.trim()===c.t('inspect'))).toBe(false);
    c.openInspection(order,order.receipts[0]);c.inspectionForm.controls.reason.setValue('Checked');c.inspectionForm.controls.items.at(0).patchValue({accepted:3,rejected:0});c.saveInspection();expect(command).not.toHaveBeenCalled();
  });
});