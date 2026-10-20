package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.IniciarCampeonatoRequest;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import java.util.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GeradorFasesService {
    private final GeradorEliminatorioService eliminatorio;

    public Fase gerar(List<Participante> participantes, IniciarCampeonatoRequest request) {
        exigir(request.formatoEliminatorio() == null || request.formato() == TipoFormato.ELIMINATORIO,
                "formatoEliminatorio é permitido apenas no formato ELIMINATORIO.");
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
                exigir(jogos != null && jogos >= 3,
                        "A liga parcial deve ter no mínimo 3 jogos por participante.");
                exigir(jogos < participantes.size(),
                        "jogosPorParticipante deve ser menor que o total de participantes.");
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
            case ELIMINATORIO -> {
                fase.setFormatoEliminatorio(request.formatoEliminatorio());
                eliminatorio.gerar(fase, participantes);
            }
            case MISTA -> {
                exigir(participantes.size() >= 4, "O formato misto deve ter no mínimo 4 participantes.");
                Integer classificados = request.quantidadeClassificados();
                exigir(classificados != null && classificados >= 4,
                        "O mata-mata deve ter no mínimo 4 classificados.");
                exigir(classificados <= participantes.size(),
                        "A quantidade de classificados não pode exceder o total de participantes.");
                fase.setTipo(TipoFase.LIGA);
                fase.setQuantidadeClassificados(classificados);
                fase.setTurnoEreturno(Boolean.TRUE.equals(request.turnoEreturno()));
                gerarLiga(fase, participantes);
            }
            case GRUPOS -> {
                exigir(participantes.size() >= 4, "A fase de grupos deve ter no mínimo 4 participantes.");
                Integer classificados = request.quantidadeClassificados();
                exigir(classificados != null && classificados >= 4,
                        "O mata-mata deve ter no mínimo 4 classificados.");
                exigir(classificados <= participantes.size(),
                        "A quantidade de classificados não pode exceder o total de participantes.");
                fase.setTipo(TipoFase.GRUPOS);
                fase.setQuantidadeClassificados(classificados);
                fase.setTurnoEreturno(Boolean.TRUE.equals(request.turnoEreturno()));
                gerarGrupos(fase, participantes);
            }
        }
        if (!fase.getRodadas().isEmpty())
            fase.getRodadas().getFirst().setStatus(RodadaStatus.EM_ANDAMENTO);
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

    private void gerarGrupos(Fase fase, List<Participante> participantes) {
        for (int indiceGrupo = 0; indiceGrupo < 2; indiceGrupo++) {
            Grupo grupo = new Grupo();
            grupo.setNome(indiceGrupo == 0 ? "Grupo A" : "Grupo B");
            for (int i = indiceGrupo; i < participantes.size(); i += 2)
                grupo.getParticipantes().add(participantes.get(i));
            fase.getGrupos().add(grupo);
            Fase agendaGrupo = new Fase();
            agendaGrupo.setTurnoEreturno(fase.isTurnoEreturno());
            gerarLiga(agendaGrupo, grupo.getParticipantes());
            for (Rodada rodada : agendaGrupo.getRodadas()) {
                while (fase.getRodadas().size() < rodada.getNumero())
                    fase.getRodadas().add(new Rodada(fase.getRodadas().size() + 1));
                fase.getRodadas().get(rodada.getNumero() - 1).getPartidas().addAll(rodada.getPartidas());
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
