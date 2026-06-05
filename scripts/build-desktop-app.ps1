param(
    [string]$JdkHome = '',
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$DefaultWindowsJdkHome = 'D:\CodeApps\Java-JDK\jdk-25.0.2'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$resolvedRoot = [System.IO.Path]::GetFullPath($projectRoot)
$tauriReleaseBackendDir = Join-Path $projectRoot 'cogniNote-agent-front\src-tauri\target\release\backend'

function Test-Jdk25Home {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $false
    }

    $candidateJava = Join-Path $Path 'bin\java.exe'
    $candidateJpackage = Join-Path $Path 'bin\jpackage.exe'
    if (-not (Test-Path -LiteralPath $candidateJava) -or -not (Test-Path -LiteralPath $candidateJpackage)) {
        return $false
    }

    $versionLine = cmd /c "`"$candidateJava`" -version 2>&1" | Select-Object -First 1
    return $versionLine -match 'version "25(\.|")'
}

function Resolve-JdkHome {
    param([string]$RequestedJdkHome)

    if (-not [string]::IsNullOrWhiteSpace($RequestedJdkHome)) {
        return $RequestedJdkHome
    }

    # GitHub Actions exposes setup-java through JAVA_HOME. On local machines,
    # JAVA_HOME may still point to an old JDK, so only trust it when it is JDK 25.
    if (Test-Jdk25Home $env:JAVA_HOME) {
        return $env:JAVA_HOME
    }

    if (Test-Jdk25Home $DefaultWindowsJdkHome) {
        return $DefaultWindowsJdkHome
    }

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        return $env:JAVA_HOME
    }

    return $DefaultWindowsJdkHome
}

$JdkHome = Resolve-JdkHome $JdkHome

function Assert-InProject {
    param([string]$Path)

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to operate outside project directory: $fullPath"
    }
}

function Invoke-Native {
    param(
        [string]$FilePath,
        [string[]]$ArgumentList = @()
    )

    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($ArgumentList -join ' ')"
    }
}

function Remove-DirectoryIfExists {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    Assert-InProject $Path

    # jpackage marks the generated Windows launcher as read-only. Tauri copies
    # that attribute into src-tauri/target/release/backend, so repeated builds
    # must clear attributes before deleting the cached resource tree.
    Get-ChildItem -LiteralPath $Path -Recurse -Force | ForEach-Object {
        if ($_.Attributes -band [System.IO.FileAttributes]::ReadOnly) {
            $_.Attributes = $_.Attributes -band (-bnot [System.IO.FileAttributes]::ReadOnly)
        }
    }
    Remove-Item -LiteralPath $Path -Recurse -Force
}

Push-Location $projectRoot
try {
    # Dot-source the verifier so the refreshed Cargo/MSVC environment remains
    # available for the following Tauri build in this same PowerShell process.
    . .\scripts\verify-desktop-toolchain.ps1 -JdkHome $JdkHome

    $env:JAVA_HOME = $JdkHome
    $env:Path = "$JdkHome\bin;$env:Path"

    if (-not $SkipTests) {
        Invoke-Native -FilePath 'mvn' -ArgumentList @('test')
    }

    # build-desktop-backend embeds Vue dist into the Spring Boot Jar and then
    # creates the full backend app-image directory consumed by Tauri resources.
    .\scripts\build-desktop-backend.ps1 -JdkHome $JdkHome -SkipTests

    Remove-DirectoryIfExists $tauriReleaseBackendDir

    Invoke-Native -FilePath 'npm' -ArgumentList @('--prefix', 'cogniNote-agent-front', 'run', 'desktop:build')

    Write-Host ''
    Write-Host 'Desktop build finished. Check cogniNote-agent-front/src-tauri/target/release/bundle/.'
} finally {
    Pop-Location
}
