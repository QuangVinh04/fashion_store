$ErrorActionPreference = "Stop"

& "$PSScriptRoot\..\mvnw.cmd" -B -DskipTests compile
exit $LASTEXITCODE
