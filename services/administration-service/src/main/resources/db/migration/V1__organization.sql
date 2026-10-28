CREATE TABLE branches (
 id UUID PRIMARY KEY,
 code VARCHAR(24) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL,
 type VARCHAR(12) NOT NULL CHECK(type IN ('DEPOT','STORE')),
 active BOOLEAN NOT NULL DEFAULT true
);
CREATE TABLE branch_assignments (
 subject VARCHAR(100) NOT NULL,
 branch_id UUID NOT NULL REFERENCES branches(id),
 PRIMARY KEY(subject,branch_id)
);
