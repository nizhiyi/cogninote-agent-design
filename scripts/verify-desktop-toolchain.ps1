param(
    [string]$JdkHome = 'D:\CodeApps\Java-JDK\jdk-25.0.2'
)

$ErrorActionPreference = 'Stop'

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

$javaExe = Join-Path $JdkHome 'bin\java.exe'
$jpackageExe = Join-Path $JdkHome 'bin\jpackage.exe'

# Desktop delivery depends on jpackage from JDK 25. Use the explicit JDK path
# instead of trusting a shell PATH that may still contain JDK 8/17.
Require-File $javaExe 'Check the JDK 25 install path or pass -JdkHome.'
Require-File $jpackageExe 'Check that the path points to a full JDK, not a JRE.'

Require-Command mvn 'Install Maven 3.9+ and add it to PATH.'
Require-Command node 'Install Node.js 20.19.6 or a compatible version.'
Require-Command npm 'Install npm 10.8.2 or a compatible version.'
Require-Command cargo 'Install Rust stable with rustup.'
Require-Command rustc 'Install Rust stable with rustup.'

Write-Host 'Desktop toolchain check passed.'
Write-Host "JAVA_HOME = $JdkHome"
& $javaExe -version
Write-Host "jpackage = $(& $jpackageExe --version)"
Write-Host "node = $(node --version)"
Write-Host "npm = $(npm --version)"
Write-Host "rustc = $(rustc --version)"
Write-Host "cargo = $(cargo --version)"

Write-Host ''
Write-Host 'Note: Windows Tauri packaging also needs MSVC Build Tools. The installer is configured to download the WebView2 bootstrapper.'
