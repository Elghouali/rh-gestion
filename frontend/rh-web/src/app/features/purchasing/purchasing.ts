import { Component, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin, Observable, of, Subscription } from 'rxjs';
import { LanguageService } from '../../core/language.service';
import { InventoryApi } from '../inventory/inventory-api';
import { Page, Site, Variant } from '../inventory/inventory-models';
import { Supplier, Purchase, Delivery } from './purchasing-models';
import { purchasingText, PurchaseKey } from './purchasing-text';
@Component({
  selector: 'app-purchasing',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './purchasing.html',
  styleUrls: ['../inventory/inventory.scss', './purchasing.scss'],
})
export class PurchasingComponent implements OnInit {
  readonly branches = input<Site[]>([]);
  readonly owner = input(false);
  readonly lang = inject(LanguageService);
  private readonly http = inject(HttpClient);
  private readonly api = inject(InventoryApi);
  private readonly fb = inject(FormBuilder);
  private readonly destroy = inject(DestroyRef);
  readonly orders = signal<Purchase[]>([]);
  readonly suppliers = signal<Supplier[]>([]);
  readonly variants = signal<Variant[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  readonly supplierPage = signal(0);
  readonly supplierTotal = signal(0);
  readonly variantPage = signal(0);
  readonly variantTotal = signal(0);
  readonly loading = signal(false);
  readonly loadError = signal(false);
  readonly saving = signal(false);
  readonly saved = signal(false);
  readonly error = signal<'invalid' | 'failed' | 'uncertain' | null>(null);
  readonly tab = signal<'orders' | 'suppliers'>('orders');
  readonly editor = signal<'supplier' | 'order' | 'receipt' | 'inspection' | 'close' | null>(null);
  readonly selectedOrder = signal<Purchase | null>(null);
  readonly selectedReceipt = signal<Delivery | null>(null);
  readonly editingSupplier = signal<Supplier | null>(null);
  readonly query = this.fb.nonNullable.control('');
  readonly supplierQuery = this.fb.nonNullable.control('');
  readonly variantQuery = this.fb.nonNullable.control('');
  readonly supplierForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    phone: ['', Validators.maxLength(40)],
    active: [true],
  });
  private readonly integer = [
    Validators.required,
    Validators.pattern(/^\d+$/),
    Validators.min(0),
    Validators.max(1000000),
  ];
  readonly orderForm = this.fb.nonNullable.group({
    supplierId: ['', Validators.required],
    branchId: ['', Validators.required],
    reference: ['', [Validators.required, Validators.maxLength(100)]],
    note: ['', [Validators.required, Validators.maxLength(500)]],
    items: this.fb.array([this.orderLine()]),
  });
  readonly receiptForm = this.fb.nonNullable.group({
    reference: ['', [Validators.required, Validators.maxLength(100)]],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
    items: this.fb.array<ReturnType<PurchasingComponent['receiptLine']>>([]),
  });
  readonly inspectionForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(500)]],
    items: this.fb.array<ReturnType<PurchasingComponent['inspectionLine']>>([]),
  });
  readonly closeForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });
  private read?: Subscription;
  private supplierRead?: Subscription;
  private variantRead?: Subscription;
  constructor() {
    this.destroy.onDestroy(() => {
      this.read?.unsubscribe();
      this.supplierRead?.unsubscribe();
      this.variantRead?.unsubscribe();
    });
  }
  ngOnInit() {
    this.load();
  }
  t(key: PurchaseKey) {
    return purchasingText[this.lang.language()][key];
  }
  state(status: string) {
    return status in purchasingText.fr ? this.t(status as PurchaseKey) : status;
  }
  money(value: number) {
    return new Intl.NumberFormat(this.lang.language() === 'ar' ? 'ar-MA' : this.lang.language(), {
      minimumFractionDigits: 2,
      maximumFractionDigits: 4,
    }).format(value);
  }
  date(value: string) {
    return new Intl.DateTimeFormat(this.lang.language() === 'ar' ? 'ar-MA' : this.lang.language(), {
      dateStyle: 'short',
      timeStyle: 'short',
      timeZone: 'Africa/Casablanca',
    }).format(new Date(value));
  }
  load() {
    this.read?.unsubscribe();
    this.loading.set(true);
    this.loadError.set(false);
    this.read = forkJoin({
      orders: this.http.get<Page<Purchase>>('/api/v1/purchases', {
        params: { q: this.query.value, page: this.page(), size: 20 },
      }),
      suppliers: this.owner()
        ? this.http.get<Page<Supplier>>('/api/v1/suppliers', {
            params: { q: this.supplierQuery.value, page: 0, size: 100 },
          })
        : of(null),
    })
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (r) => {
          this.error.update((e) => (e === 'failed' ? null : e));
          this.orders.set(r.orders.items);
          this.total.set(r.orders.total);
          if (r.suppliers) {
            this.suppliers.set(r.suppliers.items);
            this.supplierTotal.set(r.suppliers.total);
            this.supplierPage.set(0);
          }
        },
        error: () => {
          this.orders.set([]);
          this.suppliers.set([]);
          this.total.set(0);
          this.supplierTotal.set(0);
          this.loadError.set(true);
        },
      });
  }
  search() {
    this.page.set(0);
    this.load();
  }
  paginate(delta: number) {
    if (this.saving()) return;
    this.page.update((p) => p + delta);
    this.load();
  }
  searchSuppliers(more = false) {
    if (!this.owner()) return;
    this.supplierRead?.unsubscribe();
    const page = more ? this.supplierPage() + 1 : 0;
    this.supplierRead = this.http
      .get<Page<Supplier>>('/api/v1/suppliers', {
        params: { q: this.supplierQuery.value, page, size: 100 },
      })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (r) => {
          this.error.update((e) => (e === 'failed' ? null : e));
          const selected = this.suppliers().filter(
            (s) => s.id === this.orderForm.controls.supplierId.value,
          );
          const list = more ? [...this.suppliers(), ...r.items] : [...selected, ...r.items];
          this.suppliers.set(Array.from(new Map(list.map((s) => [s.id, s])).values()));
          this.supplierTotal.set(r.total);
          this.supplierPage.set(r.page);
        },
        error: () => this.error.set('failed'),
      });
  }
  searchVariants(more = false) {
    this.variantRead?.unsubscribe();
    const page = more ? this.variantPage() + 1 : 0;
    this.variantRead = this.http
      .get<Page<Variant>>('/api/v1/catalog/variants', {
        params: { q: this.variantQuery.value, page, size: 100 },
      })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (r) => {
          this.error.update((e) => (e === 'failed' ? null : e));
          const ids = new Set(this.orderForm.getRawValue().items.map((i) => i.variantId));
          const selected = this.variants().filter((v) => ids.has(v.id));
          const list = more ? [...this.variants(), ...r.items] : [...selected, ...r.items];
          this.variants.set(Array.from(new Map(list.map((v) => [v.id, v])).values()));
          this.variantTotal.set(r.total);
          this.variantPage.set(r.page);
        },
        error: () => this.error.set('failed'),
      });
  }
  begin(editor: 'supplier' | 'order' | 'receipt' | 'inspection' | 'close') {
    if (this.saving()) return;
    this.editor.set(editor);
    this.error.set(null);
    this.saved.set(false);
  }
  close() {
    if (!this.saving()) {
      this.editor.set(null);
      this.error.set(null);
    }
  }
  setTab(tab: 'orders' | 'suppliers') {
    if (this.saving()) return;
    this.close();
    this.tab.set(tab);
  }
  openSupplier(s?: Supplier) {
    this.editingSupplier.set(s ?? null);
    this.supplierForm.reset(
      s
        ? { name: s.name, phone: s.phone, active: s.active }
        : { name: '', phone: '', active: true },
    );
    this.begin('supplier');
  }
  saveSupplier() {
    if (!this.owner() || this.saving()) return;
    if (this.supplierForm.invalid) {
      this.error.set('invalid');
      return;
    }
    const r = this.supplierForm.getRawValue(),
      old = this.editingSupplier();
    const data = old
      ? { name: r.name.trim(), phone: r.phone.trim(), active: r.active, version: old.version }
      : { name: r.name.trim(), phone: r.phone.trim() };
    this.write(
      this.api.command(
        old ? 'PUT' : 'POST',
        old ? '/api/v1/suppliers/' + old.id : '/api/v1/suppliers',
        data,
      ),
    );
  }
  orderLine() {
    return this.fb.nonNullable.group({
      variantId: ['', Validators.required],
      quantity: [1, [...this.integer, Validators.min(1)]],
      unitCost: ['', [Validators.required, Validators.pattern(/^\d{1,10}([.,]\d{1,4})?$/)]],
    });
  }
  addLine() {
    if (this.orderForm.controls.items.length < 100)
      this.orderForm.controls.items.push(this.orderLine());
  }
  openOrder() {
    if (!this.owner()) return;
    this.orderForm.reset({
      supplierId: '',
      branchId: this.branches()[0]?.id ?? '',
      reference: '',
      note: '',
    });
    this.orderForm.controls.items.clear();
    this.addLine();
    this.begin('order');
    this.variantQuery.setValue('');
    this.searchVariants();
  }
  saveOrder() {
    if (!this.owner() || this.saving()) return;
    const r = this.orderForm.getRawValue();
    if (
      this.orderForm.invalid ||
      new Set(r.items.map((i) => i.variantId)).size !== r.items.length
    ) {
      this.error.set('invalid');
      return;
    }
    this.write(
      this.api.command('POST', '/api/v1/purchases', {
        ...r,
        reference: r.reference.trim(),
        note: r.note.trim(),
        items: r.items.map((i) => ({ ...i, unitCost: i.unitCost.replace(',', '.') })),
      }),
    );
  }
  receiptLine(id: string, remaining: number) {
    return this.fb.nonNullable.group({
      variantId: [id],
      quantity: [0, [...this.integer, Validators.max(remaining)]],
    });
  }
  openReceipt(order: Purchase) {
    this.selectedOrder.set(order);
    this.receiptForm.controls.items.clear();
    this.receiptForm.controls.reference.setValue('');
    this.receiptForm.controls.reason.setValue('');
    for (const l of order.items.filter((l) => l.remaining > 0))
      this.receiptForm.controls.items.push(this.receiptLine(l.variantId, l.remaining));
    this.begin('receipt');
  }
  saveReceipt() {
    if (this.saving()) return;
    const r = this.receiptForm.getRawValue(),
      items = r.items.filter((i) => i.quantity > 0);
    if (this.receiptForm.invalid || items.length === 0) {
      this.error.set('invalid');
      return;
    }
    this.write(
      this.api.command('POST', '/api/v1/purchases/' + this.selectedOrder()!.id + '/receipts', {
        reference: r.reference.trim(),
        reason: r.reason.trim(),
        items,
      }),
    );
  }
  inspectionLine(id: string, pending: number) {
    return this.fb.nonNullable.group({
      variantId: [id],
      accepted: [0, [...this.integer, Validators.max(pending)]],
      rejected: [0, [...this.integer, Validators.max(pending)]],
    });
  }
  openInspection(order: Purchase, receipt: Delivery) {
    this.selectedOrder.set(order);
    this.selectedReceipt.set(receipt);
    this.inspectionForm.controls.items.clear();
    this.inspectionForm.controls.reason.setValue('');
    for (const l of receipt.items.filter((l) => l.pending > 0))
      this.inspectionForm.controls.items.push(this.inspectionLine(l.variantId, l.pending));
    this.begin('inspection');
  }
  saveInspection() {
    if (!this.owner() || this.saving()) return;
    const r = this.inspectionForm.getRawValue(),
      items = r.items.filter((i) => i.accepted + i.rejected > 0);
    if (
      this.inspectionForm.invalid ||
      items.length === 0 ||
      items.some(
        (i) =>
          i.accepted + i.rejected >
          (this.selectedReceipt()?.items.find((l) => l.variantId === i.variantId)?.pending ?? 0),
      )
    ) {
      this.error.set('invalid');
      return;
    }
    this.write(
      this.api.command(
        'POST',
        '/api/v1/purchases/' +
          this.selectedOrder()!.id +
          '/receipts/' +
          this.selectedReceipt()!.id +
          '/inspections',
        { reason: r.reason.trim(), items },
      ),
    );
  }
  openClose(order: Purchase) {
    this.selectedOrder.set(order);
    this.closeForm.reset();
    this.begin('close');
  }
  saveClose() {
    if (!this.owner() || this.saving()) return;
    if (this.closeForm.invalid) {
      this.error.set('invalid');
      return;
    }
    this.write(
      this.api.command('POST', '/api/v1/purchases/' + this.selectedOrder()!.id + '/close', {
        reason: this.closeForm.controls.reason.value.trim(),
      }),
    );
  }
  hasPending(receipt: Delivery) {
    return receipt.items.some((l) => l.pending > 0);
  }
  hasRemaining(order: Purchase) {
    return order.status !== 'CLOSED' && order.items.some((l) => l.remaining > 0);
  }
  label(id: string) {
    return this.selectedOrder()?.items.find((l) => l.variantId === id)?.sku ?? id;
  }
  private write<T>(request: Observable<T>) {
    this.saving.set(true);
    this.error.set(null);
    this.saved.set(false);
    request
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.editor.set(null);
          this.saved.set(true);
          this.load();
        },
        error: (e: HttpErrorResponse) =>
          this.error.set(!e.status || e.status >= 500 ? 'uncertain' : 'failed'),
      });
  }
}
