package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.FormatoEliminatorio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record IniciarEliminatorioManualRequest(
        FormatoEliminatorio formatoEliminatorio, Boolean disputaTerceiroLugar,
        @NotEmpty List<@NotNull @Valid ConfrontoInicialRequest> confrontosPrimeiraRodada,
        com.datadynecode.futcompetitivo.api.entity.FormaGeracaoMataMata formaGeracaoMataMata) {
    public IniciarEliminatorioManualRequest(FormatoEliminatorio formatoEliminatorio, Boolean disputaTerceiroLugar,
            List<ConfrontoInicialRequest> confrontosPrimeiraRodada) {
        this(formatoEliminatorio, disputaTerceiroLugar, confrontosPrimeiraRodada, null);
    }
}
