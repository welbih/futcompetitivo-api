package com.datadynecode.futcompetitivo.api.simulacao.suporte;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.CampeonatoStatus;
import com.datadynecode.futcompetitivo.api.entity.RodadaStatus;
import com.datadynecode.futcompetitivo.api.entity.TipoFormato;
import com.datadynecode.futcompetitivo.api.service.*;
import jakarta.persistence.EntityManager;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

public class SimuladorCampeonato {

    @Autowired private CampeonatoService campeonatos;
    @Autowired private OrganizacaoService organizacoes;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private ParticipanteService participantes;
    @Autowired private EntityManager entityManager;

    public void executar(CenarioSimulacao cenario) {
        simular(cenario.nome, cenario.quantidade, cenario.formato, cenario.returno,
                cenario.jogosPorParticipante, cenario.totalRodadas,
                cenario.totalPartidasEsperado, cenario.jogosEsperadosPorParticipante);
    }

    private void simular(String nome, int quantidade,
            TipoFormato formato, boolean returno, Integer jogosPorParticipante, Integer totalRodadas,
            int totalPartidasEsperado, Integer jogosEsperadosPorParticipante) {
        List<Long> ids = criarParticipantes(quantidade);
        var criado = campeonatos.criar(new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest(nome, true)).id(), 2026, selecoes(ids)));
        campeonatos.iniciar(criado.id(), new IniciarCampeonatoRequest(
                formato, returno, jogosPorParticipante, totalRodadas));
        recarregar();
        CampeonatoResponse campeonato = campeonatos.buscar(criado.id());
        Map<Long, String> nomes = new LinkedHashMap<>();
        Map<Long, Estatisticas> esperado = new HashMap<>();
        campeonato.participantes().forEach(p -> {
            nomes.put(p.id(), p.jogador().nome() + " / " + p.equipe().nome());
            esperado.put(p.id(), new Estatisticas());
        });
        StringBuilder saida = new StringBuilder("\n============================================================\n")
                .append(nome).append("\n============================================================\n");
        if (formato != TipoFormato.SWISS) {
            saida.append("AGENDA COMPLETA\n");
            campeonato.fases().getFirst().rodadas().forEach(r -> exibirAgenda(saida, r, nomes));
        } else {
            saida.append("Agenda Swiss gerada rodada a rodada, conforme a classificacao.\n");
        }

        Random resultados = new Random(2026);
        Map<Set<Long>, PartidaResponse> ultimoEncontro = new HashMap<>();
        List<ClassificacaoResponse> historico = new ArrayList<>();
        int partidasDisputadas = 0;
        int rodadasDisputadas = 0;
        int revanches = 0;
        while (campeonato.status() != CampeonatoStatus.FINALIZADO) {
            assertTrue(rodadasDisputadas < 100, "A simulacao deve finalizar.");
            List<RodadaResponse> rodadas = campeonato.fases().getFirst().rodadas();
            RodadaResponse rodada;
            if (formato == TipoFormato.SWISS && rodadasDisputadas == rodadas.size()) {
                rodada = campeonatos.gerarProximaRodadaSwiss(campeonato.id());
            } else {
                rodada = rodadas.get(rodadasDisputadas);
            }
            if (formato == TipoFormato.SWISS) exibirAgenda(saida, rodada, nomes);
            validarParticipantesDaRodada(rodada);
            saida.append("\nRESULTADOS - RODADA ").append(rodada.numero()).append('\n');
            for (PartidaResponse partida : rodada.partidas()) {
                assertNotNull(partida.id(), "A partida deve estar persistida.");
                Set<Long> par = Set.of(partida.mandante().id(), partida.visitante().id());
                PartidaResponse anterior = ultimoEncontro.put(par, partida);
                if (anterior != null) {
                    revanches++;
                    assertTrue(formato == TipoFormato.SWISS || returno, "Formato nao permite repeticao.");
                    assertEquals(anterior.visitante().id(), partida.mandante().id(), "Revanche deve inverter mando.");
                    assertEquals(anterior.mandante().id(), partida.visitante().id());
                }
                int golsMandante = resultados.nextInt(5);
                int golsVisitante = resultados.nextInt(5);
                esperado.get(partida.mandante().id()).registrar(golsMandante, golsVisitante);
                esperado.get(partida.visitante().id()).registrar(golsVisitante, golsMandante);
                campeonatos.registrarResultado(campeonato.id(), partida.id(),
                        new ResultadoRequest(golsMandante, golsVisitante, null, null, false));
                saida.append(String.format(Locale.ROOT, "  %-34s %d x %d %-34s%s%n",
                        nomes.get(partida.mandante().id()), golsMandante, golsVisitante,
                        nomes.get(partida.visitante().id()), anterior == null ? "" : " [REVANCHE - MANDO INVERTIDO]"));
                partidasDisputadas++;
            }
            rodadasDisputadas++;
            recarregar();
            campeonato = campeonatos.buscar(criado.id());
            ClassificacaoResponse classificacao = campeonatos.classificacao(criado.id());
            validarClassificacao(classificacao, esperado);
            assertEquals(historico, campeonato.classificacaoPorRodada().subList(0, historico.size()),
                    "Rodadas anteriores devem preservar suas classificacoes.");
            historico.add(classificacao);
            exibirClassificacao(saida, classificacao);
            assertEquals(RodadaStatus.FINALIZADA,
                    campeonato.fases().getFirst().rodadas().get(rodadasDisputadas - 1).status());
        }

        assertEquals(totalPartidasEsperado, partidasDisputadas);
        assertEquals(rodadasDisputadas, campeonato.classificacaoPorRodada().size());
        assertEquals(CampeonatoStatus.FINALIZADO, campeonato.status());
        if (jogosEsperadosPorParticipante != null)
            esperado.values().forEach(e -> assertEquals(jogosEsperadosPorParticipante.intValue(), e.jogos));
        if (formato == TipoFormato.SWISS) assertEquals(totalRodadas.intValue(), rodadasDisputadas);
        if (formato == TipoFormato.SWISS && quantidade == 4) assertTrue(revanches > 0);
        if (formato == TipoFormato.LIGA && returno) assertEquals(21, revanches);
        saida.append("\nFINALIZADO: ").append(rodadasDisputadas).append(" rodadas, ")
                .append(partidasDisputadas).append(" partidas, ").append(revanches).append(" revanches.\n")
                .append("Campeao: ").append(nomes.get(campeonatos.classificados(criado.id(), 1).getFirst().id()))
                .append("\n============================================================\n");
        System.out.print(saida);
    }

    private void validarParticipantesDaRodada(RodadaResponse rodada) {
        Set<Long> usados = new HashSet<>();
        rodada.partidas().forEach(p -> {
            assertTrue(usados.add(p.mandante().id()), "Participante nao pode jogar duas vezes na rodada.");
            assertTrue(usados.add(p.visitante().id()), "Participante nao pode jogar duas vezes na rodada.");
        });
        if (rodada.folgaParticipanteId() != null) assertFalse(usados.contains(rodada.folgaParticipanteId()));
    }

    private void validarClassificacao(ClassificacaoResponse classificacao, Map<Long, Estatisticas> esperado) {
        assertEquals(esperado.keySet(), new HashSet<>(classificacao.tabela().stream()
                .map(c -> c.participante().id()).toList()));
        assertEquals(esperado.size(), classificacao.tabela().size());
        int posicao = 1;
        for (ClassificacaoParticipanteResponse linha : classificacao.tabela()) {
            Estatisticas e = esperado.get(linha.participante().id());
            assertEquals(posicao++, linha.posicaoAtual());
            assertEquals(e.jogos, linha.jogos());
            assertEquals(e.vitorias, linha.vitorias());
            assertEquals(e.empates, linha.empates());
            assertEquals(e.derrotas, linha.derrotas());
            assertEquals(e.vitorias * 3 + e.empates, linha.pontos());
            assertEquals(e.golsPro, linha.golsPro());
            assertEquals(e.golsContra, linha.golsContra());
            assertEquals(e.golsPro - e.golsContra, linha.saldoGols());
        }
        Comparator<ClassificacaoParticipanteResponse> criterios = Comparator
                .comparingInt(ClassificacaoParticipanteResponse::pontos).reversed()
                .thenComparing(Comparator.comparingInt(ClassificacaoParticipanteResponse::vitorias).reversed())
                .thenComparing(Comparator.comparingInt(ClassificacaoParticipanteResponse::saldoGols).reversed())
                .thenComparing(Comparator.comparingInt(ClassificacaoParticipanteResponse::golsPro).reversed());
        for (int i = 1; i < classificacao.tabela().size(); i++)
            assertTrue(criterios.compare(classificacao.tabela().get(i - 1), classificacao.tabela().get(i)) <= 0);
    }

    private void exibirAgenda(StringBuilder saida, RodadaResponse rodada, Map<Long, String> nomes) {
        saida.append("\nAGENDA - RODADA ").append(rodada.numero()).append('\n');
        rodada.partidas().forEach(p -> saida.append("  ").append(nomes.get(p.mandante().id()))
                .append(" x ").append(nomes.get(p.visitante().id())).append('\n'));
        if (rodada.folgaParticipanteId() != null)
            saida.append("  Folga: ").append(nomes.get(rodada.folgaParticipanteId())).append('\n');
    }

    private void exibirClassificacao(StringBuilder saida, ClassificacaoResponse classificacao) {
        saida.append("\nCLASSIFICACAO APOS RODADA ").append(classificacao.rodada()).append('\n');
        saida.append(String.format("%-4s %-10s %-20s %4s %3s %3s %3s %3s %4s %4s %4s %-10s%n",
                "Pos", "Jogador", "Equipe", "Pts", "J", "V", "E", "D", "GP", "GC", "SG", "Mov"));
        classificacao.tabela().forEach(c -> saida.append(String.format(Locale.ROOT,
                "%-4d %-10s %-20s %4d %3d %3d %3d %3d %4d %4d %4d %-10s%n", c.posicaoAtual(),
                c.participante().jogador().nome(), c.participante().equipe().nome(), c.pontos(), c.jogos(),
                c.vitorias(), c.empates(), c.derrotas(), c.golsPro(), c.golsContra(), c.saldoGols(),
                c.movimentacao() + " " + c.variacao())));
    }

    private List<Long> criarParticipantes(int quantidade) {
        String[] nomes = {"Welber", "Geilson", "Osmar", "Caliton", "Graciel", "Luciano", "Mateus"};
        String[] clubes = {"Real Madrid", "Barcelona", "Manchester City", "Atletico de Madrid", "Chelsea", "PSG", "Arsenal"};
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            var jogador = jogadores.criar(new JogadorRequest(nomes[i], null));
            var equipe = equipes.criar(new EquipeRequest(clubes[i], "https://example.com/logo.png"));
            ids.add(participantes.criar(new ParticipanteRequest(jogador.id(), equipe.id())).id());
        }
        return ids;
    }

    private void recarregar() { entityManager.flush(); entityManager.clear(); }

    private static class Estatisticas {
        int jogos, vitorias, empates, derrotas, golsPro, golsContra;
        void registrar(int pro, int contra) {
            jogos++;
            golsPro += pro;
            golsContra += contra;
            if (pro > contra) vitorias++;
            else if (pro == contra) empates++;
            else derrotas++;
        }
    }

    private List<ParticipanteRequest> selecoes(List<Long> ids) {
        return ids.stream().map(id -> {
            if (id == Long.MAX_VALUE) return new ParticipanteRequest(id, id);
            var participante = participantes.buscar(id);
            return new ParticipanteRequest(participante.jogador().id(), participante.equipe().id());
        }).toList();
    }
}
