$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$target=Join-Path $root '.env'
$lines=[Collections.Generic.List[string]]::new()
if(Test-Path -LiteralPath $target){foreach($line in [IO.File]::ReadAllLines($target)){$lines.Add($line)}}
$names=@('RH_DB_ROOT_PASSWORD','RH_ADMIN_DB_PASSWORD','RH_IDENTITY_DB_PASSWORD','RH_INVENTORY_DB_PASSWORD','RH_KEYCLOAK_ADMIN_PASSWORD','RH_OIDC_CLIENT_SECRET','RH_OWNER_PASSWORD','RH_CASHIER_PASSWORD','RH_STOCK_PASSWORD','RH_RABBIT_PASSWORD')
$added=0
foreach($name in $names){
 if(-not ($lines | Where-Object {$_ -match ('^'+[regex]::Escape($name)+'=')})){
  $bytes=[byte[]]::new(24);[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
  $lines.Add($name+'='+[Convert]::ToHexString($bytes).ToLowerInvariant());$added++
 }
}
$socketCandidate=Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Codex'
if($IsWindows -and (Test-Path -LiteralPath $socketCandidate -PathType Container) -and $socketCandidate.Length -lt 70 -and -not ($lines | Where-Object {$_ -match '^RH_JAVA_SOCKET_TMP='})){$lines.Add('RH_JAVA_SOCKET_TMP='+$socketCandidate)}
[IO.File]::WriteAllLines($target,$lines,[Text.UTF8Encoding]::new($false))
Write-Output ('Local settings preserved; '+$added+' missing secret(s) generated. Demo users: rachid.owner, ibrahim, rachid.stock.')
