Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Push-Location (Split-Path $MyInvocation.MyCommand.Path -Parent) | Out-Null
Set-Location ..

mvn -f quarkus-app/pom.xml -T 1C -Dquarkus.devservices.enabled=false -DskipITs=true test

Pop-Location | Out-Null
