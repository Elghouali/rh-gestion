$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$target=Join-Path $root '.env'
if(Test-Path -LiteralPath $target){Write-Output 'Local configuration already exists; preserved.';exit 0}
$names=@('RH_DB_ROOT_PASSWORD','RH_ADMIN_DB_PASSWORD','RH_IDENTITY_DB_PASSWORD','RH_KEYCLOAK_ADMIN_PASSWORD','RH_OIDC_CLIENT_SECRET','RH_OWNER_PASSWORD','RH_CASHIER_PASSWORD','RH_STOCK_PASSWORD','RH_RABBIT_PASSWORD')
$lines=foreach($name in $names){$bytes=[byte[]]::new(24);[Security.Cryptography.RandomNumberGenerator]::Fill($bytes);$name+'='+[Convert]::ToHexString($bytes).ToLowerInvariant()}
$socketCandidate=Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Codex'
if($IsWindows -and (Test-Path -LiteralPath $socketCandidate -PathType Container) -and $socketCandidate.Length -lt 70){$lines += 'RH_JAVA_SOCKET_TMP='+$socketCandidate}
[IO.File]::WriteAllLines($target,$lines,[Text.UTF8Encoding]::new($false))
Write-Output 'Generated unique local secrets in .env (ignored by Git). Demo logins: rachid.owner, ibrahim, rachid.stock.'
