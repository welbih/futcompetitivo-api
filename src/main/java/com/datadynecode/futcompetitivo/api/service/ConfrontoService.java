package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ConfrontoService {

    public void gerarPartidas(Confronto confronto) {
        gerarPartidas(confronto, java.util.Set.of());
    }

    public void gerarPartidas(Confronto confronto, java.util.Set<Long> desistentes) {
        if (!confronto.getPartidas().isEmpty())
            throw new RegraCampeonatoException("As partidas do confronto já foram geradas.");
        if (confronto.getOrigemUm() != null)
            confronto.setParticipanteUm(confronto.isTerceiroLugar() ? perdedor(confronto.getOrigemUm(), desistentes) : vencedor(confronto.getOrigemUm(), desistentes));
        if (confronto.getOrigemDois() != null)
            confronto.setParticipanteDois(confronto.isTerceiroLugar() ? perdedor(confronto.getOrigemDois(), desistentes) : vencedor(confronto.getOrigemDois(), desistentes));
        if (confronto.getParticipanteUm() == null || confronto.getParticipanteDois() == null) return;
        if (confronto.getTipo() != TipoConfronto.JOGO_UNICO && confronto.getTipo() != TipoConfronto.IDA_E_VOLTA)
            throw new RegraCampeonatoException("Formato de confronto ainda não implementado: " + confronto.getTipo());
        confronto.getPartidas().add(new Partida(confronto.getParticipanteUm(), confronto.getParticipanteDois()));
        if (confronto.getTipo() == TipoConfronto.IDA_E_VOLTA)
            confronto.getPartidas().add(new Partida(confronto.getParticipanteDois(), confronto.getParticipanteUm()));
    }

    public Participante perdedor(Confronto confronto) {
        return perdedor(confronto, java.util.Set.of());
    }

    public Participante perdedor(Confronto confronto, java.util.Set<Long> desistentes) {
        Participante vencedor = vencedor(confronto, desistentes);
        if (vencedor == null) return null;
        if (confronto.getParticipanteUm() == null || confronto.getParticipanteDois() == null)
            throw new RegraCampeonatoException("Confronto com folga não possui perdedor.");
        return vencedor.getId().equals(confronto.getParticipanteUm().getId())
                ? confronto.getParticipanteDois() : confronto.getParticipanteUm();
    }

    public Participante vencedor(Confronto confronto) {
        return vencedor(confronto, java.util.Set.of());
    }

    public Participante vencedor(Confronto confronto, java.util.Set<Long> desistentes) {
        Participante um = confronto.getParticipanteUm();
        Participante dois = confronto.getParticipanteDois();
        if (um == null && confronto.getOrigemUm() != null)
            um = confronto.isTerceiroLugar() ? perdedor(confronto.getOrigemUm(), desistentes) : vencedor(confronto.getOrigemUm(), desistentes);
        if (dois == null && confronto.getOrigemDois() != null)
            dois = confronto.isTerceiroLugar() ? perdedor(confronto.getOrigemDois(), desistentes) : vencedor(confronto.getOrigemDois(), desistentes);
        if (um != null && desistentes.contains(um.getId())) um = null;
        if (dois != null && desistentes.contains(dois.getId())) dois = null;
        if (um == null) return dois;
        if (dois == null) return um;
        if (!confronto.getPartidas().isEmpty() && confronto.getPartidas().stream().allMatch(p -> p.isFinalizada()
                || (p.isWo() && p.getStatus() == StatusPartida.CANCELADA))
                && confronto.getPartidas().stream().anyMatch(Partida::isWo)) {
            return confronto.getPartidas().stream().filter(p -> p.isWo() && p.isFinalizada())
                    .map(Partida::vencedor).filter(Objects::nonNull).findFirst().orElse(null);
        }
        int esperado = switch (confronto.getTipo()) {
            case JOGO_UNICO -> 1;
            case IDA_E_VOLTA -> 2;
            default -> throw new RegraCampeonatoException("Formato de confronto ainda não implementado: " + confronto.getTipo());
        };
        if (confronto.getPartidas().size() != esperado
                || confronto.getPartidas().stream().anyMatch(p -> !p.isFinalizada()))
            throw new RegraCampeonatoException("As partidas do confronto não foram finalizadas.");
        long golsUm = 0;
        long golsDois = 0;
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
