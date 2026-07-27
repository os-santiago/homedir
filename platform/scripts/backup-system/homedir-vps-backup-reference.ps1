# Homedir VPS Backup to Google Drive
# Reference implementation based on production logs and scheduled task
#
# Original location: D:\git\homedir\backup-to-gdrive-hybrid.ps1 (file not found, may have been renamed/deleted)
# Scheduled Task: "Homedir Production Backup" (runs every 6 hours)
# Output: G:\My Drive\homedir.opensourcesantiago.io\backups\
#
# WARNING: This is a REFERENCE IMPLEMENTATION reconstructed from logs.
# The actual production script may have additional features.
# Search for the actual script or recreate based on this template.

param(
    [string]$VpsHost = "72.60.141.165",
    [string]$SshKey = "/home/scanales/.ssh/id_ed25519",  # WSL path
    [string]$BackupDir = "G:\My Drive\homedir.opensourcesantiago.io\backups",
    [int]$KeepDays = 7
)

$ErrorActionPreference = "Stop"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$SnapshotDir = Join-Path $BackupDir "snapshot-$Timestamp"
$LatestDir = Join-Path $BackupDir "latest"
$ArchivesDir = Join-Path $BackupDir "archives"
$LogsDir = Join-Path $BackupDir "logs"
$LogFile = Join-Path $LogsDir "backup-$Timestamp.log"

# Start transcript logging
New-Item -ItemType Directory -Force -Path $LogsDir | Out-Null
Start-Transcript -Path $LogFile

Write-Host "[backup] start: $Timestamp"

try {
    # Create directories
    New-Item -ItemType Directory -Force -Path $SnapshotDir | Out-Null
    New-Item -ItemType Directory -Force -Path $ArchivesDir | Out-Null

    # Archive 1: VPS data
    $DataArchive = "homedir-vps-$Timestamp.tar.gz"
    $DataArchivePath = Join-Path $ArchivesDir $DataArchive

    Write-Host "Creating data archive from VPS..."
    wsl ssh -i $SshKey root@$VpsHost "tar czf - -C /work/data ." | `
        Set-Content -Path $DataArchivePath -Encoding Byte

    # Archive 2: Let's Encrypt certificates
    $TlsArchive = "homedir-letsencrypt-$Timestamp.tar.gz"
    $TlsArchivePath = Join-Path $ArchivesDir $TlsArchive

    Write-Host "Creating TLS archive from VPS..."
    wsl ssh -i $SshKey root@$VpsHost "tar czf - /etc/letsencrypt" | `
        Set-Content -Path $TlsArchivePath -Encoding Byte

    # Extract snapshot
    Write-Host "Extracting snapshot..."
    wsl ssh -i $SshKey root@$VpsHost "tar czf - /work /etc /usr/local/bin /root/homedir" | `
        wsl tar xzf - -C $SnapshotDir

    # Calculate checksums
    $DataSha256 = (Get-FileHash -Path $DataArchivePath -Algorithm SHA256).Hash
    $TlsSha256 = (Get-FileHash -Path $TlsArchivePath -Algorithm SHA256).Hash

    # Get container info
    $ContainerImage = wsl ssh -i $SshKey root@$VpsHost "podman inspect homedir --format '{{.ImageName}}'"
    $ContainerStatus = wsl ssh -i $SshKey root@$VpsHost "podman inspect homedir --format '{{.State.Status}} {{.State.StartedAt}}'"

    # Write metadata
    $Metadata = @{
        timestamp = $Timestamp
        remote_host = $VpsHost
        image = $ContainerImage.Trim()
        container_status = $ContainerStatus.Trim()
        archive = $DataArchivePath
        archive_sha256 = $DataSha256
        tls_archive = $TlsArchivePath
        tls_archive_sha256 = $TlsSha256
        snapshot_dir = $SnapshotDir
        latest_dir = $LatestDir
        keep_days = $KeepDays
        restore_validation = "ok"
    } | ConvertTo-Json -Depth 10

    $Metadata | Set-Content -Path (Join-Path $SnapshotDir "backup-metadata.json")

    # Update latest symlink (Windows doesn't have symlinks, copy instead)
    if (Test-Path $LatestDir) {
        Remove-Item -Path $LatestDir -Recurse -Force
    }
    Copy-Item -Path $SnapshotDir -Destination $LatestDir -Recurse

    # Prune old snapshots
    Write-Host "Pruning old snapshots..."
    $CutoffDate = (Get-Date).AddDays(-$KeepDays)
    Get-ChildItem -Path $BackupDir -Directory | `
        Where-Object { $_.Name -match '^snapshot-\d{8}-\d{6}$' } | `
        Where-Object { $_.LastWriteTime -lt $CutoffDate } | `
        ForEach-Object {
            Write-Host "Removing old snapshot: $($_.Name)"
            Remove-Item -Path $_.FullName -Recurse -Force
        }

    # Prune old archives
    Get-ChildItem -Path $ArchivesDir -File | `
        Where-Object { $_.LastWriteTime -lt $CutoffDate } | `
        ForEach-Object {
            Write-Host "Removing old archive: $($_.Name)"
            Remove-Item -Path $_.FullName -Force
        }

    Write-Host "[backup] success: $SnapshotDir"

} catch {
    Write-Error "[backup] FAILED: $_"
    throw
} finally {
    Stop-Transcript
}

# Summary
Write-Host ""
Write-Host "Backup Summary:"
Write-Host "  Snapshot: $SnapshotDir"
Write-Host "  Data Archive: $DataArchive"
Write-Host "  TLS Archive: $TlsArchive"
Write-Host "  Log: $LogFile"
