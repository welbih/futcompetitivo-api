package com.datadynecode.futcompetitivo.api.suporte;

import com.datadynecode.futcompetitivo.api.dto.CampeonatoResponse;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.service.CampeonatoService;

/** Simula as solicitacoes explicitas de um administrador nos testes de torneio completo. */
public final class AvancoEliminatorio {
    private AvancoEliminatorio() {}
    public static CampeonatoResponse solicitar(CampeonatoService service, Long id) {
        var campeonato = service.buscar(id);
        if (campeonato.aguardandoMataMata()) return service.iniciarMataMata(id);
        if (campeonato.status()!=CampeonatoStatus.EM_ANDAMENTO || campeonato.fases().isEmpty()) return campeonato;
        var fase = campeonato.fases().getLast();
        if (fase.tipo()!=TipoFase.ELIMINATORIA || fase.rodadas().stream().anyMatch(r -> r.status()==RodadaStatus.EM_ANDAMENTO)) return campeonato;
        var proxima = fase.rodadas().stream().filter(r -> r.status()==RodadaStatus.PENDENTE).findFirst();
        return proxima.isPresent() ? service.iniciarRodadaMataMata(id,proxima.get().id()) : campeonato;
    }
}
