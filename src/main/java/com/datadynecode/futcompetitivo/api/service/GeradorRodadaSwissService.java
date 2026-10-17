package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GeradorRodadaSwissService {

    public Rodada gerar(Fase fase, List<Participante> participantes, Classificacao classificacao) {
        if (fase.getRodadas().size() >= fase.getTotalRodadas())
            throw new RegraCampeonatoException("Todas as rodadas já foram geradas.");
        if (fase.getRodadas().stream().anyMatch(r -> r.getStatus() != RodadaStatus.FINALIZADA))
            throw new RegraCampeonatoException("Rodada anterior ainda não finalizada.");

        List<Participante> ordem = classificacao == null ? new ArrayList<>(participantes)
                : new ArrayList<>(classificacao.getTabela().stream()
                        .map(ClassificacaoParticipante::getParticipante).toList());
        Rodada rodada = new Rodada(fase.getRodadas().size() + 1);
        Map<String, Partida> anteriores = new HashMap<>();
        Map<Long, Integer> folgas = new HashMap<>();
        fase.getRodadas().stream().sorted(Comparator.comparing(Rodada::getNumero)).forEach(r -> {
            r.getPartidas().forEach(p -> anteriores.put(chave(p.getMandante(), p.getVisitante()), p));
            if (r.getFolga() != null) folgas.merge(r.getFolga().getId(), 1, Integer::sum);
        });
        List<Participante> candidatosFolga = new ArrayList<>(ordem.reversed());
        candidatosFolga.sort(Comparator.comparingInt(p -> folgas.getOrDefault(p.getId(), 0)));
        // Tenta todas as combinações e folgas antes de permitir mais uma revanche.
        for (int revanches = 0; revanches <= ordem.size() / 2; revanches++) {
            if (ordem.size() % 2 != 0) {
                for (Participante folga : candidatosFolga) {
                    List<Participante> restantes = new ArrayList<>(ordem);
                    restantes.remove(folga);
                    if (parear(restantes, anteriores, rodada.getPartidas(), revanches)) {
                        rodada.setFolga(folga);
                        fase.getRodadas().add(rodada);
                        return rodada;
                    }
                }
            } else if (parear(ordem, anteriores, rodada.getPartidas(), revanches)) {
                fase.getRodadas().add(rodada);
                return rodada;
            }
        }
        throw new RegraCampeonatoException(
                "Não é possível gerar a próxima rodada Swiss.");
    }

    private boolean parear(List<Participante> participantes, Map<String, Partida> anteriores,
            List<Partida> partidas, int revanchesDisponiveis) {
        if (participantes.isEmpty()) return true;
        Participante primeiro = participantes.getFirst();
        for (int i = 1; i < participantes.size(); i++) {
            Participante segundo = participantes.get(i);
            Partida anterior = anteriores.get(chave(primeiro, segundo));
            int custo = anterior == null ? 0 : 1;
            if (custo > revanchesDisponiveis) continue;
            List<Participante> restantes = new ArrayList<>(participantes);
            restantes.remove(i);
            restantes.removeFirst();
            partidas.add(anterior == null ? new Partida(primeiro, segundo)
                    : new Partida(anterior.getVisitante(), anterior.getMandante()));
            if (parear(restantes, anteriores, partidas, revanchesDisponiveis - custo)) return true;
            partidas.removeLast();
        }
        return false;
    }

    private String chave(Participante a, Participante b) {
        return Math.min(a.getId(), b.getId()) + ":" + Math.max(a.getId(), b.getId());
    }
}
