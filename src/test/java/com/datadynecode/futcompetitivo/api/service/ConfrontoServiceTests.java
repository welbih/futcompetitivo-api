package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import com.datadynecode.futcompetitivo.api.service.ConfrontoService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfrontoServiceTests {
    private final ConfrontoService service = new ConfrontoService();

    @Test
    void idaEVoltaSomaGolsDoMandanteEDoVisitante() {
        Confronto confronto = confronto(TipoConfronto.IDA_E_VOLTA);
        service.gerarPartidas(confronto);
        assertThrows(RegraCampeonatoException.class, () -> service.vencedor(confronto));
        finalizar(confronto.getPartidas().getFirst(), new Resultado(false, 0, 1, null, null));
        finalizar(confronto.getPartidas().getLast(), new Resultado(false, 0, 3, null, null));
        assertEquals(confronto.getParticipanteUm(), service.vencedor(confronto));
    }

    @Test
    void jogoUnicoUsaPenaltisApenasParaDesempatar() {
        Confronto confronto = confronto(TipoConfronto.JOGO_UNICO);
        service.gerarPartidas(confronto);
        finalizar(confronto.getPartidas().getFirst(), new Resultado(false, 1, 1, null, null));
        assertThrows(RegraCampeonatoException.class, () -> service.vencedor(confronto));
        finalizar(confronto.getPartidas().getFirst(), new Resultado(false, 1, 1, 3, 4));
        assertEquals(confronto.getParticipanteDois(), service.vencedor(confronto));
    }

    private Confronto confronto(TipoConfronto tipo) {
        Participante a = new Participante(); a.setId(1L);
        Participante b = new Participante(); b.setId(2L);
        Confronto confronto = new Confronto();
        confronto.setTipo(tipo);
        confronto.setParticipanteUm(a);
        confronto.setParticipanteDois(b);
        return confronto;
    }

    private void finalizar(Partida partida, Resultado resultado) {
        partida.setResultado(resultado);
        partida.setStatus(StatusPartida.FINALIZADA);
    }
}
