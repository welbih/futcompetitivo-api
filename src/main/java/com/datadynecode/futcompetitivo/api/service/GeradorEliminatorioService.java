package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeradorEliminatorioService {
    private final ConfrontoService confrontos;

    public void gerar(Fase fase, List<Participante> participantes) {
        if (participantes.size() < 4)
            throw new RegraCampeonatoException("Eliminatório precisa de pelo menos 4 participantes.");
        int tamanho = Integer.highestOneBit(participantes.size() - 1) << 1;
        fase.setTipo(TipoFase.ELIMINATORIA);
        if (fase.getFormatoEliminatorio() == null) fase.setFormatoEliminatorio(FormatoEliminatorio.JOGO_UNICO);
        fase.setTurnoEreturno(fase.getFormatoEliminatorio() != FormatoEliminatorio.JOGO_UNICO);
        fase.setTotalRodadas(Integer.numberOfTrailingZeros(tamanho));
        List<Confronto> anteriores = new ArrayList<>();
        for (int i = 0; i < tamanho / 2; i++) {
            Confronto confronto = new Confronto();
            anteriores.add(confronto);
        }
        fase.getConfrontos().addAll(anteriores);
        for (int numero = 1; numero <= fase.getTotalRodadas(); numero++) {
            fase.getRodadas().add(new Rodada(numero));
            if (numero == fase.getTotalRodadas()) break;
            List<Confronto> proximos = new ArrayList<>();
            for (int i = 0; i < anteriores.size(); i += 2) {
                Confronto confronto = new Confronto();
                confronto.setOrigemUm(anteriores.get(i));
                confronto.setOrigemDois(anteriores.get(i + 1));
                proximos.add(confronto);
            }
            fase.getConfrontos().addAll(proximos);
            anteriores = proximos;
        }
        for (Confronto confronto : fase.getConfrontos()) {
            boolean finalUnica = confronto == fase.getConfrontos().getLast()
                    && fase.getFormatoEliminatorio() == FormatoEliminatorio.IDA_E_VOLTA_FINAL_UNICA;
            confronto.setTipo(fase.getFormatoEliminatorio() == FormatoEliminatorio.JOGO_UNICO || finalUnica
                    ? TipoConfronto.JOGO_UNICO : TipoConfronto.IDA_E_VOLTA);
        }
        atualizarParticipantes(fase, participantes);
    }

    public void atualizarParticipantes(Fase fase, List<Participante> classificados) {
        int tamanho = 1 << fase.getTotalRodadas();
        List<Integer> sementes = new ArrayList<>(List.of(1, 2));
        for (int vagas = 4; vagas <= tamanho; vagas *= 2) {
            List<Integer> expandidas = new ArrayList<>();
            for (int semente : sementes) {
                expandidas.add(semente);
                expandidas.add(vagas + 1 - semente);
            }
            sementes = expandidas;
        }
        for (int i = 0; i < tamanho / 2; i++) {
            Confronto confronto = fase.getConfrontos().get(i);
            int um = sementes.get(2 * i) - 1;
            int dois = sementes.get(2 * i + 1) - 1;
            confronto.setParticipanteUm(um < classificados.size() ? classificados.get(um) : null);
            confronto.setParticipanteDois(dois < classificados.size() ? classificados.get(dois) : null);
        }
        prepararRodada(fase, fase.getRodadas().getFirst());
    }

    public void prepararRodada(Fase fase, Rodada rodada) {
        int quantidade = 1 << (fase.getTotalRodadas() - rodada.getNumero());
        int inicio = (1 << fase.getTotalRodadas()) - 2 * quantidade;
        for (Confronto confronto : fase.getConfrontos().subList(inicio, inicio + quantidade)) {
            if (confronto.getOrigemUm() != null)
                confronto.setParticipanteUm(confrontos.vencedor(confronto.getOrigemUm()));
            if (confronto.getOrigemDois() != null)
                confronto.setParticipanteDois(confrontos.vencedor(confronto.getOrigemDois()));
            if (confronto.getPartidas().isEmpty()) {
                confrontos.gerarPartidas(confronto);
                rodada.getPartidas().addAll(confronto.getPartidas());
            } else {
                for (int i = 0; i < confronto.getPartidas().size(); i++) {
                    Partida partida = confronto.getPartidas().get(i);
                    partida.setMandante(i == 0 ? confronto.getParticipanteUm() : confronto.getParticipanteDois());
                    partida.setVisitante(i == 0 ? confronto.getParticipanteDois() : confronto.getParticipanteUm());
                }
            }
        }
        if (rodada.getStatus() == RodadaStatus.PENDENTE)
            rodada.setStatus(RodadaStatus.EM_ANDAMENTO);
    }

    public Participante campeao(Fase fase) {
        return confrontos.vencedor(fase.getConfrontos().getLast());
    }

}
