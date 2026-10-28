\set ON_ERROR_STOP on
SELECT format('CREATE ROLE rh_inventory LOGIN PASSWORD %L', :'inventory_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='rh_inventory')
\gexec
SELECT 'CREATE DATABASE rh_inventory OWNER rh_inventory'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname='rh_inventory')
\gexec
