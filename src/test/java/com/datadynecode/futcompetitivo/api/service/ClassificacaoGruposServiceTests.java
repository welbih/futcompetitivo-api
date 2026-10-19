package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.entity.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassificacaoGruposServiceTests {
    private final ClassificacaoGruposService service = new ClassificacaoGruposService(new GeradorClassificacaoService());

    @Test
    void vagaExtraComparaAproveitamentoEmGruposDeTamanhosDiferentes() {
        Fase fase = fase();
        var a = fase.getGrupos().getFirst().getClassificacaoPorRodada().getLast().getTabela().getLast();
        var b = fase.getGrupos().getLast().getClassificacaoPorRodada().getLast().getTabela().getLast();
        a.setPontos(4); a.setJogos(3);
        b.setPontos(3); b.setJogos(2);
        var selecionados = service.classificados(fase, ordem(fase));
        assertEquals(5, selecionados.size());
        assertEquals(b.getParticipante(), selecionados.getLast());
        assertFalse(selecionados.contains(a.getParticipante()));
        assertEquals(fase.getGrupos().getLast().getParticipantes().getFirst(), selecionados.getFirst());
    }

    @Test
    void sorteioInicialMantemClassificacaoEstavelAoRecalcularEmpatesCompletos() {
        Fase fase = fase();
        for (int rodadaNumero = 1; rodadaNumero <= 3; rodadaNumero++) {
            Rodada rodada = new Rodada(rodadaNumero);
            rodada.setStatus(RodadaStatus.FINALIZADA);
            for (Grupo grupo : fase.getGrupos()) {
                Partida partida = new Partida(grupo.getParticipantes().get(rodadaNumero - 1),
                        grupo.getParticipantes().get(rodadaNumero % 3));
                partida.setStatus(StatusPartida.FINALIZADA);
                partida.setResultado(new Resultado(false, 0, 0, null, null));
                rodada.getPartidas().add(partida);
            }
            fase.getRodadas().add(rodada);
        }
        for (int repeticao = 0; repeticao < 3; repeticao++) {
            service.recalcular(fase, 1);
            for (Grupo grupo : fase.getGrupos()) {
                assertEquals(grupo.getParticipantes(), grupo.getClassificacaoPorRodada().getLast().getTabela()
                        .stream().map(ClassificacaoParticipante::getParticipante).toList());
            }
        }
    }

    @Test
    void desempataPorVitoriasSaldoGolsESorteioInicial() {
        for (int criterio = 0; criterio < 4; criterio++) {
            Fase fase = fase();
            var a = fase.getGrupos().getFirst().getClassificacaoPorRodada().getLast().getTabela().getLast();
            var b = fase.getGrupos().getLast().getClassificacaoPorRodada().getLast().getTabela().getLast();
            a.setJogos(2); b.setJogos(2);
            if (criterio == 0) b.setVitorias(1);
            if (criterio == 1) b.setSaldoGols(1);
            if (criterio == 2) b.setGolsPro(1);
            List<Participante> sorteio = new ArrayList<>(ordem(fase));
            sorteio.remove(b.getParticipante()); sorteio.addFirst(b.getParticipante());
            assertEquals(b.getParticipante(), service.classificados(fase, sorteio).getLast());
        }
    }

    private Fase fase() {
        Fase fase = new Fase(); fase.setQuantidadeClassificados(5);
        for (int g = 0; g < 2; g++) {
            Grupo grupo = new Grupo();
            Classificacao classificacao = new Classificacao();
            for (int i = 1; i <= 3; i++) {
                Participante participante = new Participante(); participante.setId((long) (g * 3 + i));
                grupo.getParticipantes().add(participante);
                classificacao.getTabela().add(new ClassificacaoParticipante(participante));
            }
            grupo.getClassificacaoPorRodada().add(classificacao); fase.getGrupos().add(grupo);
        }
        return fase;
    }

    private List<Participante> ordem(Fase fase) {
        return fase.getGrupos().stream().flatMap(g -> g.getParticipantes().stream()).toList();
    }
}
