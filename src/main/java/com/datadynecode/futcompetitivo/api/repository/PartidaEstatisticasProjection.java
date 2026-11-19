package com.datadynecode.futcompetitivo.api.repository;

/** Uma linha por partida, com contexto escalar para evitar colecoes e consultas N+1. */
public interface PartidaEstatisticasProjection {
    Long getPartidaId();
    Long getMandanteJogadorId();
    Long getVisitanteJogadorId();
    String getMandanteNome();
    String getMandanteApelido();
    Boolean getMandanteAtivo();
    String getVisitanteNome();
    String getVisitanteApelido();
    Boolean getVisitanteAtivo();
    Long getMandanteEquipeId();
    String getMandanteEquipeNome();
    String getMandanteEquipeLogo();
    Boolean getMandanteEquipeAtiva();
    Long getVisitanteEquipeId();
    String getVisitanteEquipeNome();
    String getVisitanteEquipeLogo();
    Boolean getVisitanteEquipeAtiva();
    Integer getGolsMandante();
    Integer getGolsVisitante();
    Integer getPenaltisMandante();
    Integer getPenaltisVisitante();
    Long getX1Id();
    Integer getNumeroPartida();
    Long getCampeonatoId();
    Long getOrganizacaoId();
    String getOrganizacaoNome();
    Integer getEdicao();
    Long getRodadaId();
    Integer getRodadaNumero();
    Long getFaseId();
    Integer getFaseOrdem();
    String getFaseTipo();
    Integer getTotalRodadas();
    Boolean getPreliminar();
    Boolean getTerceiroLugar();
    String getConfrontoTipo();
    Long getPrimeiraPartidaId();
}
