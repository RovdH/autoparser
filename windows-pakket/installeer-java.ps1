$ErrorActionPreference = 'Stop'

$architecture = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'aarch64' } else { 'x64' }
$apiUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/$architecture/jre/hotspot/normal/eclipse"
$packageDirectory = $PSScriptRoot
$runtimeDirectory = Join-Path $packageDirectory 'runtime'
$stagingDirectory = Join-Path $packageDirectory 'runtime-download'
$zipFile = Join-Path $env:TEMP 'wijnkado-temurin21-jre.zip'

try {
    if (Test-Path $stagingDirectory) {
        Remove-Item $stagingDirectory -Recurse -Force
    }

    New-Item $stagingDirectory -ItemType Directory | Out-Null
    Write-Host 'Officiele Eclipse Temurin Java 21-runtime downloaden...'
    Invoke-WebRequest -Uri $apiUrl -OutFile $zipFile -UseBasicParsing

    Write-Host 'Java-runtime uitpakken...'
    Expand-Archive -Path $zipFile -DestinationPath $stagingDirectory -Force
    $extractedRoot = Get-ChildItem $stagingDirectory -Directory | Select-Object -First 1

    if (-not $extractedRoot -or -not (Test-Path (Join-Path $extractedRoot.FullName 'bin\java.exe'))) {
        throw 'Het gedownloade Java-pakket heeft niet de verwachte inhoud.'
    }

    if (Test-Path $runtimeDirectory) {
        Remove-Item $runtimeDirectory -Recurse -Force
    }

    Move-Item $extractedRoot.FullName $runtimeDirectory
    Write-Host 'Java is klaar voor gebruik.'
    exit 0
}
catch {
    Write-Host ''
    Write-Host ('Downloaden van Java is mislukt: ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
finally {
    if (Test-Path $zipFile) {
        Remove-Item $zipFile -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path $stagingDirectory) {
        Remove-Item $stagingDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
}
