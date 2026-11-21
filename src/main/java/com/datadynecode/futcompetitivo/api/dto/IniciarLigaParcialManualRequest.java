package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record IniciarLigaParcialManualRequest(
        @NotNull @Valid IniciarCampeonatoRequest configuracao,
        @NotEmpty List<@NotNull @Valid RodadaManual> rodadas) {
    public record RodadaManual(@NotEmpty List<@NotNull @Valid ConfrontoInicialRequest> partidas) {}
}
