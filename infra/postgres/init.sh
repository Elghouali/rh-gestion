#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
 --set=admin_password="$RH_ADMIN_DB_PASSWORD" \
 --set=identity_password="$RH_IDENTITY_DB_PASSWORD" \
 --set=finance_password="$RH_FINANCE_DB_PASSWORD" \
 --set=inventory_password="$RH_INVENTORY_DB_PASSWORD" <<'SQL'
CREATE USER rh_admin WITH PASSWORD :'admin_password';
CREATE DATABASE rh_admin OWNER rh_admin;
CREATE USER rh_identity WITH PASSWORD :'identity_password';
CREATE DATABASE rh_identity OWNER rh_identity;
CREATE USER rh_inventory WITH PASSWORD :'inventory_password';
CREATE DATABASE rh_inventory OWNER rh_inventory;
CREATE USER rh_finance WITH PASSWORD :'finance_password';
CREATE DATABASE rh_finance OWNER rh_finance;
SQL
