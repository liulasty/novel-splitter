$ErrorActionPreference = "Stop"

$Version = mvn help:evaluate -Dexpression=project.version -q -DforceStdout

Write-Output $Version
