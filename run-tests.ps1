$ErrorActionPreference = "Stop"
& "$PSScriptRoot/gradlew.bat" :common:test
exit $LASTEXITCODE
