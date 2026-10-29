CREATE TABLE suppliers(id UUID PRIMARY KEY,name VARCHAR(160) NOT NULL,phone VARCHAR(40) NOT NULL,active BOOLEAN NOT NULL DEFAULT true,version BIGINT NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX supplier_name ON suppliers(lower(name));
CREATE TABLE supplier_audit(id UUID PRIMARY KEY,supplier_id UUID NOT NULL REFERENCES suppliers(id),action VARCHAR(24) NOT NULL,actor VARCHAR(100) NOT NULL,payload JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE command_receipts(actor VARCHAR(100) NOT NULL,operation VARCHAR(160) NOT NULL,command_key VARCHAR(100) NOT NULL,request_hash CHAR(64) NOT NULL,response JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),PRIMARY KEY(actor,operation,command_key));
CREATE TABLE outbox_events(event_id UUID PRIMARY KEY,event_type VARCHAR(160) NOT NULL,actor VARCHAR(100) NOT NULL,payload JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),published_at TIMESTAMPTZ);
CREATE FUNCTION forbid_audit_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Audit is immutable'; END; $$;
CREATE TRIGGER supplier_audit_immutable BEFORE UPDATE OR DELETE ON supplier_audit FOR EACH ROW EXECUTE FUNCTION forbid_audit_rewrite();
