$ErrorActionPreference = "Stop"

$jdk21 = "C:\Program Files\Java\jdk-21"
if (Test-Path $jdk21) {
    $env:JAVA_HOME = $jdk21
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

& "$PSScriptRoot\..\mvnw.cmd" -B test
exit $LASTEXITCODE
