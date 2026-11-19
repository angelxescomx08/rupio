# Aplica migraciones en Neon producción con tu sesión de la consola (ver aplicar.cjs).
#   powershell -File db/neon/aplicar.ps1 db/migrations/010_cardio.sql --verificar="select ..."
# Las dependencias (playwright y pg) se instalan una vez en .agents/neon, que no se sube a git.
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$deps = Join-Path $root ".agents\neon"
if (-not (Test-Path (Join-Path $deps "node_modules\pg"))) {
    New-Item -ItemType Directory -Force $deps | Out-Null
    Push-Location $deps
    try {
        $env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = "1"    # usa el Chrome instalado
        if (-not (Test-Path "package.json")) { npm init -y | Out-Null }
        npm install --silent --no-audit --no-fund playwright@1.65.1 pg | Out-Null
    } finally { Pop-Location }
}
$env:NODE_PATH = Join-Path $deps "node_modules"
# Lo que node escriba en stderr (avisos) no debe cortar la corrida a la mitad.
$ErrorActionPreference = "Continue"
Push-Location $root
try { node (Join-Path $PSScriptRoot "aplicar.cjs") @args; exit $LASTEXITCODE } finally { Pop-Location }
