# Downloads ONNX Runtime 1.28.2 (matches sherpa-onnx 1.13.8) into app/libs/
# Run from repo root: .\scripts\download_onnxruntime.ps1

$ErrorActionPreference = "Stop"

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$destDir = Join-Path $PSScriptRoot "..\app\libs"
$dest = Join-Path $destDir "onnxruntime-android-1.28.2.aar"
$dest = [System.IO.Path]::GetFullPath($dest)
New-Item -ItemType Directory -Force -Path $destDir | Out-Null

$url = "https://github.com/csukuangfj/onnxruntime-libs/releases/download/v1.28.2/onnxruntime-android-1.28.2.zip"
$zip = Join-Path $env:TEMP "onnxruntime-android-1.28.2.zip"
$minBytes = 30L * 1024 * 1024

function Test-Download([string]$path) {
    return (Test-Path $path) -and ((Get-Item $path).Length -ge $minBytes)
}

function Download-WithCurl {
    param([string]$Url, [string]$OutFile)
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if (-not $curl) {
        throw "curl.exe not found. Install curl or use Windows 10+."
    }
    if (Test-Path $OutFile) { Remove-Item $OutFile -Force }

    & curl.exe -fL --retry 5 --retry-delay 3 --connect-timeout 30 `
        -A "ai-agent-android/1.0" `
        -o $OutFile $Url

    if (-not (Test-Download $OutFile)) {
        throw "curl download incomplete: $OutFile"
    }
}

function Download-WithWebRequest {
    param([string]$Url, [string]$OutFile)
    if (Test-Path $OutFile) { Remove-Item $OutFile -Force }

    Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing `
        -UserAgent "ai-agent-android/1.0" `
        -TimeoutSec 600

    if (-not (Test-Download $OutFile)) {
        throw "Invoke-WebRequest download incomplete: $OutFile"
    }
}

Write-Host "Downloading ONNX Runtime 1.28.2 (~32 MB)..."
Write-Host "URL: $url"

try {
    Download-WithCurl -Url $url -OutFile $zip
    Write-Host "Downloaded with curl.exe"
} catch {
    Write-Warning "curl failed: $($_.Exception.Message)"
    Write-Host "Retrying with Invoke-WebRequest..."
    Download-WithWebRequest -Url $url -OutFile $zip
    Write-Host "Downloaded with Invoke-WebRequest"
}

Copy-Item $zip $dest -Force
Remove-Item $zip -ErrorAction SilentlyContinue

$sizeMb = [math]::Round((Get-Item $dest).Length / 1MB, 1)
Write-Host "Saved $dest ($sizeMb MB)"
