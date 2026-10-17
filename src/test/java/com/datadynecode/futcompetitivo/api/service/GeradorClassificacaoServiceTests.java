package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.service.GeradorClassificacaoService;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeradorClassificacaoServiceTests {
    private final GeradorClassificacaoService service = new GeradorClassificacaoService();

    @Test
    void calculaPontosSaldoMovimentacaoSemRecontarJogosOuAlterarHistorico() {
        Participante a = participante(1L);
        Participante b = participante(2L);
        Partida ida = finalizada(a, b, 2, 0);
        Classificacao primeira = service.gerar(List.of(a, b), List.of(ida), 1, null);
        Classificacao segunda = service.gerar(List.of(a, b),
                List.of(ida, finalizada(b, a, 3, 0)), 2, primeira);
        var vencedor = segunda.getTabela().getFirst();
        assertEquals(2L, vencedor.getParticipante().getId());
        assertEquals(3, vencedor.getPontos());
        assertEquals(2, vencedor.getJogos());
        assertEquals(1, vencedor.getSaldoGols());
        assertEquals(1, vencedor.getVitorias());
        assertEquals(1, vencedor.getDerrotas());
        assertEquals(2, vencedor.getPosicaoAnterior());
        assertEquals(MovimentacaoPosicao.SUBIU, vencedor.getMovimentacao());
        assertEquals(1, vencedor.getVariacao());
        assertEquals(1L, primeira.getTabela().getFirst().getParticipante().getId());
        assertEquals(1, primeira.getTabela().getFirst().getJogos());
        assertNotSame(primeira.getTabela().getFirst(), segunda.getTabela().getLast());
    }

    @Test
    void empateValeUmPontoEPartidaAgendadaNaoConta() {
        Participante a = participante(1L);
        Participante b = participante(2L);
        var classificacao = service.gerar(List.of(a, b),
                List.of(finalizada(a, b, 1, 1), new Partida(b, a)), 1, null);
        classificacao.getTabela().forEach(c -> {
            assertEquals(1, c.getJogos());
            assertEquals(1, c.getPontos());
            assertEquals(1, c.getEmpates());
            assertEquals(0, c.getSaldoGols());
        });
    }

    private Participante participante(Long id) {
        Participante participante = new Participante();
        participante.setId(id);
        return participante;
    }

    private Partida finalizada(Participante a, Participante b, int golsA, int golsB) {
        Partida partida = new Partida(a, b);
        partida.setResultado(new Resultado(false, golsA, golsB, null, null));
        partida.setStatus(StatusPartida.FINALIZADA);
        return partida;
    }
}
