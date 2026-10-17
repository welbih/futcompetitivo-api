package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ConfrontoService {

    public void gerarPartidas(Confronto confronto) {
        if (!confronto.getPartidas().isEmpty())
            throw new RegraCampeonatoException("As partidas do confronto já foram geradas.");
        if (confronto.getOrigemUm() != null)
            confronto.setParticipanteUm(vencedor(confronto.getOrigemUm()));
        if (confronto.getOrigemDois() != null)
            confronto.setParticipanteDois(vencedor(confronto.getOrigemDois()));
        if (confronto.getParticipanteUm() == null || confronto.getParticipanteDois() == null) return;
        if (confronto.getTipo() != TipoConfronto.JOGO_UNICO && confronto.getTipo() != TipoConfronto.IDA_E_VOLTA)
            throw new RegraCampeonatoException("Formato de confronto ainda não implementado: " + confronto.getTipo());
        confronto.getPartidas().add(new Partida(confronto.getParticipanteUm(), confronto.getParticipanteDois()));
        if (confronto.getTipo() == TipoConfronto.IDA_E_VOLTA)
            confronto.getPartidas().add(new Partida(confronto.getParticipanteDois(), confronto.getParticipanteUm()));
    }

    public Participante vencedor(Confronto confronto) {
        Participante um = confronto.getParticipanteUm();
        Participante dois = confronto.getParticipanteDois();
        if (um == null && confronto.getOrigemUm() != null) um = vencedor(confronto.getOrigemUm());
        if (dois == null && confronto.getOrigemDois() != null) dois = vencedor(confronto.getOrigemDois());
        if (um == null) return dois;
        if (dois == null) return um;
        int esperado = switch (confronto.getTipo()) {
            case JOGO_UNICO -> 1;
            case IDA_E_VOLTA -> 2;
            default -> throw new RegraCampeonatoException("Formato de confronto ainda não implementado: " + confronto.getTipo());
        };
        if (confronto.getPartidas().size() != esperado
                || confronto.getPartidas().stream().anyMatch(p -> !p.isFinalizada()))
            throw new RegraCampeonatoException("As partidas do confronto não foram finalizadas.");
        int golsUm = 0;
        int golsDois = 0;
        for (Partida partida : confronto.getPartidas()) {
            boolean mandanteUm = Objects.equals(partida.getMandante().getId(), um.getId());
            golsUm += mandanteUm ? partida.getResultado().getGolsMandante() : partida.getResultado().getGolsVisitante();
            golsDois += mandanteUm ? partida.getResultado().getGolsVisitante() : partida.getResultado().getGolsMandante();
        }
        if (golsUm != golsDois) return golsUm > golsDois ? um : dois;
        Partida decisiva = confronto.getPartidas().getLast();
        Resultado resultado = decisiva.getResultado();
        if (resultado.getPenaltisMandante() == null || resultado.getPenaltisVisitante() == null
                || resultado.getPenaltisMandante().equals(resultado.getPenaltisVisitante()))
            throw new RegraCampeonatoException("Confronto empatado precisa de desempate.");
        return resultado.getPenaltisMandante() > resultado.getPenaltisVisitante()
                ? decisiva.getMandante() : decisiva.getVisitante();
    }
}
