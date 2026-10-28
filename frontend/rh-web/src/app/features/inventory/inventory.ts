import { Component, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin, Observable, of, Subscription } from 'rxjs';
import { LanguageService } from '../../core/language.service';
import { InventoryApi } from './inventory-api';
import { inventoryText, InventoryKey } from './inventory-text';
import { Page, Product, Variant, StockRow, Transfer, Movement, Site } from './inventory-models';
type Tab = 'catalog' | 'stock' | 'transfers' | 'history';
const empty = <T>(): Page<T> => ({ items: [], page: 0, size: 20, total: 0 });
@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './inventory.html',
  styleUrl: './inventory.scss',
})
export class InventoryComponent implements OnInit {
  readonly branches = input<Site[]>([]);
  readonly owner = input(false);
  readonly manager = input(false);
  readonly lang = inject(LanguageService);
  private readonly http = inject(HttpClient);
  private readonly api = inject(InventoryApi);
  private readonly fb = inject(FormBuilder);
  private readonly destroy = inject(DestroyRef);
  readonly tab = signal<Tab>('catalog');
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<InventoryKey | null>(null);
  readonly formError = signal<InventoryKey | null>(null);
  readonly saved = signal(false);
  readonly variants = signal<Page<Variant>>(empty());
  readonly balances = signal<Page<StockRow>>(empty());
  readonly transfers = signal<Page<Transfer>>(empty());
  readonly movements = signal<Page<Movement>>(empty());
  readonly pickerVariants = signal<Variant[]>([]);
  readonly pickerPage = signal(0);
  readonly pickerTotal = signal(0);
  readonly variantSearch = this.fb.nonNullable.control('');
  readonly products = signal<Product[]>([]);
  readonly modelTotal = signal(0);
  readonly destinations = signal<Site[]>([]);
  readonly page = signal(0);
  readonly historyVariant = signal<string | null>(null);
  readonly editor = signal<'product' | 'variant' | 'stock' | 'transfer' | 'receipt' | null>(null);
  readonly editingProduct = signal<Product | null>(null);
  readonly editingVariant = signal<Variant | null>(null);
  readonly selectedTransfer = signal<Transfer | null>(null);
  readonly loss = signal(false);
  readonly filter = this.fb.nonNullable.group({ q: [''], branchId: [''] });
  readonly modelSearch = this.fb.nonNullable.control('');
  readonly productForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    brand: ['', [Validators.required, Validators.maxLength(80)]],
    category: ['PHONE', Validators.required],
    active: [true],
  });
  private readonly money = [Validators.required, Validators.pattern(/^\d{1,10}([.,]\d{1,2})?$/)];
  readonly variantForm = this.fb.group({
    productId: ['', Validators.required],
    sku: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9._/-]{0,63}$/)]],
    color: ['', [Validators.required, Validators.maxLength(80)]],
    ramGb: [
      null as number | null,
      [Validators.min(1), Validators.max(1024), Validators.pattern(/^\d+$/)],
    ],
    storageGb: [
      null as number | null,
      [Validators.min(1), Validators.max(65536), Validators.pattern(/^\d+$/)],
    ],
    wholesalePrice: ['', this.money],
    retailPrice: ['', this.money],
    minimumStock: [
      0,
      [
        Validators.required,
        Validators.min(0),
        Validators.max(1000000),
        Validators.pattern(/^-?\d+$/),
      ],
    ],
    active: [true],
  });
  readonly stockMode = signal<'opening' | 'adjustment'>('opening');
  readonly stockForm = this.fb.group({
    branchId: ['', Validators.required],
    variantId: ['', Validators.required],
    quantity: [
      null as number | null,
      [
        Validators.required,
        Validators.min(0),
        Validators.max(1000000),
        Validators.pattern(/^-?\d+$/),
      ],
    ],
    delta: [
      null as number | null,
      [
        Validators.required,
        Validators.min(-1000000),
        Validators.max(1000000),
        Validators.pattern(/^-?\d+$/),
      ],
    ],
    unitCost: ['', [Validators.pattern(/^\d{1,10}([.,]\d{1,4})?$/)]],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });
  readonly transferForm = this.fb.group({
    sourceId: ['', Validators.required],
    destinationId: ['', Validators.required],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
    items: this.fb.array([this.transferLine()]),
  });
  readonly receiptForm = this.fb.group({
    reason: ['', [Validators.required, Validators.maxLength(500)]],
    items: this.fb.array<ReturnType<InventoryComponent['receiptLine']>>([]),
  });
  private reads?: Subscription;
  private modelRead?: Subscription;
  private pickerRead?: Subscription;
  constructor() {
    this.destroy.onDestroy(() => {
      this.reads?.unsubscribe();
      this.modelRead?.unsubscribe();
      this.pickerRead?.unsubscribe();
    });
  }
  ngOnInit() {
    this.filter.controls.branchId.setValue(this.branches()[0]?.id ?? '');
    this.load();
  }
  t(key: InventoryKey): string {
    return inventoryText[this.lang.language()][key];
  }
  state(value: string): string {
    return value in inventoryText.fr ? this.t(value as InventoryKey) : value;
  }
  siteName(id: string) {
    return this.branches().find((s) => s.id === id)?.name ?? id;
  }
  price(value: number) {
    return new Intl.NumberFormat(this.lang.language() === 'ar' ? 'ar-MA' : this.lang.language(), {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  }
  date(value: string) {
    return new Intl.DateTimeFormat(this.lang.language() === 'ar' ? 'ar-MA' : this.lang.language(), {
      dateStyle: 'short',
      timeStyle: 'short',
      timeZone: 'Africa/Casablanca',
    }).format(new Date(value));
  }
  setTab(tab: Tab) {
    if (this.saving()) return;
    this.tab.set(tab);
    this.page.set(0);
    this.close();
    this.load();
  }
  apply() {
    this.page.set(0);
    this.load();
  }
  paginate(delta: number) {
    this.page.set(this.page() + delta);
    this.load();
  }
  count() {
    return this.tab() === 'catalog'
      ? this.variants().total
      : this.tab() === 'stock'
        ? this.balances().total
        : this.tab() === 'transfers'
          ? this.transfers().total
          : this.movements().total;
  }
  load() {
    this.reads?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    const { q, branchId } = this.filter.getRawValue();
    const page = this.page();
    const scoped: Record<string, string | number> = { page, size: 20 };
    if (branchId) scoped['branchId'] = branchId;
    const historyParams = { ...scoped };
    if (this.historyVariant()) historyParams['variantId'] = this.historyVariant()!;
    this.reads = forkJoin({
      variants: this.http.get<Page<Variant>>('/api/v1/catalog/variants', {
        params: { q, page, size: 20 },
      }),
      stock: this.http.get<Page<StockRow>>('/api/v1/stock/balances', { params: { ...scoped, q } }),
      transfers: this.http.get<Page<Transfer>>('/api/v1/stock/transfers', {
        params: { page, size: 20 },
      }),
      movements: this.http.get<Page<Movement>>('/api/v1/stock/movements', {
        params: historyParams,
      }),
      products: this.http.get<Page<Product>>('/api/v1/catalog/products', {
        params: { q: this.modelSearch.value, page: 0, size: 100 },
      }),
      destinations: this.manager()
        ? this.http.get<Site[]>('/api/v1/stock/destinations')
        : of([] as Site[]),
    })
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (r) => {
          this.variants.set(r.variants);
          if (!this.editor()) this.pickerVariants.set(r.variants.items);
          this.balances.set(r.stock);
          this.transfers.set(r.transfers);
          this.movements.set(r.movements);
          this.products.set(r.products.items);
          this.modelTotal.set(r.products.total);
          this.destinations.set(r.destinations);
        },
        error: (e) => {
          this.error.set(this.errorKey(e));
          this.variants.set(empty());
          this.balances.set(empty());
          this.transfers.set(empty());
          this.movements.set(empty());
        },
      });
  }
  searchModels(more = false) {
    this.modelRead?.unsubscribe();
    const page = more ? Math.floor(this.products().length / 100) : 0;
    this.modelRead = this.http
      .get<Page<Product>>('/api/v1/catalog/products', {
        params: { q: this.modelSearch.value, page, size: 100 },
      })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (r) => {
          this.products.set(more ? [...this.products(), ...r.items] : r.items);
          this.modelTotal.set(r.total);
        },
        error: (e) => this.formError.set(this.errorKey(e)),
      });
  }
  close() {
    if (!this.saving()) {
      this.editor.set(null);
      this.formError.set(null);
    }
  }
  private begin(editor: 'product' | 'variant' | 'stock' | 'transfer' | 'receipt') {
    this.formError.set(null);
    this.saved.set(false);
    this.editor.set(editor);
  }
  openProduct(product?: Product) {
    this.editingProduct.set(product ?? null);
    this.productForm.reset(
      product
        ? {
            name: product.name,
            brand: product.brand,
            category: product.category,
            active: product.active,
          }
        : { name: '', brand: '', category: 'PHONE', active: true },
    );
    this.begin('product');
  }
  saveProduct() {
    if (!this.owner() || this.saving()) return;
    if (this.productForm.invalid) {
      this.productForm.markAllAsTouched();
      this.formError.set('invalidForm');
      return;
    }
    const raw = this.productForm.getRawValue(),
      old = this.editingProduct();
    const data = old
      ? { name: raw.name.trim(), brand: raw.brand.trim(), active: raw.active, version: old.version }
      : { name: raw.name.trim(), brand: raw.brand.trim(), category: raw.category };
    this.write(
      this.api.command<Product>(
        old ? 'PUT' : 'POST',
        old ? '/api/v1/catalog/products/' + old.id : '/api/v1/catalog/products',
        data,
      ),
    );
  }
  openVariant(variant?: Variant) {
    this.editingVariant.set(variant ?? null);
    this.variantForm.reset(
      variant
        ? {
            productId: variant.productId,
            sku: variant.sku,
            color: variant.color,
            ramGb: variant.ramGb,
            storageGb: variant.storageGb,
            wholesalePrice: variant.wholesalePrice.toFixed(2),
            retailPrice: variant.retailPrice.toFixed(2),
            minimumStock: variant.minimumStock,
            active: variant.active,
          }
        : {
            productId: this.products().find((p) => p.active)?.id ?? '',
            sku: '',
            color: '',
            ramGb: null,
            storageGb: null,
            wholesalePrice: '',
            retailPrice: '',
            minimumStock: 0,
            active: true,
          },
    );
    this.begin('variant');
  }
  saveVariant() {
    if (!this.owner() || this.saving()) return;
    const raw = this.variantForm.getRawValue();
    const product = this.products().find((p) => p.id === raw.productId);
    if (
      this.variantForm.invalid ||
      (product?.category === 'PHONE' && (!raw.ramGb || !raw.storageGb))
    ) {
      this.variantForm.markAllAsTouched();
      this.formError.set('invalidForm');
      return;
    }
    const old = this.editingVariant();
    const { productId, ...fields } = raw;
    const values = {
      ...fields,
      sku: raw.sku!.trim(),
      color: raw.color!.trim(),
      wholesalePrice: raw.wholesalePrice!.replace(',', '.'),
      retailPrice: raw.retailPrice!.replace(',', '.'),
    };
    const data = old ? { ...values, version: old.version } : { ...values, productId };
    this.write(
      this.api.command<Variant>(
        old ? 'PUT' : 'POST',
        old ? '/api/v1/catalog/variants/' + old.id : '/api/v1/catalog/variants',
        data,
      ),
    );
  }
  toggleVariant(v: Variant) {
    if (!this.owner() || this.saving()) return;
    if (v.active && !window.confirm(this.t('confirmArchive'))) return;
    this.write(
      this.api.command('PUT', '/api/v1/catalog/variants/' + v.id, {
        sku: v.sku,
        color: v.color,
        ramGb: v.ramGb,
        storageGb: v.storageGb,
        wholesalePrice: v.wholesalePrice.toFixed(2),
        retailPrice: v.retailPrice.toFixed(2),
        minimumStock: v.minimumStock,
        active: !v.active,
        version: v.version,
      }),
    );
  }
  searchVariants(more = false) {
    this.pickerRead?.unsubscribe();
    const page = more ? this.pickerPage() + 1 : 0;
    this.pickerRead = this.http
      .get<Page<Variant>>('/api/v1/catalog/variants', {
        params: { q: this.variantSearch.value, page, size: 100 },
      })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (r) => {
          const ids = new Set(
            this.editor() === 'stock'
              ? [this.stockForm.controls.variantId.value]
              : this.transferForm.getRawValue().items.map((i) => i.variantId),
          );
          const selected = this.pickerVariants().filter((v) => ids.has(v.id));
          const items = more ? [...this.pickerVariants(), ...r.items] : [...selected, ...r.items];
          this.pickerVariants.set(Array.from(new Map(items.map((v) => [v.id, v])).values()));
          this.pickerPage.set(r.page);
          this.pickerTotal.set(r.total);
        },
        error: (e) => this.formError.set(this.errorKey(e)),
      });
  }
  openStock(mode: 'opening' | 'adjustment', row?: StockRow) {
    this.stockMode.set(mode);
    this.stockForm.reset({
      branchId:
        row?.branchId ?? this.filter.controls.branchId.value ?? this.branches()[0]?.id ?? '',
      variantId: row?.variant.id ?? this.variants().items[0]?.id ?? '',
      quantity: null,
      delta: null,
      unitCost: '',
      reason: '',
    });
    if (mode === 'opening') {
      this.stockForm.controls.delta.disable();
      this.stockForm.controls.quantity.enable();
    } else {
      this.stockForm.controls.quantity.disable();
      this.stockForm.controls.delta.enable();
    }
    if (row)
      this.pickerVariants.set([
        row.variant,
        ...this.pickerVariants().filter((v) => v.id !== row.variant.id),
      ]);
    this.begin('stock');
    this.variantSearch.setValue('');
    this.searchVariants();
  }
  saveStock() {
    if (!this.owner() || this.saving()) return;
    if (this.stockForm.invalid) {
      this.stockForm.markAllAsTouched();
      this.formError.set('invalidForm');
      return;
    }
    const r = this.stockForm.getRawValue();
    const data =
      this.stockMode() === 'opening'
        ? {
            branchId: r.branchId,
            variantId: r.variantId,
            quantity: r.quantity,
            unitCost: r.unitCost ? r.unitCost.replace(',', '.') : null,
            reason: r.reason!.trim(),
          }
        : {
            branchId: r.branchId,
            variantId: r.variantId,
            delta: r.delta,
            reason: r.reason!.trim(),
          };
    if (this.stockMode() === 'adjustment' && r.delta === 0) {
      this.formError.set('invalidForm');
      return;
    }
    this.write(
      this.api.command(
        'POST',
        '/api/v1/stock/' + (this.stockMode() === 'opening' ? 'openings' : 'adjustments'),
        data,
      ),
    );
  }
  transferLine() {
    return this.fb.group({
      variantId: ['', Validators.required],
      quantity: [
        1,
        [
          Validators.required,
          Validators.min(1),
          Validators.max(1000000),
          Validators.pattern(/^-?\d+$/),
        ],
      ],
    });
  }
  addTransferLine() {
    this.transferForm.controls.items.push(this.transferLine());
  }
  openTransfer() {
    this.transferForm.reset({
      sourceId: this.branches()[0]?.id ?? '',
      destinationId: '',
      reason: '',
    });
    this.transferForm.controls.items.clear();
    this.addTransferLine();
    this.begin('transfer');
    this.variantSearch.setValue('');
    this.searchVariants();
  }
  sendTransfer() {
    if (!this.manager() || this.saving()) return;
    const r = this.transferForm.getRawValue();
    if (this.transferForm.invalid || r.sourceId === r.destinationId) {
      this.transferForm.markAllAsTouched();
      this.formError.set('invalidForm');
      return;
    }
    this.write(this.api.command('POST', '/api/v1/stock/transfers', r));
  }
  canReceive(t: Transfer) {
    return (
      this.manager() &&
      t.items.some((i) => i.remaining > 0) &&
      (this.owner() || this.branches().some((b) => b.id === t.destinationId))
    );
  }
  receiptLine(id: string, remaining: number) {
    return this.fb.group({
      variantId: [id, Validators.required],
      quantity: [
        remaining,
        [
          Validators.required,
          Validators.min(0),
          Validators.max(remaining),
          Validators.pattern(/^\d+$/),
        ],
      ],
    });
  }
  openReceipt(transfer: Transfer, loss = false) {
    this.selectedTransfer.set(transfer);
    this.loss.set(loss);
    this.receiptForm.controls.items.clear();
    this.receiptForm.controls.reason.setValue('');
    for (const line of transfer.items.filter((i) => i.remaining > 0))
      this.receiptForm.controls.items.push(this.receiptLine(line.variantId, line.remaining));
    this.begin('receipt');
  }
  lineLabel(id: string) {
    return this.selectedTransfer()?.items.find((i) => i.variantId === id)?.label ?? id;
  }
  saveReceipt() {
    if (!this.manager() || this.saving() || (this.loss() && !this.owner())) return;
    const raw = this.receiptForm.getRawValue();
    const items = raw.items.filter((i) => (i.quantity ?? 0) > 0);
    if (this.receiptForm.invalid || items.length === 0) {
      this.formError.set('invalidForm');
      return;
    }
    this.write(
      this.api.command(
        'POST',
        '/api/v1/stock/transfers/' +
          this.selectedTransfer()!.id +
          (this.loss() ? '/losses' : '/receipts'),
        { reason: raw.reason!.trim(), items },
      ),
    );
  }
  showHistory(row?: StockRow) {
    this.historyVariant.set(row?.variant.id ?? null);
    if (row) this.filter.controls.branchId.setValue(row.branchId);
    this.setTab('history');
  }
  private write<T>(request: Observable<T>) {
    this.saving.set(true);
    this.formError.set(null);
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
          this.formError.set(!e.status || e.status >= 500 ? 'uncertain' : this.errorKey(e)),
      });
  }
  private errorKey(error: unknown): InventoryKey {
    const e = error as HttpErrorResponse;
    const code = e.error?.code;
    const codes: Record<string, InventoryKey> = {
      DATA_CONFLICT: 'duplicate',
      INSUFFICIENT_STOCK: 'insufficient',
      OPENING_ALREADY_RECORDED: 'openingRecorded',
      STALE_VERSION: 'stale',
      IDENTITY_LOCKED: 'identityLocked',
      PHONE_ATTRIBUTES_REQUIRED: 'phoneRequired',
      RECEIPT_EXCEEDS_REMAINING: 'receiptTooLarge',
      INVALID_SKU: 'invalidSku',
      SAME_BRANCH: 'sameBranch',
      VARIANT_ARCHIVED: 'archived',
      PRODUCT_ARCHIVED: 'archived',
      DUPLICATE_TRANSFER_LINE: 'duplicateLine',
      VALIDATION_FAILED: 'invalidForm',
      ZERO_ADJUSTMENT: 'invalidForm',
      IDEMPOTENCY_CONFLICT: 'stale',
    };
    return (
      codes[code] ??
      (e.status === 401
        ? 'sessionExpired'
        : e.status === 403
          ? 'forbidden'
          : e.status === 0 || !e.status
            ? 'uncertain'
            : 'unavailable')
    );
  }
}
