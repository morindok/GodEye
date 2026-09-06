param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Tasks)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'Install a full JDK 17 and set JAVA_HOME first.' }
if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT -and -not (Test-Path local.properties)) { throw 'Set ANDROID_HOME to your installed Android SDK.' }
$version = '8.9'
New-Item -ItemType Directory -Force .tooling | Out-Null
$gradle = ".tooling/gradle-$version/bin/gradle.bat"
if (-not (Test-Path $gradle)) {
  $archive = ".tooling/gradle-$version-bin.zip"
  $url = "https://services.gradle.org/distributions/gradle-$version-bin.zip"
  Invoke-WebRequest -Uri $url -OutFile $archive
  $expected = ([string](Invoke-RestMethod -Uri "$url.sha256")).Trim()
  $actual = (Get-FileHash $archive -Algorithm SHA256).Hash
  if ($actual -ine $expected) { Remove-Item $archive; throw 'Gradle checksum mismatch' }
  Expand-Archive $archive -DestinationPath .tooling -Force
  Remove-Item $archive
}
if (-not $Tasks -or $Tasks.Count -eq 0) { $Tasks = @('testDebugUnitTest', 'lintDebug', 'assembleDebug') }
& $gradle --no-daemon @Tasks
exit $LASTEXITCODE
