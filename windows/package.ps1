# Assembles the standalone AntiROTAid for Windows payload:
#   dist\AntiROTAid\  = self-contained .NET app (WPF port of the Android app:
#   Home / Courses / Course detail / Study / Exams / Guide / Settings + overlay).
# Runs on any Windows 10/11 with nothing pre-installed. Local only, no network.
# Self-contained: bundles the REAL offline engine (engine_ara) + an embeddable
# CPython 3.12 runtime (engine\ + python\), copied by the csproj Publish target.
#
# Usage:  powershell -ExecutionPolicy Bypass -File package.ps1

$ErrorActionPreference = "Stop"
$root   = $PSScriptRoot
$proj   = Join-Path $root "AntiROTAid.Windows\AntiROTAid.Windows.csproj"
$outApp = Join-Path $root "dist\AntiROTAid"
$pubTmp = Join-Path $root "dist\_publish"
$dotnet = "C:\Program Files\dotnet\dotnet.exe"

Write-Host "== 1/2 Clean ==" -ForegroundColor Cyan
if (Test-Path $outApp) { Remove-Item $outApp -Recurse -Force }
if (Test-Path $pubTmp) { Remove-Item $pubTmp -Recurse -Force }
New-Item -ItemType Directory -Force -Path $outApp | Out-Null

Write-Host "== 2/2 Publish .NET (self-contained win-x64, no pdb) ==" -ForegroundColor Cyan
& $dotnet publish $proj -c Release -r win-x64 --self-contained true `
    -p:PublishSingleFile=false -o $pubTmp
if ($LASTEXITCODE -ne 0) { throw "publish failed" }
Copy-Item "$pubTmp\*" $outApp -Recurse -Force
if (Test-Path $pubTmp) { Remove-Item $pubTmp -Recurse -Force }

$size = "{0:N0} MB" -f ((Get-ChildItem $outApp -Recurse | Measure-Object Length -Sum).Sum / 1MB)
Write-Host "Package: $outApp  ($size)" -ForegroundColor Green
Write-Host "Run:     $outApp\AntiROTAid.exe"
