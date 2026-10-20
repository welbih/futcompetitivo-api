package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.ResultadoRequest;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ValidadorResultadoEliminatorioService {
    private final ConfrontoService confrontos;

    public void validar(Fase fase, Map<Long, ResultadoRequest> novosResultados) {
        if (fase.getTipo() != TipoFase.ELIMINATORIA) return;
        for (Confronto confronto : fase.getConfrontos()) {
            if (confronto.getPartidas().stream().noneMatch(p -> novosResultados.containsKey(p.getId()))) continue;
            Participante novoVencedor = validar(confronto, novosResultados);
            if (novoVencedor != null && confronto.getPartidas().stream().allMatch(Partida::isFinalizada)
                    && !confrontos.vencedor(confronto).getId().equals(novoVencedor.getId())) {
                exigir(fase.getConfrontos().stream()
                        .filter(c -> (c.getOrigemUm() != null && c.getOrigemUm().getId().equals(confronto.getId()))
                                || (c.getOrigemDois() != null && c.getOrigemDois().getId().equals(confronto.getId())))
                        .flatMap(c -> c.getPartidas().stream()).noneMatch(Partida::isFinalizada),
                        "Não é possível alterar o vencedor: o confronto seguinte já possui resultado.");
            }
        }
    }

    private Participante validar(Confronto confronto, Map<Long, ResultadoRequest> novos) {
        Partida ida = confronto.getPartidas().getFirst();
        ResultadoRequest primeiro = resultado(ida, novos);
        if (confronto.getTipo() == TipoConfronto.JOGO_UNICO) {
            if (primeiro == null) return null;
            validarPenaltis(primeiro, primeiro.golsMandante().equals(primeiro.golsVisitante()));
            return vencedor(ida, primeiro, primeiro.golsMandante().longValue() - primeiro.golsVisitante());
        }
        exigir(primeiro == null || !temPenaltis(primeiro), "Pênaltis não são permitidos no jogo de ida.");
        Partida volta = confronto.getPartidas().getLast();
        ResultadoRequest segundo = resultado(volta, novos);
        exigir(segundo == null || primeiro != null, "Registre o resultado da ida antes da volta.");
        if (segundo == null) return null;
        long saldoMandanteVolta = (long) primeiro.golsVisitante() + segundo.golsMandante()
                - primeiro.golsMandante() - segundo.golsVisitante();
        validarPenaltis(segundo, saldoMandanteVolta == 0);
        return vencedor(volta, segundo, saldoMandanteVolta);
    }

    private ResultadoRequest resultado(Partida partida, Map<Long, ResultadoRequest> novos) {
        if (novos.containsKey(partida.getId())) return novos.get(partida.getId());
        if (!partida.isFinalizada()) return null;
        Resultado resultado = partida.getResultado();
        return new ResultadoRequest(resultado.getGolsMandante(), resultado.getGolsVisitante(),
                resultado.getPenaltisMandante(), resultado.getPenaltisVisitante(), resultado.isGolDeOuro());
    }

    private void validarPenaltis(ResultadoRequest resultado, boolean empate) {
        if (empate) {
            exigir(resultado.penaltisMandante() != null && resultado.penaltisVisitante() != null
                            && resultado.penaltisMandante() >= 0 && resultado.penaltisVisitante() >= 0
                            && !resultado.penaltisMandante().equals(resultado.penaltisVisitante()),
                    "Confronto empatado exige pênaltis válidos e um vencedor na partida decisiva.");
        } else exigir(!temPenaltis(resultado), "Pênaltis só são permitidos quando o confronto termina empatado.");
    }

    private boolean temPenaltis(ResultadoRequest resultado) {
        return resultado.penaltisMandante() != null || resultado.penaltisVisitante() != null;
    }

    private Participante vencedor(Partida decisiva, ResultadoRequest resultado, long saldo) {
        if (saldo != 0) return saldo > 0 ? decisiva.getMandante() : decisiva.getVisitante();
        return resultado.penaltisMandante() > resultado.penaltisVisitante() ? decisiva.getMandante() : decisiva.getVisitante();
    }

    private void exigir(boolean condicao, String mensagem) {
        if (!condicao) throw new RegraCampeonatoException(mensagem);
    }
}
