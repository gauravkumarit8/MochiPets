# Builds the debug APK on Windows. Run from the repo root:  powershell -ExecutionPolicy Bypass -File scripts\build_apk.ps1
$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not (Test-Path "local.properties")) {
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
    if (-not (Test-Path $sdk)) { throw "Android SDK not found. Install Android Studio or set ANDROID_HOME. See README.md" }
    $sdkEsc = $sdk -replace '\\', '/'
    "sdk.dir=$sdkEsc" | Set-Content -Path "local.properties" -Encoding ASCII
    Write-Host "Wrote local.properties (sdk.dir=$sdkEsc)"
}

& .\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

New-Item -ItemType Directory -Force -Path dist | Out-Null
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "dist\MochiPets-debug.apk" -Force
Get-Item "dist\MochiPets-debug.apk" | Format-List Name, Length
Write-Host "APK ready: dist\MochiPets-debug.apk"
