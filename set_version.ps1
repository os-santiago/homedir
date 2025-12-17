param (
    [Parameter(Mandatory=$true)]
    [string]$Version
)

Write-Host "Updating project to version $Version"

# Update pom.xml
& ./quarkus-app/mvnw.cmd -f quarkus-app/pom.xml versions:set "-DnewVersion=$Version" "-DgenerateBackupPoms=false"

# Update application.properties
$propFile = "quarkus-app/src/main/resources/application.properties"
(Get-Content $propFile) | ForEach-Object {
    if ($_ -match "quarkus.application.version=") {
        "quarkus.application.version=$Version"
    } else {
        $_
    }
} | Set-Content $propFile -Encoding UTF8

Write-Host "Version updated to $Version in pom.xml and application.properties"
Write-Host "You can now commit and tag:"
Write-Host "  git commit -am 'chore: bump to $Version'"
Write-Host "  git tag v$Version"
Write-Host "  git push origin main v$Version"
