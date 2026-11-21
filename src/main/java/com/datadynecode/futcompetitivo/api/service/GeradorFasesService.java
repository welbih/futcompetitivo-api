package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.IniciarCampeonatoRequest;
import com.datadynecode.futcompetitivo.api.dto.IniciarGruposManuaisRequest;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import java.util.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GeradorFasesService {
    private final GeradorEliminatorioService eliminatorio;

    public Fase gerarGruposManuais(List<Participante> participantes, IniciarGruposManuaisRequest request) {
        List<List<Long>> grupos = request.grupos();
        if (grupos == null) {
            exigir(request.grupoA() != null && request.grupoB() != null, "Informe grupos ou os campos grupoA e grupoB.");
            grupos = List.of(request.grupoA(),request.grupoB());
        } else exigir(request.grupoA() == null && request.grupoB() == null, "Não combine grupos com grupoA/grupoB.");
        exigir(grupos.size() >= 2 && grupos.stream().allMatch(g -> g != null && g.size() >= 3),
                "Informe pelo menos dois grupos, cada um com no mínimo três participantes.");
        List<Long> ids = grupos.stream().flatMap(List::stream).toList();
        exigir(ids.stream().allMatch(id -> id != null && id > 0), "Informe IDs de participantes válidos.");
        exigir(new HashSet<>(ids).size() == ids.size(), "Um participante não pode aparecer mais de uma vez nos grupos.");
        Map<Long, Participante> porId = new HashMap<>();
        participantes.forEach(p -> porId.put(p.getId(), p));
        exigir(new HashSet<>(ids).equals(porId.keySet()),
                "Os grupos devem conter todos os participantes do campeonato, sem incluir participantes de outros campeonatos.");
        Integer classificados = request.quantidadeClassificados();
        exigir(classificados != null && classificados >= 4 && classificados <= participantes.size(),
                "A quantidade de classificados deve ser de pelo menos quatro e não pode exceder o total de participantes.");
        int porGrupo = classificados / grupos.size();
        exigir(grupos.stream().allMatch(g -> g.size() >= porGrupo),
                "Cada grupo deve ter participantes suficientes para a quantidade de classificados informada.");
        Fase fase = new Fase();
        fase.setOrdem(1);
        fase.setTipo(TipoFase.GRUPOS);
        fase.setQuantidadeClassificados(classificados);
        fase.setTurnoEreturno(Boolean.TRUE.equals(request.turnoEreturno()));
        fase.setDisputaTerceiroLugar(Boolean.TRUE.equals(request.disputaTerceiroLugar()));
        fase.setFormatoEliminatorio(request.formatoEliminatorio());
        fase.setFormaGeracaoMataMata(request.formaGeracaoMataMata() == null
                ? FormaGeracaoMataMata.AUTOMATICA : request.formaGeracaoMataMata());
        gerarAgendaGrupos(fase, grupos.stream().map(g -> g.stream().map(porId::get).toList()).toList());
        fase.getRodadas().getFirst().setStatus(RodadaStatus.EM_ANDAMENTO);
        return fase;
    }

    public Fase gerar(List<Participante> participantes, IniciarCampeonatoRequest request) {
        return gerar(participantes,request,true);
    }

    private Fase gerar(List<Participante> participantes, IniciarCampeonatoRequest request,boolean gerarAgenda) {
        exigir(request.formatoEliminatorio() == null || request.formato() == TipoFormato.ELIMINATORIO
                        || request.formato() == TipoFormato.MISTA || request.formato() == TipoFormato.MISTA_PARCIAL || request.formato() == TipoFormato.GRUPOS,
                "formatoEliminatorio exige um formato com mata-mata.");
        Fase fase = new Fase();
        fase.setOrdem(1);
        exigir(request.quantidadeGrupos() == null || request.formato() == TipoFormato.GRUPOS,
                "quantidadeGrupos é permitido apenas no formato GRUPOS.");
        exigir(!Boolean.TRUE.equals(request.disputaTerceiroLugar())
                        || request.formato() == TipoFormato.ELIMINATORIO || request.formato() == TipoFormato.MISTA
                        || request.formato() == TipoFormato.GRUPOS || request.formato() == TipoFormato.MISTA_PARCIAL,
                "Disputa de terceiro lugar exige um formato com mata-mata.");
        fase.setDisputaTerceiroLugar(Boolean.TRUE.equals(request.disputaTerceiroLugar()));
        fase.setFormatoEliminatorio(request.formatoEliminatorio());
        boolean temMataMata = request.formato() == TipoFormato.ELIMINATORIO
                || request.formato() == TipoFormato.MISTA || request.formato() == TipoFormato.MISTA_PARCIAL || request.formato() == TipoFormato.GRUPOS;
        exigir(request.formaGeracaoMataMata() == null || temMataMata,
                "formaGeracaoMataMata exige um formato com mata-mata.");
        if (temMataMata) fase.setFormaGeracaoMataMata(request.formaGeracaoMataMata() != null
                ? request.formaGeracaoMataMata() : request.formato() == TipoFormato.ELIMINATORIO
                        ? FormaGeracaoMataMata.ALEATORIA : FormaGeracaoMataMata.AUTOMATICA);
        switch (request.formato()) {
            case LIGA -> {
                exigir(participantes.size() >= 3, "Liga precisa de pelo menos 3 participantes.");
                fase.setTipo(TipoFase.LIGA);
                fase.setTurnoEreturno(Boolean.TRUE.equals(request.turnoEreturno()));
                gerarLiga(fase, participantes);
            }
            case LIGA_PARCIAL, MISTA_PARCIAL -> {
                Integer jogos = request.jogosPorParticipante();
                exigir(jogos != null && jogos >= 3,
                        "A liga parcial deve ter no mínimo 3 jogos por participante.");
                exigir(jogos < participantes.size(),
                        "jogosPorParticipante deve ser menor que o total de participantes.");
                exigir(((long) participantes.size() * jogos) % 2 == 0,
                        "O total de participantes multiplicado pelos jogos deve ser par.");
                fase.setTipo(TipoFase.LIGA);
                fase.setJogosPorParticipante(jogos);
                if(request.formato()==TipoFormato.MISTA_PARCIAL) {
                    exigir(!Boolean.TRUE.equals(request.turnoEreturno()),"A liga parcial usa jogosPorParticipante, sem turno e returno.");
                    exigir(request.totalRodadas()==null,"Na liga parcial configure jogosPorParticipante; a agenda define o total de rodadas.");
                    Integer vagas=request.quantidadeClassificados();
                    exigir(vagas!=null && vagas>=4 && vagas<=participantes.size(),"Informe pelo menos quatro classificados, sem exceder o total de participantes.");
                    fase.setQuantidadeClassificados(vagas);
                }
                if(gerarAgenda) {
                    gerarLigaParcial(fase, participantes, jogos);
                    fase.setTotalRodadas(fase.getRodadas().size());
                }
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
                int quantidadeGrupos = request.quantidadeGrupos() == null ? 2 : request.quantidadeGrupos();
                exigir(quantidadeGrupos >= 2 && participantes.size() / quantidadeGrupos >= 3,
                        "Cada grupo deve ter no mínimo três participantes; reduza a quantidade de grupos.");
                Integer classificados = request.quantidadeClassificados();
                exigir(classificados != null && classificados >= 4,
                        "O mata-mata deve ter no mínimo 4 classificados.");
                exigir(classificados <= participantes.size(),
                        "A quantidade de classificados não pode exceder o total de participantes.");
                fase.setTipo(TipoFase.GRUPOS);
                fase.setQuantidadeClassificados(classificados);
                fase.setTurnoEreturno(Boolean.TRUE.equals(request.turnoEreturno()));
                gerarGrupos(fase, participantes, quantidadeGrupos);
            }
        }
        if (!fase.getRodadas().isEmpty())
            fase.getRodadas().getFirst().setStatus(RodadaStatus.EM_ANDAMENTO);
        return fase;
    }

    public Fase gerarLigaParcialManual(List<Participante> participantes,
            com.datadynecode.futcompetitivo.api.dto.IniciarLigaParcialManualRequest request) {
        exigir(request!=null && request.configuracao()!=null,"Informe a configuração da liga parcial.");
        var configuracao=request.configuracao();
        exigir(configuracao.formato()==TipoFormato.MISTA_PARCIAL || configuracao.formato()==TipoFormato.LIGA_PARCIAL,
                "A agenda manual exige LIGA_PARCIAL ou MISTA_PARCIAL.");
        Fase fase=gerar(participantes,configuracao,false);
        exigir(request.rodadas()!=null && !request.rodadas().isEmpty(),"Informe todas as rodadas da liga parcial.");
        Map<Long,Participante> porId=new HashMap<>();
        Map<Long,Integer> jogos=new HashMap<>();
        participantes.forEach(p -> {porId.put(p.getId(),p); jogos.put(p.getId(),0);});
        Set<String> confrontos=new HashSet<>();
        for(var entrada:request.rodadas()) {
            exigir(entrada!=null && entrada.partidas()!=null && !entrada.partidas().isEmpty(),"Cada rodada deve conter partidas.");
            Rodada rodada=new Rodada(fase.getRodadas().size()+1);
            Set<Long> usados=new HashSet<>();
            for(var p:entrada.partidas()) {
                exigir(p!=null && p.mandanteId()!=null && p.visitanteId()!=null
                        && porId.containsKey(p.mandanteId()) && porId.containsKey(p.visitanteId()),"Informe dois participantes do campeonato em cada jogo.");
                exigir(usados.add(p.mandanteId()) && usados.add(p.visitanteId()),"Um participante só pode jogar uma vez por rodada, contra outro participante.");
                String chave=Math.min(p.mandanteId(),p.visitanteId())+":"+Math.max(p.mandanteId(),p.visitanteId());
                exigir(confrontos.add(chave),"A liga parcial não permite repetir adversários, mesmo com mando invertido.");
                jogos.merge(p.mandanteId(),1,Integer::sum); jogos.merge(p.visitanteId(),1,Integer::sum);
                rodada.getPartidas().add(new Partida(porId.get(p.mandanteId()),porId.get(p.visitanteId())));
            }
            fase.getRodadas().add(rodada);
        }
        exigir(jogos.values().stream().allMatch(n -> n.equals(fase.getJogosPorParticipante())),
                "A agenda deve conter exatamente jogosPorParticipante jogos para cada participante.");
        fase.setTotalRodadas(fase.getRodadas().size());
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

    private void gerarGrupos(Fase fase, List<Participante> participantes, int quantidade) {
        List<List<Participante>> composicoes = new ArrayList<>();
        for (int i=0;i<quantidade;i++) composicoes.add(new ArrayList<>());
        for (int i = 0; i < participantes.size(); i++) composicoes.get(i % quantidade).add(participantes.get(i));
        gerarAgendaGrupos(fase, composicoes);
    }

    private String nomeGrupo(int indice) {
        StringBuilder nome = new StringBuilder();
        for (int valor=indice+1;valor>0;valor=(valor-1)/26) nome.insert(0,(char)('A'+(valor-1)%26));
        return nome.toString();
    }

    private void gerarAgendaGrupos(Fase fase, List<List<Participante>> composicoes) {
        for (int indiceGrupo = 0; indiceGrupo < composicoes.size(); indiceGrupo++) {
            Grupo grupo = new Grupo();
            grupo.setNome("Grupo " + nomeGrupo(indiceGrupo));
            grupo.getParticipantes().addAll(composicoes.get(indiceGrupo));
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
