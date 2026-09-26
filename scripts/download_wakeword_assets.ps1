# Downloads openWakeWord ONNX models into app/src/main/assets/
# Run from repo root: .\scripts\download_wakeword_assets.ps1

$ErrorActionPreference = "Stop"

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$dest = Join-Path $PSScriptRoot "..\app\src\main\assets"
$dest = [System.IO.Path]::GetFullPath($dest)
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$baseUrl = "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1"
$files = @{
    "melspectrogram.onnx"   = @{ Url = "$baseUrl/melspectrogram.onnx"; MinBytes = 500 * 1024 }
    "embedding_model.onnx"  = @{ Url = "$baseUrl/embedding_model.onnx"; MinBytes = 500 * 1024 }
    "hey_jarvis_v0.1.onnx"  = @{ Url = "$baseUrl/hey_jarvis_v0.1.onnx"; MinBytes = 500 * 1024 }
}

function Test-Download([string]$path, [long]$minBytes) {
    return (Test-Path $path) -and ((Get-Item $path).Length -ge $minBytes)
}

function Download-WithCurl {
    param([string]$Url, [string]$OutFile, [long]$MinBytes)
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if (-not $curl) {
        throw "curl.exe not found. Install curl or use Windows 10+."
    }
    if (Test-Path $OutFile) { Remove-Item $OutFile -Force }

    & curl.exe -fL --retry 5 --retry-delay 3 --connect-timeout 30 `
        -A "ai-agent-android/1.0" `
        -o $OutFile $Url

    if (-not (Test-Download $OutFile $MinBytes)) {
        throw "curl download incomplete: $OutFile"
    }
}

function Download-WithWebRequest {
    param([string]$Url, [string]$OutFile, [long]$MinBytes)
    if (Test-Path $OutFile) { Remove-Item $OutFile -Force }

    Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing `
        -UserAgent "ai-agent-android/1.0" `
        -TimeoutSec 300

    if (-not (Test-Download $OutFile $MinBytes)) {
        throw "Invoke-WebRequest download incomplete: $OutFile"
    }
}

function Download-File {
    param([string]$Name, [string]$Url, [long]$MinBytes, [string]$OutFile)

    if (Test-Download $OutFile $MinBytes) {
        $sizeKb = [math]::Round((Get-Item $OutFile).Length / 1024, 0)
        Write-Host "Skipping $Name (already present, $sizeKb KB)"
        return
    }

    Write-Host "Downloading $Name..."
    try {
        Download-WithCurl -Url $Url -OutFile $OutFile -MinBytes $MinBytes
    } catch {
        Write-Warning "curl failed for ${Name}: $($_.Exception.Message)"
        Write-Host "Retrying with Invoke-WebRequest..."
        Download-WithWebRequest -Url $Url -OutFile $OutFile -MinBytes $MinBytes
    }

    $sizeKb = [math]::Round((Get-Item $OutFile).Length / 1024, 0)
    Write-Host "Saved $Name ($sizeKb KB)"
}

foreach ($entry in $files.GetEnumerator()) {
    $outFile = Join-Path $dest $entry.Key
    Download-File -Name $entry.Key -Url $entry.Value.Url -MinBytes $entry.Value.MinBytes -OutFile $outFile
}

Write-Host ""
Write-Host "Wake word assets ready in $dest"
Get-ChildItem $dest -Filter "*.onnx" | ForEach-Object {
    Write-Host "  $($_.Name) ($([math]::Round($_.Length / 1024, 0)) KB)"
}
