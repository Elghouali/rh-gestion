$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$previous=$env:JAVA_TOOL_OPTIONS
Push-Location $root
try {
 $config=Join-Path $root '.env'
 if($IsWindows -and (Test-Path -LiteralPath $config)){
  $line=Get-Content -LiteralPath $config | Where-Object { $_ -match '^RH_JAVA_SOCKET_TMP=' } | Select-Object -First 1
  if($line){
   $socketDir=$line.Substring('RH_JAVA_SOCKET_TMP='.Length)
   if($socketDir){
    if(-not (Test-Path -LiteralPath $socketDir -PathType Container)){throw 'RH_JAVA_SOCKET_TMP must be an existing short local directory'}
    if($socketDir.Contains('"')){throw 'Invalid socket directory'}
    $env:JAVA_TOOL_OPTIONS=($previous+' -Djdk.net.unixdomain.tmpdir="'+$socketDir+'"').Trim()
   }
  }
 }
 & ./mvnw.cmd -B verify
 if($LASTEXITCODE -ne 0){throw 'Backend verification failed'}
} finally {
 $env:JAVA_TOOL_OPTIONS=$previous
 Pop-Location
}
