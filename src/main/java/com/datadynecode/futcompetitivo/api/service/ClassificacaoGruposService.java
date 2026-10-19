package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.entity.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClassificacaoGruposService {
    private final GeradorClassificacaoService classificacoes;

    public void recalcular(Fase fase, int aPartirDaRodada) {
        for (Grupo grupo : fase.getGrupos()) {
            grupo.getClassificacaoPorRodada().removeIf(c -> c.getRodada() >= aPartirDaRodada);
            Set<Long> membros = new HashSet<>(grupo.getParticipantes().stream().map(Participante::getId).toList());
            List<Partida> acumuladas = new ArrayList<>();
            for (Rodada rodada : fase.getRodadas()) {
                if (rodada.getStatus() != RodadaStatus.FINALIZADA) break;
                acumuladas.addAll(rodada.getPartidas().stream()
                        .filter(p -> membros.contains(p.getMandante().getId()) && membros.contains(p.getVisitante().getId())).toList());
                if (rodada.getNumero() >= aPartirDaRodada) {
                    var anterior = grupo.getClassificacaoPorRodada().isEmpty() ? null
                            : grupo.getClassificacaoPorRodada().getLast();
                    grupo.getClassificacaoPorRodada().add(classificacoes.gerarComSorteioInicial(
                            grupo.getParticipantes(), acumuladas, rodada.getNumero(), anterior));
                }
            }
        }
    }

    public List<Participante> classificados(Fase fase, List<Participante> ordemSorteada) {
        int vagas = fase.getQuantidadeClassificados() / 2;
        var a = fase.getGrupos().getFirst().getClassificacaoPorRodada().getLast().getTabela();
        var b = fase.getGrupos().getLast().getClassificacaoPorRodada().getLast().getTabela();
        List<ClassificacaoParticipante> primeiro = a;
        List<ClassificacaoParticipante> segundo = b;
        ClassificacaoParticipante extra = null;
        if (fase.getQuantidadeClassificados() % 2 != 0) {
            if (a.size() <= vagas) extra = b.get(vagas);
            else if (b.size() <= vagas) extra = a.get(vagas);
            else extra = comparar(a.get(vagas), b.get(vagas), ordemSorteada) <= 0 ? a.get(vagas) : b.get(vagas);
            if (b.contains(extra)) { primeiro = b; segundo = a; }
        }
        List<Participante> selecionados = new ArrayList<>();
        for (int i = 0; i < vagas; i++) {
            selecionados.add(primeiro.get(i).getParticipante());
            selecionados.add(segundo.get(i).getParticipante());
        }
        if (extra != null) selecionados.add(extra.getParticipante());
        return selecionados;
    }

    private int comparar(ClassificacaoParticipante a, ClassificacaoParticipante b, List<Participante> sorteio) {
        int[] criteriosA = {a.getPontos(), a.getVitorias(), a.getSaldoGols(), a.getGolsPro()};
        int[] criteriosB = {b.getPontos(), b.getVitorias(), b.getSaldoGols(), b.getGolsPro()};
        for (int i = 0; i < criteriosA.length; i++) {
            int comparacao = Long.compare((long) criteriosB[i] * a.getJogos(), (long) criteriosA[i] * b.getJogos());
            if (comparacao != 0) return comparacao;
        }
        return Integer.compare(sorteio.indexOf(a.getParticipante()), sorteio.indexOf(b.getParticipante()));
    }
}
