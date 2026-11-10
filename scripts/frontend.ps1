param([string]$Server = 'http://127.0.0.1:8080')
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    if (-not (Test-Path 'target/chess-software-1.0.0.jar')) { throw 'Primero ejecuta mvn verify desde la raiz del proyecto.' }
    & java -cp target/chess-software-1.0.0.jar software.ulpgc.chess.frontend.FrontendMain --server $Server
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo ejecutar el frontend.' }
} finally { Pop-Location }
