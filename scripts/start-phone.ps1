# Open the existing project phone in a standalone window; Android Studio is not required.
$ErrorActionPreference = 'Stop'
$iptvSdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$iptvEmulatorPath = Join-Path $iptvSdkRoot 'emulator/emulator.exe'
if (-not (Test-Path -LiteralPath $iptvEmulatorPath -PathType Leaf)) {
    throw 'Android Emulator is missing. Set ANDROID_HOME to your installed Android SDK.'
}

$iptvRunningEmulators = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -like 'qemu-system-*.exe' })
if ($iptvRunningEmulators.Count -gt 0) {
    Write-Host 'An emulator is already running. Use its window, or close it before starting this phone.'
    return
}

$iptvAvds = @(& $iptvEmulatorPath -list-avds)
if ($LASTEXITCODE -ne 0 -or 'Iptv_Phone_API_34' -notin $iptvAvds) {
    throw 'The Iptv_Phone_API_34 device is missing. Create it using the project environment guide.'
}

$iptvLogDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) '.tools'
New-Item -ItemType Directory -Force -Path $iptvLogDirectory | Out-Null
# A visible interactive emulator is intentional. No driver, firewall or host power changes.
Start-Process -FilePath $iptvEmulatorPath -ArgumentList @(
    '-avd', 'Iptv_Phone_API_34', '-gpu', 'swiftshader', '-memory', '2048', '-no-snapshot'
) -WindowStyle Normal -RedirectStandardOutput (Join-Path $iptvLogDirectory 'phone-emulator.log') -RedirectStandardError (Join-Path $iptvLogDirectory 'phone-emulator-errors.log')
Write-Host 'Opening the phone in its own window. Close that window when you are finished.'
