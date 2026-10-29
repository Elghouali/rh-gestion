ALTER TABLE stock_balances DROP CONSTRAINT stock_balances_bucket_check;
ALTER TABLE stock_balances ADD CHECK(bucket IN('SELLABLE','TRANSIT','QUARANTINE','REJECTED'));
ALTER TABLE stock_entries DROP CONSTRAINT stock_entries_bucket_check;
ALTER TABLE stock_entries ADD CHECK(bucket IN('SELLABLE','TRANSIT','QUARANTINE','REJECTED'));
ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_kind_check;
ALTER TABLE stock_movements ADD CHECK(kind IN('OPENING','ADJUSTMENT','TRANSFER_OUT','TRANSFER_IN','TRANSIT_LOSS','PURCHASE_RECEIPT','QUALITY_RELEASE','QUALITY_REJECT'));
CREATE TABLE purchase_orders(
 id UUID PRIMARY KEY,supplier_id UUID NOT NULL,supplier_name VARCHAR(160) NOT NULL,branch_id UUID NOT NULL,branch_name VARCHAR(160) NOT NULL,
 reference VARCHAR(100) NOT NULL,note VARCHAR(500) NOT NULL,closed BOOLEAN NOT NULL DEFAULT false,version BIGINT NOT NULL DEFAULT 0,
 actor VARCHAR(100) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),close_reason VARCHAR(500),closed_by VARCHAR(100),closed_at TIMESTAMPTZ);
CREATE UNIQUE INDEX purchase_reference ON purchase_orders(lower(reference));
CREATE TABLE purchase_lines(
 order_id UUID NOT NULL REFERENCES purchase_orders(id),variant_id UUID NOT NULL REFERENCES product_variants(id),sku VARCHAR(64) NOT NULL,label TEXT NOT NULL,
 quantity BIGINT NOT NULL CHECK(quantity>0),received BIGINT NOT NULL DEFAULT 0 CHECK(received>=0 AND received<=quantity),unit_cost NUMERIC(14,4) NOT NULL CHECK(unit_cost>=0),
 PRIMARY KEY(order_id,variant_id));
CREATE TABLE purchase_receipts(
 id UUID PRIMARY KEY,order_id UUID NOT NULL REFERENCES purchase_orders(id),reference VARCHAR(100) NOT NULL,reason VARCHAR(500) NOT NULL,
 actor VARCHAR(100) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),movement_id UUID NOT NULL REFERENCES stock_movements(id));
CREATE UNIQUE INDEX purchase_receipt_reference ON purchase_receipts(order_id,lower(reference));
CREATE TABLE purchase_receipt_lines(
 receipt_id UUID NOT NULL REFERENCES purchase_receipts(id),variant_id UUID NOT NULL REFERENCES product_variants(id),sku VARCHAR(64) NOT NULL,label TEXT NOT NULL,
 quantity BIGINT NOT NULL CHECK(quantity>0),accepted BIGINT NOT NULL DEFAULT 0 CHECK(accepted>=0),rejected BIGINT NOT NULL DEFAULT 0 CHECK(rejected>=0),
 PRIMARY KEY(receipt_id,variant_id),CHECK(accepted+rejected<=quantity));
CREATE TABLE purchase_inspections(
 id UUID PRIMARY KEY,receipt_id UUID NOT NULL REFERENCES purchase_receipts(id),reason VARCHAR(500) NOT NULL,actor VARCHAR(100) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE purchase_inspection_lines(
 inspection_id UUID NOT NULL REFERENCES purchase_inspections(id),variant_id UUID NOT NULL REFERENCES product_variants(id),
 accepted BIGINT NOT NULL CHECK(accepted>=0),rejected BIGINT NOT NULL CHECK(rejected>=0),CHECK(accepted+rejected>0),
 PRIMARY KEY(inspection_id,variant_id));
CREATE INDEX purchase_site ON purchase_orders(branch_id,created_at DESC);
CREATE TRIGGER purchase_receipts_immutable BEFORE UPDATE OR DELETE ON purchase_receipts FOR EACH ROW EXECUTE FUNCTION forbid_journal_rewrite();
CREATE TRIGGER purchase_inspections_immutable BEFORE UPDATE OR DELETE ON purchase_inspections FOR EACH ROW EXECUTE FUNCTION forbid_journal_rewrite();
CREATE TRIGGER purchase_inspection_lines_immutable BEFORE UPDATE OR DELETE ON purchase_inspection_lines FOR EACH ROW EXECUTE FUNCTION forbid_journal_rewrite();
CREATE FUNCTION protect_purchase_snapshots() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Purchase documents cannot be deleted'; END IF;
 IF (to_jsonb(NEW)-ARRAY['received','accepted','rejected','closed','version','close_reason','closed_by','closed_at'])
    IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['received','accepted','rejected','closed','version','close_reason','closed_by','closed_at'])
 THEN RAISE EXCEPTION 'Purchase snapshots are immutable'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER purchase_order_protected BEFORE UPDATE OR DELETE ON purchase_orders FOR EACH ROW EXECUTE FUNCTION protect_purchase_snapshots();
CREATE TRIGGER purchase_line_protected BEFORE UPDATE OR DELETE ON purchase_lines FOR EACH ROW EXECUTE FUNCTION protect_purchase_snapshots();
CREATE TRIGGER purchase_receipt_line_protected BEFORE UPDATE OR DELETE ON purchase_receipt_lines FOR EACH ROW EXECUTE FUNCTION protect_purchase_snapshots();
