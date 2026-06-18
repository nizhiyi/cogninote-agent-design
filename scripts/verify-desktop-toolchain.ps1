param(
    [string]$JdkHome = ''
)

$ErrorActionPreference = 'Stop'
$DefaultWindowsJdkHome = 'D:\CodeApps\Java-JDK\jdk-25.0.2'

function Add-PathIfExists {
    param([string]$Path)

    if ((Test-Path -LiteralPath $Path) -and ($env:Path -notlike "*$Path*")) {
        $env:Path = "$Path;$env:Path"
    }
}

function Find-VsDevCmd {
    $vswhere = 'C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe'
    if (-not (Test-Path -LiteralPath $vswhere)) {
        return $null
    }

    $installPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if (-not $installPath) {
        return $null
    }

    $candidate = Join-Path $installPath 'Common7\Tools\VsDevCmd.bat'
    if (Test-Path -LiteralPath $candidate) {
        return $candidate
    }
    return $null
}

function Import-VsDevEnvironment {
    $vsDevCmd = Find-VsDevCmd
    if (-not $vsDevCmd) {
        return
    }

    # VsDevCmd is a batch file, so PowerShell cannot source it directly. Capture
    # the environment after cmd.exe loads it, then mirror those variables here.
    cmd /c "`"$vsDevCmd`" -arch=x64 -host_arch=x64 >nul && set" | ForEach-Object {
        $name, $value = $_ -split '=', 2
        if ($name -and $value) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

function Require-Command {
    param(
        [string]$Name,
        [string]$Hint
    )

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name not found. $Hint"
    }
}

function Require-File {
    param(
        [string]$Path,
        [string]$Hint
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Path does not exist. $Hint"
    }
}

function Test-Jdk25Home {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $false
    }

    $candidateJava = Join-Path $Path 'bin\java.exe'
    $candidateJlink = Join-Path $Path 'bin\jlink.exe'
    $candidateJpackage = Join-Path $Path 'bin\jpackage.exe'
    if (-not (Test-Path -LiteralPath $candidateJava) -or
        -not (Test-Path -LiteralPath $candidateJlink) -or
        -not (Test-Path -LiteralPath $candidateJpackage)) {
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
$javaExe = Join-Path $JdkHome 'bin\java.exe'
$jlinkExe = Join-Path $JdkHome 'bin\jlink.exe'
$jpackageExe = Join-Path $JdkHome 'bin\jpackage.exe'
$cargoBin = Join-Path $env:USERPROFILE '.cargo\bin'

Add-PathIfExists $cargoBin
Import-VsDevEnvironment

# Desktop delivery depends on jpackage from JDK 25. Use the explicit JDK path
# instead of trusting a shell PATH that may still contain JDK 8/17.
Require-File $javaExe 'Check the JDK 25 install path or pass -JdkHome.'
Require-File $jlinkExe 'Check that the path points to a full JDK, not a JRE.'
Require-File $jpackageExe 'Check that the path points to a full JDK, not a JRE.'

Require-Command mvn 'Install Maven 3.9+ and add it to PATH.'
Require-Command node 'Install Node.js 20.19.6 or a compatible version.'
Require-Command npm 'Install npm 10.8.2 or a compatible version.'
Require-Command cargo 'Install Rust stable with rustup.'
Require-Command rustc 'Install Rust stable with rustup.'
Require-Command cl 'Install Visual Studio Build Tools and select Desktop development with C++.'
Require-Command link 'Install Visual Studio Build Tools and select Desktop development with C++.'

Write-Host 'Desktop toolchain check passed.'
Write-Host "JAVA_HOME = $JdkHome"
& $javaExe -version
Write-Host "jlink = $(& $jlinkExe --version)"
Write-Host "jpackage = $(& $jpackageExe --version)"
Write-Host "node = $(node --version)"
Write-Host "npm = $(npm --version)"
Write-Host "rustc = $(rustc --version)"
Write-Host "cargo = $(cargo --version)"
Write-Host "cl = $((Get-Command cl).Source)"
Write-Host "link = $((Get-Command link).Source)"

Write-Host ''
Write-Host 'Note: Windows Tauri packaging also needs MSVC Build Tools. The installer is configured to download the WebView2 bootstrapper.'
