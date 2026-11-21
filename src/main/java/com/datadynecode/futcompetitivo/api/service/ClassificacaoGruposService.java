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
        var tabelas = new ArrayList<>(fase.getGrupos().stream()
                .map(g -> g.getClassificacaoPorRodada().getLast().getTabela()).toList());
        int vagas = fase.getQuantidadeClassificados() / tabelas.size();
        int restantes = fase.getQuantidadeClassificados() % tabelas.size();
        var extras = tabelas.stream().filter(t -> t.size()>vagas).map(t -> t.get(vagas))
                .sorted((a,b) -> comparar(a,b,ordemSorteada)).limit(restantes).toList();
        // Grupos com vaga extra recebem prioridade na intercalacao, preservando a regra anterior de dois grupos.
        tabelas.sort(Comparator.comparingInt(t -> {
            int indice = t.size()>vagas ? extras.indexOf(t.get(vagas)) : -1;
            return indice<0 ? extras.size() : indice;
        }));
        List<Participante> selecionados = new ArrayList<>();
        for (int i = 0; i < vagas; i++) {
            for (var tabela : tabelas) selecionados.add(tabela.get(i).getParticipante());
        }
        extras.forEach(extra -> selecionados.add(extra.getParticipante()));
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
