package com.datadynecode.futcompetitivo.api.suporte;

import com.datadynecode.futcompetitivo.api.entity.*;
import jakarta.persistence.EntityManager;

/** Historico pequeno com partidas compartilhadas entre rodada e confronto. */
public class HistoricoJogadorFixture {
    public final Jogador jogador, rivalUm, rivalDois;
    public final Equipe equipeJogador, equipeRival;
    public final Participante um, dois, tres;
    public final Campeonato campeonato;
    public final Fase fase;
    public final Rodada rodada;
    private final EntityManager em;

    public HistoricoJogadorFixture(EntityManager em) {
        this.em = em;
        jogador = jogador("Ana", "A10");
        rivalUm = jogador("Bruno", "B9");
        rivalDois = jogador("Carla", "C7");
        equipeJogador = equipe("Azul", "https://example.com/azul.png");
        equipeRival = equipe("Verde", "https://example.com/verde.png");
        um = participante(jogador, equipeJogador);
        dois = participante(rivalUm, equipeRival);
        tres = participante(rivalDois, equipeRival);
        var org = new Organizacao();
        org.setNome("Liga da Vila");
        em.persist(org);
        campeonato = new Campeonato();
        campeonato.setOrganizacao(org);
        campeonato.setEdicao(2026);
        campeonato.setStatus(CampeonatoStatus.EM_ANDAMENTO);
        fase = new Fase();
        fase.setOrdem(1);
        fase.setTipo(TipoFase.LIGA);
        fase.setTotalRodadas(2);
        rodada = new Rodada(1);
        rodada.setStatus(RodadaStatus.EM_ANDAMENTO);
        fase.getRodadas().add(rodada);
        campeonato.getFases().add(fase);
        em.persist(campeonato);
    }

    public Partida campeonato(Participante rival, boolean mandante, int marcados, int sofridos) {
        var partida = partida(rival, mandante, marcados, sofridos);
        rodada.getPartidas().add(partida);
        em.persist(partida);
        return partida;
    }

    public Partida x1(Participante rival, boolean mandante, int marcados, int sofridos) {
        var x1 = new ConfrontoPessoal();
        x1.setParticipanteUm(um);
        x1.setParticipanteDois(rival);
        x1.setTipo(TipoSeriePessoal.MELHOR_DE_TRES);
        var partida = partida(rival, mandante, marcados, sofridos);
        partida.setX1(x1);
        partida.setNumero(1);
        x1.getPartidas().add(partida);
        em.persist(x1);
        return partida;
    }

    private Partida partida(Participante rival, boolean mandante, int marcados, int sofridos) {
        var partida = new Partida(mandante ? um : rival, mandante ? rival : um);
        partida.setStatus(StatusPartida.FINALIZADA);
        partida.setResultado(new Resultado(false, mandante ? marcados : sofridos, mandante ? sofridos : marcados, null, null));
        return partida;
    }

    private Jogador jogador(String nome, String apelido) {
        var jogador = new Jogador(); jogador.setNome(nome); jogador.setApelido(apelido); em.persist(jogador); return jogador;
    }

    private Equipe equipe(String nome, String logo) {
        var equipe = new Equipe(); equipe.setNome(nome); equipe.setLogoUrl(logo); em.persist(equipe); return equipe;
    }

    private Participante participante(Jogador jogador, Equipe equipe) {
        var participante = new Participante(); participante.setJogador(jogador); participante.setEquipe(equipe); em.persist(participante); return participante;
    }
}
