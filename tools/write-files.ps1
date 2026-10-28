param([Parameter(Mandatory=$true)][string]$Payload)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$inputStream = [IO.MemoryStream]::new([Convert]::FromBase64String($Payload))
$gzip = [IO.Compression.GZipStream]::new($inputStream, [IO.Compression.CompressionMode]::Decompress)
$reader = [IO.StreamReader]::new($gzip, [Text.Encoding]::UTF8)
try { $files = ConvertFrom-Json -InputObject $reader.ReadToEnd() -AsHashtable } finally { $reader.Dispose(); $gzip.Dispose(); $inputStream.Dispose() }
foreach ($name in $files.Keys) {
  $target = [IO.Path]::GetFullPath((Join-Path $root $name))
  if (-not $target.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Path outside repository' }
  [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
  [IO.File]::WriteAllText($target, $files[$name], [Text.UTF8Encoding]::new($false))
}
Write-Output ('Files written: ' + $files.Count)
