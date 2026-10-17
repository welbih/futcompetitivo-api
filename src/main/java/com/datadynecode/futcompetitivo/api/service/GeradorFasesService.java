package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.IniciarCampeonatoRequest;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GeradorFasesService {

    public Fase gerar(List<Participante> participantes, IniciarCampeonatoRequest request) {
        Fase fase = new Fase();
        fase.setOrdem(1);
        switch (request.formato()) {
            case LIGA -> {
                exigir(participantes.size() >= 3, "Liga precisa de pelo menos 3 participantes.");
                fase.setTipo(TipoFase.LIGA);
                fase.setTurnoEreturno(Boolean.TRUE.equals(request.turnoEreturno()));
                gerarLiga(fase, participantes);
            }
            case LIGA_PARCIAL -> {
                Integer jogos = request.jogosPorParticipante();
                exigir(jogos != null && jogos > 0 && jogos < participantes.size(),
                        "jogosPorParticipante deve ser positivo e menor que o total de participantes.");
                exigir(((long) participantes.size() * jogos) % 2 == 0,
                        "O total de participantes multiplicado pelos jogos deve ser par.");
                fase.setTipo(TipoFase.LIGA);
                fase.setJogosPorParticipante(jogos);
                gerarLigaParcial(fase, participantes, jogos);
            }
            case SWISS -> {
                exigir(participantes.size() >= 2, "Swiss precisa de pelo menos 2 participantes.");
                exigir(request.totalRodadas() != null && request.totalRodadas() > 0,
                        "totalRodadas é obrigatório para Swiss.");
                fase.setTipo(TipoFase.SWISS);
                fase.setTotalRodadas(request.totalRodadas());
            }
            case ELIMINATORIO, MISTA -> throw new RegraCampeonatoException(
                    "Formato ainda não implementado no projeto de origem: " + request.formato());
        }
        return fase;
    }

    private void gerarLiga(Fase fase, List<Participante> participantes) {
        List<Participante> lista = new ArrayList<>(participantes);
        if (lista.size() % 2 != 0) lista.add(null);
        int totalRodadas = lista.size() - 1;
        for (int numero = 0; numero < totalRodadas; numero++) {
            Rodada rodada = new Rodada(numero + 1);
            for (int jogo = 0; jogo < lista.size() / 2; jogo++) {
                Participante casa = lista.get(jogo);
                Participante fora = lista.get(lista.size() - 1 - jogo);
                if (casa == null || fora == null) {
                    rodada.setFolga(casa == null ? fora : casa);
                } else {
                    rodada.getPartidas().add(numero % 2 == 1
                            ? new Partida(fora, casa) : new Partida(casa, fora));
                }
            }
            fase.getRodadas().add(rodada);
            lista.add(1, lista.remove(lista.size() - 1));
        }
        if (fase.isTurnoEreturno()) {
            for (int i = 0; i < totalRodadas; i++) {
                Rodada turno = fase.getRodadas().get(i);
                Rodada retorno = new Rodada(totalRodadas + i + 1);
                retorno.setFolga(turno.getFolga());
                turno.getPartidas().forEach(p -> retorno.getPartidas()
                        .add(new Partida(p.getVisitante(), p.getMandante())));
                fase.getRodadas().add(retorno);
            }
        }
    }

    private void gerarLigaParcial(Fase fase, List<Participante> participantes, int jogos) {
        Map<Long, Integer> grau = new HashMap<>();
        participantes.forEach(p -> grau.put(p.getId(), jogos));
        List<Partida> partidas = new ArrayList<>();
        exigir(parear(participantes, grau, partidas), "Não foi possível gerar a liga parcial.");
        while (!partidas.isEmpty()) {
            Rodada rodada = new Rodada(fase.getRodadas().size() + 1);
            Set<Long> usados = new HashSet<>();
            Iterator<Partida> iterator = partidas.iterator();
            while (iterator.hasNext()) {
                Partida p = iterator.next();
                if (!usados.contains(p.getMandante().getId()) && !usados.contains(p.getVisitante().getId())) {
                    rodada.getPartidas().add(p);
                    usados.add(p.getMandante().getId());
                    usados.add(p.getVisitante().getId());
                    iterator.remove();
                }
            }
            fase.getRodadas().add(rodada);
        }
    }

    private boolean parear(List<Participante> participantes, Map<Long, Integer> grau, List<Partida> partidas) {
        Participante primeiro = participantes.stream().filter(p -> grau.get(p.getId()) > 0)
                .max(Comparator.comparingInt(p -> grau.get(p.getId()))).orElse(null);
        if (primeiro == null) return true;
        List<Participante> candidatos = participantes.stream()
                .filter(p -> !p.getId().equals(primeiro.getId()) && grau.get(p.getId()) > 0)
                .filter(p -> partidas.stream().noneMatch(j ->
                        (j.getMandante().getId().equals(primeiro.getId()) && j.getVisitante().getId().equals(p.getId()))
                        || (j.getVisitante().getId().equals(primeiro.getId()) && j.getMandante().getId().equals(p.getId()))))
                .sorted(Comparator.comparingInt((Participante p) -> grau.get(p.getId())).reversed()).toList();
        for (Participante segundo : candidatos) {
            partidas.add(new Partida(primeiro, segundo));
            grau.compute(primeiro.getId(), (id, valor) -> valor - 1);
            grau.compute(segundo.getId(), (id, valor) -> valor - 1);
            if (parear(participantes, grau, partidas)) return true;
            partidas.remove(partidas.size() - 1);
            grau.compute(primeiro.getId(), (id, valor) -> valor + 1);
            grau.compute(segundo.getId(), (id, valor) -> valor + 1);
        }
        return false;
    }

    private void exigir(boolean condicao, String mensagem) {
        if (!condicao) throw new RegraCampeonatoException(mensagem);
    }
}
