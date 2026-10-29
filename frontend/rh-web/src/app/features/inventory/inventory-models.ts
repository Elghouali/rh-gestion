export interface Site {
  id: string;
  code: string;
  name: string;
  type: string;
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
export interface Product {
  id: string;
  name: string;
  brand: string;
  category: 'PHONE' | 'ACCESSORY';
  active: boolean;
  version: number;
}
export interface Variant {
  id: string;
  productId: string;
  productName: string;
  brand: string;
  category: string;
  sku: string;
  color: string;
  ramGb: number | null;
  storageGb: number | null;
  wholesalePrice: number;
  retailPrice: number;
  minimumStock: number;
  active: boolean;
  version: number;
}
export interface StockRow {
  branchId: string;
  variant: Variant;
  quantity: number;
  inTransit: number;
  incoming: number;
  initialized: boolean;
  lowStock: boolean;
  quarantine?: number;
  rejected?: number;
}
export interface TransferLine {
  variantId: string;
  sku: string;
  label: string;
  quantity: number;
  received: number;
  lost: number;
  remaining: number;
}
export interface Transfer {
  id: string;
  sourceId: string;
  destinationId: string;
  sourceName: string;
  destinationName: string;
  status: string;
  createdAt: string;
  items: TransferLine[];
}
export interface Movement {
  id: string;
  entryId: string;
  kind: string;
  reason: string;
  actor: string;
  createdAt: string;
  branchId: string;
  variantId: string;
  sku: string;
  label: string;
  bucket: string;
  delta: number;
  quantityAfter: number;
  unitCost?: number;
  transferId?: string;
}
