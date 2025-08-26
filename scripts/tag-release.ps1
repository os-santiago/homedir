#!/usr/bin/env pwsh
$ErrorActionPreference = 'Stop'

$repoRoot = (git rev-parse --show-toplevel).Trim()
$pomFile = Join-Path $repoRoot 'quarkus-app/pom.xml'
$readmeFile = Join-Path $repoRoot 'README.md'

[xml]$pom = Get-Content $pomFile
$version = $pom.project.version
if ([string]::IsNullOrWhiteSpace($version)) {
    Write-Error "Could not determine version from $pomFile"
    exit 1
}

$description = Get-Content $readmeFile | Where-Object { $_ -notmatch '^(#|\[|\s*$)' } | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($description)) {
    Write-Error "Could not determine description from $readmeFile"
    exit 1
}

$tag = $version

git rev-parse $tag *> $null
if ($LASTEXITCODE -eq 0) {
    Write-Host "Tag $tag already exists"
    exit 0
}

git tag -a $tag -m $description
git push origin $tag
