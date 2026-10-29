export interface Supplier {
  id: string;
  name: string;
  phone: string;
  active: boolean;
  version: number;
}
export interface PurchaseLine {
  variantId: string;
  sku: string;
  label: string;
  quantity: number;
  received: number;
  remaining: number;
  unitCost?: number;
}
export interface DeliveryLine {
  variantId: string;
  sku: string;
  label: string;
  quantity: number;
  accepted: number;
  rejected: number;
  pending: number;
}
export interface Delivery {
  id: string;
  reference: string;
  reason: string;
  actor: string;
  createdAt: string;
  items: DeliveryLine[];
}
export interface Purchase {
  id: string;
  supplierId: string;
  supplierName: string;
  branchId: string;
  branchName: string;
  reference: string;
  note: string;
  status: string;
  version: number;
  createdAt: string;
  items: PurchaseLine[];
  receipts: Delivery[];
}
