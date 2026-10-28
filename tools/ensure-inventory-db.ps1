$ErrorActionPreference='Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
 & docker compose up -d --wait postgres
 if($LASTEXITCODE -ne 0){throw 'PostgreSQL startup failed'}
 & docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres --set=inventory_password="$RH_INVENTORY_DB_PASSWORD" -f /opt/rh/inventory.sql'
 if($LASTEXITCODE -ne 0){throw 'Inventory database initialization failed'}
 Write-Output 'Inventory database is available; existing roles and data preserved.'
} finally {Pop-Location}
