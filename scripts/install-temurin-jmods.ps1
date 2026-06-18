param(
    [ValidateSet('x64', 'aarch64')]
    [string]$Architecture = 'x64',

    [string]$JavaFeatureVersion = '25',

    [string]$JdkHome = $env:JAVA_HOME,

    [string]$DestinationRoot = $(Join-Path ([System.IO.Path]::GetTempPath()) 'cogninote-temurin-jmods')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($JdkHome)) {
    throw 'JAVA_HOME is empty. Run actions/setup-java first or pass -JdkHome.'
}

$existingJmodsDir = Join-Path $JdkHome 'jmods'
$existingJavaBase = Join-Path $existingJmodsDir 'java.base.jmod'
if (Test-Path -LiteralPath $existingJavaBase) {
    if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_ENV)) {
        "JDK_JMODS_DIR=$existingJmodsDir" | Out-File -FilePath $env:GITHUB_ENV -Encoding utf8 -Append
    }
    Write-Host "JDK JMODs already available: $existingJmodsDir"
    exit 0
}

$apiUrl = "https://api.adoptium.net/v3/assets/latest/$JavaFeatureVersion/hotspot?architecture=$Architecture&image_type=jmods&os=windows&vendor=eclipse"
$assets = @(Invoke-RestMethod -Uri $apiUrl)
if ($assets.Count -eq 0) {
    throw "No Temurin JMODs asset found from Adoptium API: $apiUrl"
}

$asset = $assets[0]
$downloadUrl = $asset.binary.package.link
$expectedChecksum = $asset.binary.package.checksum
$archivePath = Join-Path ([System.IO.Path]::GetTempPath()) $asset.binary.package.name
$extractDir = Join-Path ([System.IO.Path]::GetTempPath()) "cogninote-temurin-jmods-$([System.Guid]::NewGuid())"
$jmodsDir = Join-Path $DestinationRoot 'jmods'

Write-Host "Downloading Temurin JMODs: $downloadUrl"
Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath

$actualChecksum = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash.ToLowerInvariant()
if ($actualChecksum -ne $expectedChecksum.ToLowerInvariant()) {
    throw "Temurin JMODs checksum mismatch. Expected $expectedChecksum but got $actualChecksum."
}

Expand-Archive -LiteralPath $archivePath -DestinationPath $extractDir -Force
$javaBase = Get-ChildItem -LiteralPath $extractDir -Recurse -Filter 'java.base.jmod' -File | Select-Object -First 1
if (-not $javaBase) {
    throw "Downloaded Temurin JMODs archive did not contain java.base.jmod."
}

New-Item -ItemType Directory -Force -Path $jmodsDir | Out-Null
Get-ChildItem -LiteralPath $javaBase.Directory.FullName -Filter '*.jmod' -File |
    Copy-Item -Destination $jmodsDir -Force

$installedJavaBase = Join-Path $jmodsDir 'java.base.jmod'
if (-not (Test-Path -LiteralPath $installedJavaBase)) {
    throw "Temurin JMODs installation failed: $installedJavaBase was not created."
}

if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_ENV)) {
    "JDK_JMODS_DIR=$jmodsDir" | Out-File -FilePath $env:GITHUB_ENV -Encoding utf8 -Append
}

Write-Host "Installed Temurin JMODs: $jmodsDir"
