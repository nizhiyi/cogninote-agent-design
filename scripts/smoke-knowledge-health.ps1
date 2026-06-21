param(
    [string]$JavaHome = "D:\CodeApps\Java-JDK\jdk-25.0.2"
)

$ErrorActionPreference = "Stop"

if (Test-Path -LiteralPath $JavaHome) {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

mvn -q "-Dtest=com.itqianchen.agentdesign.knowledge.KnowledgeFolderControllerTests#smokeImportSearchHealthDeleteAndRunCleanup" test
