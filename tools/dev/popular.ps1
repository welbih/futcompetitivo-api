param(
    [string]$ApiUrl = 'http://localhost:8080',
    [string]$ClientId,
    [string]$ClientSecret,
    [int]$Edicao = 2026
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$endereco = [Uri]$ApiUrl
if (-not $endereco.IsLoopback -or $endereco.Scheme -ne 'http' -or $endereco.UserInfo -or $endereco.Query -or $endereco.Fragment) {
    throw 'Este script permite escrita somente em uma API HTTP local (localhost/127.0.0.1).'
}
$apiBase = $ApiUrl.TrimEnd('/')
$configLocal = @{}
$envLocal = Join-Path $PSScriptRoot '../../.env'
if (Test-Path -LiteralPath $envLocal) {
    foreach ($linha in [IO.File]::ReadAllLines((Resolve-Path $envLocal))) {
        if ($linha -match '^\s*([A-Z_]+)\s*=(.*)$') { $configLocal[$matches[1]] = $matches[2].Trim() }
    }
}
if (-not $ClientId) { $ClientId = if ($env:CLIENT_ID) { $env:CLIENT_ID } elseif ($configLocal.ContainsKey('CLIENT_ID')) { $configLocal['CLIENT_ID'] } else { 'futcompetitivo' } }
if (-not $ClientSecret) { $ClientSecret = if ($env:CLIENT_SECRET) { $env:CLIENT_SECRET } elseif ($configLocal.ContainsKey('CLIENT_SECRET')) { $configLocal['CLIENT_SECRET'] } else { 'desenvolvimento-local' } }
$basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(
    [Uri]::EscapeDataString($ClientId) + ':' + [Uri]::EscapeDataString($ClientSecret)))
$login = Invoke-RestMethod -Uri "$apiBase/oauth2/token" -Method Post -TimeoutSec 30 `
    -Headers @{Authorization = "Basic $basic"} -ContentType 'application/x-www-form-urlencoded' `
    -Body @{grant_type = 'urn:futcompetitivo:params:oauth:grant-type:password'; username = 'admin@futcompetitivo.local'; password = '123456'}
$script:tokenDev = $login.access_token
$script:numeroJogoDev = 0

function Invoke-ApiDev([string]$Metodo, [string]$Caminho, $Corpo = $null) {
    $argumentos = @{Uri = "$apiBase$Caminho"; Method = $Metodo; TimeoutSec = 90; Headers = @{Authorization = "Bearer $script:tokenDev"}}
    if ($null -ne $Corpo) {
        $argumentos.ContentType = 'application/json; charset=utf-8'
        $argumentos.Body = [Text.Encoding]::UTF8.GetBytes(($Corpo | ConvertTo-Json -Depth 30 -Compress))
    }
    try {
        $resposta = Invoke-RestMethod @argumentos
        return $resposta
    }
    catch {
        $detalhe = if ($_.ErrorDetails) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        throw "Falha em $Metodo ${Caminho}: $detalhe"
    }
}

$jogadores = @(Invoke-ApiDev GET '/jogadores?situacao=ATIVOS' | Sort-Object id)
$equipes = @(Invoke-ApiDev GET '/equipes?situacao=ATIVOS' | Sort-Object id)
if ($jogadores.Count -lt 8 -or $equipes.Count -lt 8) { throw 'Aguarde o seed do catalogo: sao necessarios pelo menos 8 jogadores e 8 equipes ativos.' }

function New-ParticipantesDev([int]$Quantidade, [int]$Deslocamento) {
    @(for ($i = 0; $i -lt $Quantidade; $i++) {
        @{jogadorId = $jogadores[($i + $Deslocamento) % $jogadores.Count].id; equipeId = $equipes[($i + $Deslocamento) % $equipes.Count].id}
    })
}

function New-PlacarDev($Campeonato, $Fase, $Partida) {
    $script:numeroJogoDev++
    $golsMandante = ($script:numeroJogoDev * 3 + 1) % 7
    $golsVisitante = ($script:numeroJogoDev * 2 + 2) % 5
    $placar = @{golsMandante = $golsMandante; golsVisitante = $golsVisitante; penaltisMandante = $null; penaltisVisitante = $null; golDeOuro = $false}
    if ($Fase.tipo -eq 'ELIMINATORIA') {
        $confronto = $Fase.confrontos | Where-Object { @($_.partidaIds) -contains $Partida.id } | Select-Object -First 1
        if ($null -eq $confronto) { throw "Confronto nao encontrado para a partida $($Partida.id)." }
        $empate = $golsMandante -eq $golsVisitante
        if ($confronto.tipo -eq 'IDA_E_VOLTA') {
            if ($Partida.id -eq $confronto.partidaIds[0]) { return $placar }
            $ida = @($Campeonato.fases | ForEach-Object { $_.rodadas } | ForEach-Object { $_.partidas } |
                Where-Object { $_.id -eq $confronto.partidaIds[0] })[0]
            $empate = ($ida.resultado.golsVisitante + $golsMandante) -eq ($ida.resultado.golsMandante + $golsVisitante)
        }
        if ($empate) { $placar.penaltisMandante = 5; $placar.penaltisVisitante = 4 }
    }
    return $placar
}

function Complete-CampeonatoDev([long]$Id, [bool]$Finalizar) {
    for ($passo = 0; $passo -lt 200; $passo++) {
        $campeonato = Invoke-ApiDev GET "/campeonatos/$Id"
        if ($campeonato.status -eq 'FINALIZADO') { return $campeonato }
        $jogadas = @($campeonato.fases | ForEach-Object { $_.rodadas } | ForEach-Object { $_.partidas } | Where-Object { $_.status -eq 'FINALIZADA' }).Count
        if (-not $Finalizar -and $jogadas -ge 2) { return $campeonato }
        $faseAtiva = $campeonato.fases | Where-Object { @($_.rodadas | Where-Object { $_.status -eq 'EM_ANDAMENTO' }).Count -gt 0 } | Select-Object -First 1
        if ($null -eq $faseAtiva) {
            if ($campeonato.aguardandoMataMata) {
                $null = Invoke-ApiDev POST "/campeonatos/$Id/mata-mata"
                continue
            }
            if ($campeonato.fases[-1].tipo -eq 'ELIMINATORIA') {
                $pendente = $campeonato.fases[-1].rodadas | Where-Object { $_.status -eq 'PENDENTE' } | Select-Object -First 1
                if ($null -eq $pendente) { throw "Campeonato $Id sem rodada pendente para solicitar." }
                $null = Invoke-ApiDev POST "/campeonatos/$Id/rodadas/$($pendente.id)/iniciar"
                continue
            }
            if ($campeonato.fases[-1].tipo -ne 'SWISS') { throw "Campeonato $Id sem rodada ativa." }
            $null = Invoke-ApiDev POST "/campeonatos/$Id/rodadas/swiss"
            continue
        }
        $rodada = $faseAtiva.rodadas | Where-Object { $_.status -eq 'EM_ANDAMENTO' } | Select-Object -First 1
        $partida = $rodada.partidas | Where-Object { $_.status -ne 'FINALIZADA' } | Select-Object -First 1
        if ($null -eq $partida) {
            $null = Invoke-ApiDev POST "/campeonatos/$Id/rodadas/$($rodada.id)/finalizar"
            continue
        }
        $placar = New-PlacarDev $campeonato $faseAtiva $partida
        $null = Invoke-ApiDev PUT "/campeonatos/$Id/partidas/$($partida.id)/resultado" $placar
    }
    throw "Limite de passos atingido no campeonato $Id. Nenhum dado foi apagado; execute novamente para continuar."
}

$casos = @(
    @{Nome='Liga - finalizada'; Formato='LIGA'; Finalizar=$true},
    @{Nome='Liga - em andamento'; Formato='LIGA'; Finalizar=$false},
    @{Nome='Liga turno e returno'; Formato='LIGA'; Finalizar=$true; Returno=$true; Quantidade=6},
    @{Nome='Liga parcial - finalizada'; Formato='LIGA_PARCIAL'; Finalizar=$true},
    @{Nome='Liga parcial - em andamento'; Formato='LIGA_PARCIAL'; Finalizar=$false},
    @{Nome='Mata-mata - finalizado'; Formato='ELIMINATORIO'; Finalizar=$true},
    @{Nome='Mata-mata - em andamento'; Formato='ELIMINATORIO'; Finalizar=$false},
    @{Nome='Mata-mata ida e volta'; Formato='ELIMINATORIO'; Finalizar=$true; Modo='IDA_E_VOLTA'},
    @{Nome='Mata-mata final unica e preliminar'; Formato='ELIMINATORIO'; Finalizar=$true; Modo='IDA_E_VOLTA_FINAL_UNICA'; Quantidade=7},
    @{Nome='Misto - finalizado'; Formato='MISTA'; Finalizar=$true},
    @{Nome='Misto - em andamento'; Formato='MISTA'; Finalizar=$false},
    @{Nome='Swiss - finalizado'; Formato='SWISS'; Finalizar=$true},
    @{Nome='Swiss - em andamento'; Formato='SWISS'; Finalizar=$false},
    @{Nome='Grupos - finalizado'; Formato='GRUPOS'; Finalizar=$true},
    @{Nome='Grupos - em andamento'; Formato='GRUPOS'; Finalizar=$false}
)
$organizacoes = @(Invoke-ApiDev GET '/organizacoes')
$campeonatos = @(Invoke-ApiDev GET '/campeonatos')
$resumoCampeonatos = @()
for ($indice = 0; $indice -lt $casos.Count; $indice++) {
    $caso = $casos[$indice]
    $nome = '[DEV] ' + $caso.Nome
    $organizacao = $organizacoes | Where-Object { $_.nome -eq $nome } | Select-Object -First 1
    if ($null -eq $organizacao) {
        $organizacao = Invoke-ApiDev POST '/organizacoes' @{nome=$nome; ativo=$true}
        $organizacoes += $organizacao
    }
    $campeonato = $campeonatos | Where-Object { $_.organizacao.id -eq $organizacao.id -and $_.edicao -eq $Edicao } | Select-Object -First 1
    if ($null -eq $campeonato) {
        $quantidade = if ($caso.ContainsKey('Quantidade')) { $caso.Quantidade } else { 8 }
        $selecoes = @(New-ParticipantesDev $quantidade ($indice * 3))
        $campeonato = Invoke-ApiDev POST '/campeonatos' @{organizacaoId=$organizacao.id; edicao=$Edicao; participantes=$selecoes}
    }
    if ($campeonato.status -eq 'CRIADO') {
        $inicio = @{formato=$caso.Formato; turnoEreturno=$caso.ContainsKey('Returno'); disputaTerceiroLugar=($caso.Formato -in @('ELIMINATORIO','MISTA','GRUPOS'))}
        if ($caso.Formato -eq 'LIGA_PARCIAL') { $inicio.jogosPorParticipante = 3 }
        if ($caso.Formato -eq 'SWISS') { $inicio.totalRodadas = 3 }
        if ($caso.Formato -in @('MISTA','GRUPOS')) { $inicio.quantidadeClassificados = 4 }
        if ($caso.Formato -eq 'ELIMINATORIO') { $inicio.formatoEliminatorio = if ($caso.ContainsKey('Modo')) { $caso.Modo } else { 'JOGO_UNICO' } }
        $campeonato = Invoke-ApiDev POST "/campeonatos/$($campeonato.id)/iniciar" $inicio
    }
    $campeonato = Complete-CampeonatoDev $campeonato.id $caso.Finalizar
    Write-Host "$nome - id=$($campeonato.id) status=$($campeonato.status)"
    $resumoCampeonatos += @{id=$campeonato.id; nome=$nome; formato=$caso.Formato; status=$campeonato.status}
}

$casosX1 = @(
    @{Tipo='MELHOR_DE_TRES'; Antecipar=$false; Jogos=3},
    @{Tipo='MELHOR_DE_CINCO'; Antecipar=$false; Jogos=5},
    @{Tipo='MELHOR_DE_TRES'; Antecipar=$true; Jogos=2},
    @{Tipo='MELHOR_DE_CINCO'; Antecipar=$true; Jogos=3},
    @{Tipo='MELHOR_DE_TRES'; Antecipar=$false; Jogos=1},
    @{Tipo='MELHOR_DE_CINCO'; Antecipar=$false; Jogos=2}
)
$existentesX1 = @(Invoke-ApiDev GET '/x1')
$resumoX1 = @()
for ($indice = 0; $indice -lt $casosX1.Count; $indice++) {
    $caso = $casosX1[$indice]
    $par = @(New-ParticipantesDev 2 ($indice * 3))
    $x1 = $existentesX1 | Where-Object { $_.tipo -eq $caso.Tipo -and $_.encerrarAntecipadamente -eq $caso.Antecipar -and
        $_.participanteUm.jogador.id -eq $par[0].jogadorId -and $_.participanteDois.jogador.id -eq $par[1].jogadorId } | Select-Object -First 1
    if ($null -eq $x1) { $x1 = Invoke-ApiDev POST '/x1' @{participanteUm=$par[0]; participanteDois=$par[1]; tipo=$caso.Tipo; encerrarAntecipadamente=$caso.Antecipar} }
    while ($x1.status -ne 'FINALIZADO' -and @($x1.partidas | Where-Object { $_.status -eq 'FINALIZADA' }).Count -lt $caso.Jogos) {
        $partida = $x1.partidas | Where-Object { $_.status -eq 'AGENDADA' } | Select-Object -First 1
        if ($null -eq $partida) { throw "X1 $($x1.id) sem partida agendada." }
        $umMandante = $partida.mandante.jogador.id -eq $par[0].jogadorId
        $gm = if ($umMandante) { 4 } else { 1 }
        $gv = if ($umMandante) { 1 } else { 4 }
        if (-not $caso.Antecipar -and $partida.numero -eq 2) { $gm = 2; $gv = 2 }
        $x1 = Invoke-ApiDev PUT "/x1/$($x1.id)/partidas/$($partida.id)/resultado" @{golsMandante=$gm; golsVisitante=$gv; golDeOuro=$false}
    }
    Write-Host "[DEV] X1 $($caso.Tipo) - id=$($x1.id) status=$($x1.status)"
    $resumoX1 += @{id=$x1.id; tipo=$x1.tipo; status=$x1.status; encerrarAntecipadamente=$x1.encerrarAntecipadamente}
}

$resumo = @{jogadores=$jogadores.Count; equipes=$equipes.Count; campeonatos=$resumoCampeonatos; x1=$resumoX1}
$saida = Join-Path $PSScriptRoot '../../target/populacao-dev.json'
New-Item -ItemType Directory -Force (Split-Path $saida) | Out-Null
$resumo | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 $saida
Write-Host "Populacao concluida. Resumo: $saida"
