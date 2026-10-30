param(
    [ValidateRange(1, 600)]
    [int]$DockerStartupTimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
# Native test failures must be collected so both suites run, then propagated as exit 1.
$PSNativeCommandUseErrorActionPreference = $false

function Get-DockerEngineState {
    param([string]$DockerPath, [int]$TimeoutMilliseconds)

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo.FileName = $DockerPath
    $process.StartInfo.Arguments = 'info --format {{.OSType}}'
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.CreateNoWindow = $true
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutMilliseconds)) {
            $process.Kill()
            $process.WaitForExit()
            return [pscustomobject]@{ Ready = $false; Detail = 'O Docker nao respondeu a verificacao a tempo.' }
        }
        $engine = $stdout.GetAwaiter().GetResult().Trim()
        $detail = $stderr.GetAwaiter().GetResult().Trim()
        if ($process.ExitCode -eq 0) {
            if ($engine -ne 'linux') {
                throw 'O PostgreSQL de teste exige containers Linux. No Docker Desktop, selecione Switch to Linux containers e execute .\test.ps1 novamente.'
            }
            return [pscustomobject]@{ Ready = $true; Detail = '' }
        }
        return [pscustomobject]@{ Ready = $false; Detail = $detail }
    } finally {
        $process.Dispose()
    }
}

function Wait-TestDocker {
    $dockerCommand = Get-Command docker -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $dockerCommand) {
        throw 'Docker nao encontrado. Instale o Docker Desktop com containers Linux e abra um novo terminal antes de executar .\test.ps1.'
    }

    Write-Host 'Verificando o Docker para o PostgreSQL descartavel de teste...'
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $state = Get-DockerEngineState $dockerCommand.Source 5000
    if ($state.Ready) { return }

    if ($env:OS -ne 'Windows_NT') {
        throw "Docker indisponivel. Inicie o Docker e execute ./test.ps1 novamente. $($state.Detail)"
    }

    $desktopPath = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (-not (Get-Process -Name 'Docker Desktop' -ErrorAction SilentlyContinue)) {
        if (-not (Test-Path -LiteralPath $desktopPath -PathType Leaf)) {
            throw "Docker indisponivel. Abra o Docker Desktop, aguarde o engine iniciar e execute .\test.ps1 novamente. $($state.Detail)"
        }
        Write-Host 'Iniciando o Docker Desktop...'
        Start-Process -FilePath $desktopPath -WindowStyle Hidden
    }

    Write-Host "Aguardando o Docker ficar pronto (limite de $DockerStartupTimeoutSeconds segundos)..."
    $nextProgressSeconds = 10
    while ($timer.Elapsed.TotalSeconds -lt $DockerStartupTimeoutSeconds) {
        $remainingMilliseconds = [int](($DockerStartupTimeoutSeconds - $timer.Elapsed.TotalSeconds) * 1000)
        $state = Get-DockerEngineState $dockerCommand.Source ([Math]::Max(1, [Math]::Min(5000, $remainingMilliseconds)))
        if ($state.Ready) {
            Write-Host 'Docker pronto. O banco de teste sera criado pelo Testcontainers.'
            return
        }
        if ($timer.Elapsed.TotalSeconds -ge $nextProgressSeconds) {
            Write-Host "Ainda aguardando o Docker... $([int]$timer.Elapsed.TotalSeconds)s"
            $nextProgressSeconds += 10
        }
        $remainingMilliseconds = [int](($DockerStartupTimeoutSeconds - $timer.Elapsed.TotalSeconds) * 1000)
        if ($remainingMilliseconds -gt 0) {
            Start-Sleep -Milliseconds ([Math]::Min(2000, $remainingMilliseconds))
        }
    }
    throw "O Docker nao ficou pronto em $DockerStartupTimeoutSeconds segundos. Abra o Docker Desktop e confira se o engine Linux iniciou; depois execute .\test.ps1 novamente. Nenhum teste da API foi executado. $($state.Detail)"
}

$results = @()

Push-Location (Join-Path $PSScriptRoot 'finance-manager-api')
try {
    Wait-TestDocker
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
