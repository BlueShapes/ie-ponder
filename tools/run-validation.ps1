param([string]$LogPath = 'build/native-navigation-validation.log', [switch]$Captions, [switch]$Layout,
      [switch]$Presentation, [switch]$Coverage)
$ErrorActionPreference = 'Stop'
$projectPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectPath
$taskMutex = [Threading.Mutex]::new($false, 'Local\PonderSeriesValidation')
$acquired = $false
try {
    Write-Output 'Waiting for Local\PonderSeriesValidation...'
    try { $acquired = $taskMutex.WaitOne([TimeSpan]::FromMinutes(30)) }
    catch [Threading.AbandonedMutexException] { $acquired = $true }
    if (!$acquired) { throw 'Timed out waiting for the shared Minecraft validation mutex' }
    $existing = Get-CimInstance Win32_Process | Where-Object {
        $_.Name -match '^javaw?\.exe$' -and
        $_.CommandLine -like '*ponder-series*' -and
        $_.CommandLine -match 'devlaunch|BootstrapLauncher|forgeclientdev'
    }
    if ($existing) { throw "Another Ponder series client is already running: $($existing.ProcessId -join ', ')" }
    New-Item -ItemType Directory -Force -Path 'build/validation-run' | Out-Null
    if (!(Test-Path -LiteralPath 'build/validation-run/options.txt')) {
        [IO.File]::WriteAllLines((Join-Path $projectPath 'build/validation-run/options.txt'),
            @('onboardAccessibility:false', 'lang:ja_jp', 'guiScale:2', 'soundCategory_master:0.0',
              'renderDistance:2', 'simulationDistance:5', 'maxFps:60', 'pauseOnLostFocus:false'),
            [Text.UTF8Encoding]::new($false))
    }
    $validationArgs = @('-PvalidateGuide')
    if ($Coverage -and ($Captions -or $Layout -or $Presentation)) {
        throw 'Coverage validation is a focused scope and cannot be combined with captions, layout or presentation validation'
    }
    if ($Presentation -and ($Captions -or $Layout)) {
        throw 'Presentation validation is a focused scope and cannot be combined with -Captions or -Layout'
    }
    if ($Coverage) {
        $expectations = [IO.Path]::GetFullPath((Join-Path $projectPath 'docs/item-coverage-expectations.json'))
        if (!(Test-Path -LiteralPath $expectations -PathType Leaf)) {
            throw "Coverage expectation fixture is missing: $expectations"
        }
        $validationArgs += '-PcoverageValidation'
        $validationArgs += "-PcoverageExpectations=$expectations"
    }
    if ($Captions -or $Layout) { $validationArgs += '-PcaptionValidation' }
    if ($Layout) { $validationArgs += '-PlayoutValidation' }
    if ($Presentation) { $validationArgs += '-PpresentationValidation' }
    $validationArgs += 'runValidationClient'
    & .\gradlew.bat @validationArgs *> $LogPath
    $resultCode = $LASTEXITCODE
    if (Test-Path -LiteralPath 'build/validation-run/validation-result.json') {
        Get-Content -LiteralPath 'build/validation-run/validation-result.json'
    }
    Get-Content -LiteralPath $LogPath -Tail 12
    exit $resultCode
}
finally {
    if ($acquired) { $taskMutex.ReleaseMutex() }
    $taskMutex.Dispose()
}
