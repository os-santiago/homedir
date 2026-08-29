param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$OutDir = "reports/performance",
  [int[]]$Users = @(30, 80, 150),
  [int]$DurationSeconds = 40,
  [int]$ThinkMs = 300
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Write-Host "[reputation-iteration] base_url=$BaseUrl out_dir=$OutDir"

$hubPath = "/comunidad/reputation-hub"
$howPath = "/comunidad/reputation-hub/how"

foreach ($u in $Users) {
  $loadFile = Join-Path $OutDir ("reputation-hub-load-u{0}.txt" -f $u)
  Write-Host "[reputation-iteration] load-test users=$u duration=$DurationSeconds"
  python tools/load-test/community_capacity_probe.py `
    --base-url $BaseUrl `
    --users $u `
    --duration $DurationSeconds `
    --think-ms $ThinkMs `
    --extra-endpoint $hubPath `
    --extra-endpoint $howPath | Tee-Object -FilePath $loadFile | Out-Null
}

Write-Host "[reputation-iteration] lighthouse mobile+desktop"
npx --yes lighthouse "$BaseUrl$hubPath" --only-categories=performance --output=json --output-path="$OutDir/lh-hub-mobile.json" --chrome-flags="--headless=new --no-sandbox --disable-gpu" | Out-Null
npx --yes lighthouse "$BaseUrl$hubPath" --preset=desktop --only-categories=performance --output=json --output-path="$OutDir/lh-hub-desktop.json" --chrome-flags="--headless=new --no-sandbox --disable-gpu" | Out-Null
npx --yes lighthouse "$BaseUrl$howPath" --only-categories=performance --output=json --output-path="$OutDir/lh-how-mobile.json" --chrome-flags="--headless=new --no-sandbox --disable-gpu" | Out-Null
npx --yes lighthouse "$BaseUrl$howPath" --preset=desktop --only-categories=performance --output=json --output-path="$OutDir/lh-how-desktop.json" --chrome-flags="--headless=new --no-sandbox --disable-gpu" | Out-Null

Write-Host ""
Write-Host "[reputation-iteration] summary"
Get-ChildItem "$OutDir/reputation-hub-load-u*.txt" |
  Sort-Object Name |
  ForEach-Object {
    Write-Host ""
    Write-Host $_.Name
    Get-Content $_.FullName |
      Where-Object {
        $_ -match "error_rate=" -or
        $_ -match "rps=" -or
        $_ -match "^path=/comunidad/reputation-hub " -or
        $_ -match "^path=/comunidad/reputation-hub/how "
      } |
      ForEach-Object { Write-Host "  $_" }
  }

Get-ChildItem "$OutDir/lh-*.json" |
  Sort-Object Name |
  ForEach-Object {
    $j = Get-Content $_.FullName -Raw | ConvertFrom-Json
    $perf = [math]::Round(($j.categories.performance.score * 100), 1)
    $lcp = [math]::Round($j.audits.'largest-contentful-paint'.numericValue, 2)
    $tbt = [math]::Round($j.audits.'total-blocking-time'.numericValue, 2)
    Write-Host ("{0} perf={1} LCP_ms={2} TBT_ms={3}" -f $_.Name, $perf, $lcp, $tbt)
  }

Write-Host ""
Write-Host "[reputation-iteration] done"
