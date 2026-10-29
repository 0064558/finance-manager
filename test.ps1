$ErrorActionPreference = 'Stop'
# Native test failures must be collected so both suites run, then propagated as exit 1.
$PSNativeCommandUseErrorActionPreference = $false
$results = @()

Push-Location (Join-Path $PSScriptRoot 'finance-manager-api')
try {
    Write-Host 'Executando todos os testes da API e a verificacao de cobertura...'
    if ($env:OS -eq 'Windows_NT') {
        & .\mvnw.cmd -B verify
    } else {
        & ./mvnw -B verify
    }
    $results += [pscustomobject]@{ Suite = 'API'; ExitCode = $LASTEXITCODE }
} catch {
    Write-Host "Falha ao executar a API: $_" -ForegroundColor Red
    $results += [pscustomobject]@{ Suite = 'API'; ExitCode = 1 }
} finally {
    Pop-Location
}

Push-Location (Join-Path $PSScriptRoot 'finance-manager-web')
try {
    Write-Host 'Executando todos os testes do frontend...'
    if (-not (Test-Path -LiteralPath 'node_modules')) {
        throw 'Dependencias ausentes. Execute npm ci em finance-manager-web antes de rodar os testes.'
    }
    if ($env:OS -eq 'Windows_NT') {
        & npm.cmd test -- --watch=false
    } else {
        & npm test -- --watch=false
    }
    $results += [pscustomobject]@{ Suite = 'Frontend'; ExitCode = $LASTEXITCODE }
} catch {
    Write-Host "Falha ao executar o frontend: $_" -ForegroundColor Red
    $results += [pscustomobject]@{ Suite = 'Frontend'; ExitCode = 1 }
} finally {
    Pop-Location
}

$results | Format-Table -AutoSize | Out-Host
if (@($results | Where-Object { $_.ExitCode -ne 0 }).Count -gt 0) {
    exit 1
}
exit 0
