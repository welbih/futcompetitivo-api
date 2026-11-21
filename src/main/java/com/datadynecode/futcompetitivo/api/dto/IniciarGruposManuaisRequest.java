package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record IniciarGruposManuaisRequest(
        @Size(min = 3, message = "O grupo A deve ter pelo menos três participantes.")
        List<@NotNull @Positive Long> grupoA,
        @Size(min = 3, message = "O grupo B deve ter pelo menos três participantes.")
        List<@NotNull @Positive Long> grupoB,
        @NotNull(message = "quantidadeClassificados é obrigatório.") @Min(4) Integer quantidadeClassificados,
        Boolean turnoEreturno, Boolean disputaTerceiroLugar,
        com.datadynecode.futcompetitivo.api.entity.FormatoEliminatorio formatoEliminatorio,
        com.datadynecode.futcompetitivo.api.entity.FormaGeracaoMataMata formaGeracaoMataMata,
        @Size(min = 2) List<@NotNull @Size(min = 3) List<@NotNull @Positive Long>> grupos) {
    public IniciarGruposManuaisRequest(List<Long> grupoA, List<Long> grupoB, Integer quantidadeClassificados,
            Boolean turnoEreturno, Boolean disputaTerceiroLugar,
            com.datadynecode.futcompetitivo.api.entity.FormatoEliminatorio formatoEliminatorio,
            com.datadynecode.futcompetitivo.api.entity.FormaGeracaoMataMata formaGeracaoMataMata) {
        this(grupoA,grupoB,quantidadeClassificados,turnoEreturno,disputaTerceiroLugar,formatoEliminatorio,formaGeracaoMataMata,null);
    }
    public IniciarGruposManuaisRequest(List<Long> grupoA, List<Long> grupoB,
            Integer quantidadeClassificados, Boolean turnoEreturno, Boolean disputaTerceiroLugar,
            com.datadynecode.futcompetitivo.api.entity.FormatoEliminatorio formatoEliminatorio) {
        this(grupoA, grupoB, quantidadeClassificados, turnoEreturno, disputaTerceiroLugar, formatoEliminatorio, null);
    }
    public IniciarGruposManuaisRequest(List<Long> grupoA, List<Long> grupoB,
            Integer quantidadeClassificados, Boolean turnoEreturno, Boolean disputaTerceiroLugar) {
        this(grupoA, grupoB, quantidadeClassificados, turnoEreturno, disputaTerceiroLugar, null);
    }
}
