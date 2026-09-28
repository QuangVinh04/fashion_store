$ErrorActionPreference = 'Stop'

& "$PSScriptRoot\require-jdk21.ps1"
& "$PSScriptRoot\..\mvnw.cmd" -B -DskipTests package
exit $LASTEXITCODE
