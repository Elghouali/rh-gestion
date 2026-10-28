$ErrorActionPreference='Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try{
 & ./tools/init-local.ps1
 & ./tools/verify-backend.ps1
 if($LASTEXITCODE -ne 0){throw 'Backend verification failed'}
 Push-Location frontend/rh-web
 try{& npm.cmd ci;if($LASTEXITCODE -ne 0){throw 'npm ci failed'};& npm.cmd test -- --watch=false;if($LASTEXITCODE -ne 0){throw 'Angular tests failed'};& npm.cmd run build;if($LASTEXITCODE -ne 0){throw 'Angular build failed'}}finally{Pop-Location}
 & ./tools/ensure-inventory-db.ps1
 & docker compose up -d --build --wait
 if($LASTEXITCODE -ne 0){throw 'Docker startup failed; inspect docker compose logs'}
 Write-Output 'RH is available at http://localhost:4300'
}finally{Pop-Location}
