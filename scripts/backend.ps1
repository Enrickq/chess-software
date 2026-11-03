param([int]$Port = 8080, [string]$Data = 'data', [string]$BindAddress = '127.0.0.1')
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    if (-not (Test-Path 'target/chess-software-1.0.0.jar')) { throw 'Primero ejecuta mvn verify desde la raiz del proyecto.' }
    & java -jar target/chess-software-1.0.0.jar --port $Port --host $BindAddress --data $Data
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo ejecutar el backend.' }
} finally { Pop-Location }
