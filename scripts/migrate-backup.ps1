param(
    [Parameter(Mandatory = $true)]
    [string]$InputZip,

    [string]$TargetVersion,

    [string]$OutputZip,

    [switch]$KeepTemp
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Set-OrAddProperty {
    param(
        [Parameter(Mandatory = $true)] $Object,
        [Parameter(Mandatory = $true)] [string]$Name,
        [Parameter(Mandatory = $true)] $Value
    )

    if ($Object.PSObject.Properties[$Name]) {
        $Object.$Name = $Value
    }
    else {
        $Object | Add-Member -MemberType NoteProperty -Name $Name -Value $Value
    }
}

function Get-DefaultTargetVersion {
    param([string]$ScriptDir)

    $pomPath = Join-Path (Split-Path -Parent $ScriptDir) "quarkus-app\pom.xml"
    if (-not (Test-Path $pomPath)) {
        throw "No se pudo resolver quarkus-app/pom.xml desde $ScriptDir."
    }

    [xml]$pom = Get-Content -Path $pomPath -Raw
    $revision = $pom.project.properties.revision
    if ([string]::IsNullOrWhiteSpace($revision)) {
        throw "No se pudo leer <revision> desde $pomPath."
    }
    return $revision.Trim()
}

function Normalize-Talk {
    param(
        [Parameter(Mandatory = $true)] $Talk
    )

    if ($null -eq $Talk.day -or [int]$Talk.day -lt 1) {
        Set-OrAddProperty -Object $Talk -Name "day" -Value 1
    }

    if ($null -eq $Talk.durationMinutes -or [int]$Talk.durationMinutes -lt 0) {
        Set-OrAddProperty -Object $Talk -Name "durationMinutes" -Value 0
    }

    if (-not $Talk.PSObject.Properties["breakSlot"] -and $Talk.PSObject.Properties["break"]) {
        Set-OrAddProperty -Object $Talk -Name "breakSlot" -Value ([bool]$Talk.break)
    }

    if ($null -eq $Talk.speakers) {
        Set-OrAddProperty -Object $Talk -Name "speakers" -Value @()
    }
}

$resolvedInput = (Resolve-Path -Path $InputZip).Path
if (-not (Test-Path $resolvedInput)) {
    throw "No existe el ZIP de entrada: $InputZip"
}

if ([string]::IsNullOrWhiteSpace($TargetVersion)) {
    $TargetVersion = Get-DefaultTargetVersion -ScriptDir $PSScriptRoot
}

if ([string]::IsNullOrWhiteSpace($OutputZip)) {
    $inputFile = [System.IO.Path]::GetFileNameWithoutExtension($resolvedInput)
    $baseName = [regex]::Replace($inputFile, "_v\d+\.\d+(?:\.\d+)?$", "")
    $outName = "${baseName}_v${TargetVersion}.zip"
    $OutputZip = Join-Path (Split-Path -Parent $resolvedInput) $outName
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputZip)
$tmpDir = Join-Path ([System.IO.Path]::GetTempPath()) ("homedir-backup-migrate-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tmpDir | Out-Null

try {
    Expand-Archive -Path $resolvedInput -DestinationPath $tmpDir -Force

    $eventsPath = Join-Path $tmpDir "events.json"
    $speakersPath = Join-Path $tmpDir "speakers.json"

    if (-not (Test-Path $eventsPath)) {
        throw "El backup no contiene events.json"
    }
    if (-not (Test-Path $speakersPath)) {
        throw "El backup no contiene speakers.json"
    }

    $events = Get-Content -Path $eventsPath -Raw | ConvertFrom-Json -Depth 100
    $speakers = Get-Content -Path $speakersPath -Raw | ConvertFrom-Json -Depth 100

    foreach ($eventEntry in $events.PSObject.Properties) {
        $event = $eventEntry.Value

        $eventType = if ($event.PSObject.Properties["type"]) { [string]$event.type } else { "" }
        if ([string]::IsNullOrWhiteSpace($eventType)) {
            Set-OrAddProperty -Object $event -Name "type" -Value "OTHER"
        }

        if ($null -eq $event.days -or [int]$event.days -lt 1) {
            Set-OrAddProperty -Object $event -Name "days" -Value 1
        }

        $eventTimezone = if ($event.PSObject.Properties["timezone"]) { [string]$event.timezone } else { "" }
        if ([string]::IsNullOrWhiteSpace($eventTimezone)) {
            Set-OrAddProperty -Object $event -Name "timezone" -Value "UTC"
        }

        if ($null -eq $event.scenarios) {
            Set-OrAddProperty -Object $event -Name "scenarios" -Value @()
        }

        if ($null -eq $event.agenda) {
            Set-OrAddProperty -Object $event -Name "agenda" -Value @()
        }

        foreach ($talk in @($event.agenda)) {
            Normalize-Talk -Talk $talk
        }
    }

    foreach ($speakerEntry in $speakers.PSObject.Properties) {
        $speaker = $speakerEntry.Value

        if ($null -eq $speaker.talks) {
            Set-OrAddProperty -Object $speaker -Name "talks" -Value @()
        }

        foreach ($talk in @($speaker.talks)) {
            Normalize-Talk -Talk $talk
        }
    }

    ($events | ConvertTo-Json -Depth 100) | Set-Content -Path $eventsPath -Encoding UTF8
    ($speakers | ConvertTo-Json -Depth 100) | Set-Content -Path $speakersPath -Encoding UTF8

    if (Test-Path $resolvedOutput) {
        Remove-Item -Path $resolvedOutput -Force
    }

    Compress-Archive -Path $eventsPath, $speakersPath -DestinationPath $resolvedOutput -CompressionLevel Optimal

    Write-Host "OK: backup migrado"
    Write-Host "Input:  $resolvedInput"
    Write-Host "Output: $resolvedOutput"
    Write-Host "Version objetivo: $TargetVersion"
}
finally {
    if (-not $KeepTemp) {
        if (Test-Path $tmpDir) {
            Remove-Item -Path $tmpDir -Recurse -Force
        }
    }
    else {
        Write-Host "Temp: $tmpDir"
    }
}
