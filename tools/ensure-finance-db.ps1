$ErrorActionPreference='Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
 & docker compose up -d --wait postgres
 if($LASTEXITCODE -ne 0){throw 'PostgreSQL startup failed'}
 & docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres --set=finance_password="$RH_FINANCE_DB_PASSWORD" -f /opt/rh/finance.sql'
 if($LASTEXITCODE -ne 0){throw 'Finance database initialization failed'}
 Write-Output 'Finance database is available; existing roles and data preserved.'
} finally {Pop-Location}
