# Builds the Microsoft Store MSIX for AntiROTAid for Windows.
#
# Pipeline:
#   1. package.ps1                    -> dist\AntiROTAid  (self-contained payload)
#   2. build-store-msix.ps1 <params>  -> dist\AntiROTAid.msix  (Store-ready, unsigned)
#      The Store re-signs on submission; do NOT self-sign a Store upload.
#
# The three identity values come from your reserved app in Partner Center
# (Product identity page). The script fails if any is missing so no placeholder
# (REPLACE_WITH_*, CN=AntiROTAid, local identity) can ever ship.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File build-store-msix.ps1 `
#       -IdentityName        "1234YourPublisher.AntiROTAid" `
#       -Publisher           "CN=XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX" `
#       -PublisherDisplayName "Your Seller Display Name"

param(
    [Parameter(Mandatory = $true)] [string]$IdentityName,
    [Parameter(Mandatory = $true)] [string]$Publisher,
    [Parameter(Mandatory = $true)] [string]$PublisherDisplayName,
    [string]$Version = "1.0.0.0"
)

$ErrorActionPreference = "Stop"
$root     = $PSScriptRoot
$payload  = Join-Path $root "dist\AntiROTAid"
$staging  = Join-Path $root "dist\_msix_stage"
$template = Join-Path $root "msix\AppxManifest.template.xml"
$assets   = Join-Path $root "msix\Assets"
$outMsix  = Join-Path $root "dist\AntiROTAid.msix"

if (-not (Test-Path $payload))  { throw "Run package.ps1 first - $payload not found." }
if (-not (Test-Path $template)) { throw "Manifest template not found: $template" }

# --- Validate identity values (reject placeholders / empty) ---
function Assert-Real([string]$name, [string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { throw "$name is empty." }
    if ($value -match '{{|}}|REPLACE_WITH') { throw "$name still contains a placeholder token: $value" }
}
Assert-Real "IdentityName" $IdentityName
Assert-Real "Publisher"    $Publisher
Assert-Real "PublisherDisplayName" $PublisherDisplayName
if ($Publisher -notmatch '^CN=')          { throw "Publisher must start with 'CN=' (got: $Publisher)." }
if ($Publisher -eq 'CN=AntiROTAid')       { throw "Publisher is the temporary local value CN=AntiROTAid - use the Partner Center Publisher." }
if ($Version -notmatch '^\d+\.\d+\.\d+\.\d+$') { throw "Version must be x.x.x.x (got: $Version)." }

$makeappx = Get-ChildItem "C:\Program Files (x86)\Windows Kits\10\bin\*\x64\makeappx.exe" -ErrorAction SilentlyContinue |
    Sort-Object FullName | Select-Object -Last 1
if (-not $makeappx) { throw "makeappx.exe not found (install the Windows 10/11 SDK)." }

Write-Host "== Stage payload ==" -ForegroundColor Cyan
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path $staging | Out-Null
Copy-Item "$payload\*" $staging -Recurse -Force
# Merge the MSIX tile/store logos INTO staging\Assets (the payload may already have
# an Assets folder from packaged demo lessons, so copy the contents, not the folder).
$stageAssets = Join-Path $staging "Assets"
New-Item -ItemType Directory -Force -Path $stageAssets | Out-Null
Copy-Item "$assets\*" $stageAssets -Recurse -Force

Write-Host "== Strip debug symbols (.pdb) ==" -ForegroundColor Cyan
$pdbs = Get-ChildItem $staging -Recurse -Include *.pdb -File -ErrorAction SilentlyContinue
if ($pdbs) { $pdbs | Remove-Item -Force; Write-Host ("   removed {0} .pdb file(s)" -f $pdbs.Count) }
else       { Write-Host "   no .pdb present" }

Write-Host "== Render manifest from template ==" -ForegroundColor Cyan
# Literal string replaces (no regex) so identity values pass through verbatim.
$manifest = (Get-Content $template -Raw)
$manifest = $manifest.Replace('{{IDENTITY_NAME}}',          $IdentityName)
$manifest = $manifest.Replace('{{IDENTITY_PUBLISHER}}',      $Publisher)
$manifest = $manifest.Replace('{{PUBLISHER_DISPLAY_NAME}}',  $PublisherDisplayName)
$manifest = $manifest -replace '<Identity([\s\S]*?)Version="[^"]*"', ('<Identity$1Version="' + $Version + '"')
if ($manifest -match '{{|}}|REPLACE_WITH') { throw "Rendered manifest still contains a placeholder token." }
$manifestOut = Join-Path $staging "AppxManifest.xml"
Set-Content -Path $manifestOut -Value $manifest -Encoding UTF8

Write-Host "== Pack MSIX ==" -ForegroundColor Cyan
if (Test-Path $outMsix) { Remove-Item $outMsix -Force }
& $makeappx.FullName pack /d $staging /p $outMsix /o
if ($LASTEXITCODE -ne 0) { throw "makeappx failed" }
Remove-Item $staging -Recurse -Force

$sha = (Get-FileHash $outMsix -Algorithm SHA256).Hash
$sizeMB = "{0:N1} MB" -f ((Get-Item $outMsix).Length / 1MB)

Write-Host ""
Write-Host "==================== STORE MSIX BUILT ====================" -ForegroundColor Green
Write-Host ("  File               : {0} ({1})" -f $outMsix, $sizeMB)
Write-Host ("  Identity Name       : {0}" -f $IdentityName)
Write-Host ("  Publisher           : {0}" -f $Publisher)
Write-Host ("  PublisherDisplayName: {0}" -f $PublisherDisplayName)
Write-Host ("  Version             : {0}" -f $Version)
Write-Host ("  Architecture        : x64")
Write-Host ("  Capabilities        : runFullTrust")
Write-Host ("  PDB in package      : NO (stripped)")
Write-Host ("  SHA256              : {0}" -f $sha)
Write-Host "=========================================================" -ForegroundColor Green
Write-Host "Upload dist\AntiROTAid.msix to Partner Center. Do NOT self-sign it." -ForegroundColor Yellow
