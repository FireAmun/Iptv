# Activate the project's local Android tools in this PowerShell process.
# Usage from the project root: . ./scripts/android-env.ps1
$iptvProjectRoot = Split-Path -Parent $PSScriptRoot
$iptvAndroidStudioPath = Join-Path $iptvProjectRoot '.tools/ide/android-studio'
$iptvGradlePath = Join-Path $iptvProjectRoot '.tools/gradle/gradle-9.6.0'
$iptvCommandLinePath = Join-Path $iptvProjectRoot '.tools/android-cli/cmdline-tools'
$iptvAndroidSdkPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$iptvSdkManagerPath = Join-Path $iptvAndroidSdkPath 'cmdline-tools/22.0'
$iptvBundledJavaPath = Join-Path $iptvAndroidStudioPath 'jbr'

foreach ($iptvRequiredFile in @(
    (Join-Path $iptvGradlePath 'bin/gradle.bat'),
    (Join-Path $iptvCommandLinePath 'bin/android.exe'),
    (Join-Path $iptvSdkManagerPath 'bin/sdkmanager.bat'),
    (Join-Path $iptvAndroidSdkPath 'platform-tools/adb.exe')
)) {
    if (-not (Test-Path -LiteralPath $iptvRequiredFile -PathType Leaf)) {
        throw "Android tool setup is incomplete: $iptvRequiredFile"
    }
}

if (Test-Path -LiteralPath (Join-Path $iptvBundledJavaPath 'bin/java.exe')) {
    $env:JAVA_HOME = $iptvBundledJavaPath
} elseif (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'No working JDK found. Complete Android Studio extraction or set JAVA_HOME to an installed JDK.'
}

$env:ANDROID_HOME = $iptvAndroidSdkPath
$env:ANDROID_SDK_ROOT = $iptvAndroidSdkPath
$iptvAndroidToolBins = @(
    (Join-Path $env:JAVA_HOME 'bin'),
    (Join-Path $iptvGradlePath 'bin'),
    (Join-Path $iptvSdkManagerPath 'bin'),
    (Join-Path $iptvCommandLinePath 'bin'),
    (Join-Path $iptvAndroidSdkPath 'platform-tools'),
    (Join-Path $iptvAndroidSdkPath 'emulator')
)
$iptvExistingPathEntries = @($env:PATH -split ';' | Where-Object { $_ -and $_ -notin $iptvAndroidToolBins })
$env:PATH = ($iptvAndroidToolBins + $iptvExistingPathEntries) -join ';'

[pscustomobject]@{
    JavaHome = $env:JAVA_HOME
    AndroidSdk = $iptvAndroidSdkPath
    Gradle = $iptvGradlePath
    CommandLineTools = $iptvCommandLinePath
    SdkManager = $iptvSdkManagerPath
    AndroidStudio = $iptvAndroidStudioPath
}
