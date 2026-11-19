package com.datadynecode.futcompetitivo.api.mapper;

import com.datadynecode.futcompetitivo.api.dto.EtapaResponse;

public final class EtapaEliminatoriaMapper {
    private EtapaEliminatoriaMapper() {}

    public static EtapaResponse identificar(int confrontos, boolean preliminar) {
        if (preliminar) return new EtapaResponse("PRELIMINAR", "Fase preliminar");
        return switch (confrontos) {
            case 1 -> new EtapaResponse("FINAL", "Final");
            case 2 -> new EtapaResponse("SEMIFINAL", "Semifinal");
            case 4 -> new EtapaResponse("QUARTAS_DE_FINAL", "Quartas de final");
            case 8 -> new EtapaResponse("OITAVAS_DE_FINAL", "Oitavas de final");
            case 16 -> new EtapaResponse("DEZESSEIS_AVOS_DE_FINAL", "Dezesseis avos de final");
            case 32 -> new EtapaResponse("TRINTA_E_DOIS_AVOS_DE_FINAL", "Trinta e dois avos de final");
            default -> new EtapaResponse("ELIMINATORIA_" + (2 * confrontos),
                    "Eliminatória de " + (2 * confrontos) + " participantes");
        };
    }
}
