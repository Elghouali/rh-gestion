CREATE TABLE products(
 id UUID PRIMARY KEY,name VARCHAR(120) NOT NULL,brand VARCHAR(80) NOT NULL,
 category VARCHAR(12) NOT NULL CHECK(category IN ('PHONE','ACCESSORY')),
 active BOOLEAN NOT NULL DEFAULT true,version BIGINT NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX product_identity ON products(lower(name),lower(brand),category);
CREATE TABLE product_variants(
 id UUID PRIMARY KEY,product_id UUID NOT NULL REFERENCES products(id),sku VARCHAR(64) NOT NULL UNIQUE,
 color VARCHAR(80) NOT NULL,ram_gb INTEGER CHECK(ram_gb>0),storage_gb INTEGER CHECK(storage_gb>0),
 wholesale_price NUMERIC(12,2) NOT NULL CHECK(wholesale_price>=0),retail_price NUMERIC(12,2) NOT NULL CHECK(retail_price>=0),
 minimum_stock INTEGER NOT NULL DEFAULT 0 CHECK(minimum_stock>=0),active BOOLEAN NOT NULL DEFAULT true,version BIGINT NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX variant_identity ON product_variants(product_id,lower(color),COALESCE(ram_gb,0),COALESCE(storage_gb,0));
CREATE TABLE stock_balances(
 branch_id UUID NOT NULL,variant_id UUID NOT NULL REFERENCES product_variants(id),
 bucket VARCHAR(10) NOT NULL CHECK(bucket IN('SELLABLE','TRANSIT')),
 quantity BIGINT NOT NULL DEFAULT 0 CHECK(quantity>=0),PRIMARY KEY(branch_id,variant_id,bucket));
CREATE TABLE stock_transfers(
 id UUID PRIMARY KEY,source_id UUID NOT NULL,destination_id UUID NOT NULL,
 source_name VARCHAR(160) NOT NULL,destination_name VARCHAR(160) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),CHECK(source_id<>destination_id));
CREATE TABLE transfer_lines(
 transfer_id UUID NOT NULL REFERENCES stock_transfers(id),variant_id UUID NOT NULL REFERENCES product_variants(id),
 sku VARCHAR(64) NOT NULL,label TEXT NOT NULL,quantity BIGINT NOT NULL CHECK(quantity>0),
 received BIGINT NOT NULL DEFAULT 0 CHECK(received>=0),lost BIGINT NOT NULL DEFAULT 0 CHECK(lost>=0),
 PRIMARY KEY(transfer_id,variant_id),CHECK(received+lost<=quantity));
CREATE TABLE stock_movements(
 id UUID PRIMARY KEY,kind VARCHAR(24) NOT NULL CHECK(kind IN('OPENING','ADJUSTMENT','TRANSFER_OUT','TRANSFER_IN','TRANSIT_LOSS')),
 reason VARCHAR(500) NOT NULL,actor VARCHAR(100) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 transfer_id UUID REFERENCES stock_transfers(id));
CREATE TABLE stock_entries(
 id UUID PRIMARY KEY,movement_id UUID NOT NULL REFERENCES stock_movements(id),
 branch_id UUID NOT NULL,variant_id UUID NOT NULL REFERENCES product_variants(id),
 bucket VARCHAR(10) NOT NULL CHECK(bucket IN('SELLABLE','TRANSIT')),delta BIGINT NOT NULL,
 quantity_after BIGINT NOT NULL CHECK(quantity_after>=0),sku VARCHAR(64) NOT NULL,label TEXT NOT NULL,
 unit_cost NUMERIC(14,4) CHECK(unit_cost>=0));
CREATE INDEX stock_history ON stock_entries(branch_id,variant_id,movement_id);
CREATE INDEX movements_date ON stock_movements(created_at DESC,id);
CREATE INDEX transfer_destination ON stock_transfers(destination_id,source_id);
CREATE TABLE stock_openings(
 branch_id UUID NOT NULL,variant_id UUID NOT NULL REFERENCES product_variants(id),movement_id UUID NOT NULL REFERENCES stock_movements(id),
 PRIMARY KEY(branch_id,variant_id));
CREATE TABLE catalogue_audit(
 id UUID PRIMARY KEY,aggregate_id UUID NOT NULL,action VARCHAR(30) NOT NULL,actor VARCHAR(100) NOT NULL,
 payload JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE command_receipts(
 actor VARCHAR(100) NOT NULL,operation VARCHAR(160) NOT NULL,command_key VARCHAR(100) NOT NULL,
 request_hash CHAR(64) NOT NULL,response JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(actor,operation,command_key));
CREATE TABLE outbox_events(
 event_id UUID PRIMARY KEY,event_type VARCHAR(160) NOT NULL,actor VARCHAR(100) NOT NULL,payload JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),published_at TIMESTAMPTZ);
CREATE FUNCTION forbid_journal_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Journal entries are immutable'; END; $$;
CREATE TRIGGER stock_entries_immutable BEFORE UPDATE OR DELETE ON stock_entries FOR EACH ROW EXECUTE FUNCTION forbid_journal_rewrite();
CREATE TRIGGER stock_movements_immutable BEFORE UPDATE OR DELETE ON stock_movements FOR EACH ROW EXECUTE FUNCTION forbid_journal_rewrite();
CREATE TRIGGER catalogue_audit_immutable BEFORE UPDATE OR DELETE ON catalogue_audit FOR EACH ROW EXECUTE FUNCTION forbid_journal_rewrite();
