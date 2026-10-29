\set ON_ERROR_STOP on
SELECT format('CREATE ROLE rh_finance LOGIN PASSWORD %L', :'finance_password') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='rh_finance')
\gexec
SELECT 'CREATE DATABASE rh_finance OWNER rh_finance' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname='rh_finance')
\gexec
