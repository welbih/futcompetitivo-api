package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.dto.ConfrontoInicialRequest;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeradorEliminatorioService {
    private final ConfrontoService confrontos;

    public void gerar(Fase fase, List<Participante> participantes) {
        gerar(fase, participantes, null);
    }

    public void gerar(Fase fase, List<Participante> participantes, List<ConfrontoInicialRequest> primeiraRodada) {
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
        if (fase.isDisputaTerceiroLugar()) {
            Confronto finalConfronto = fase.getConfrontos().getLast();
            Confronto terceiro = new Confronto();
            terceiro.setTerceiroLugar(true);
            terceiro.setTipo(fase.getFormatoEliminatorio() != FormatoEliminatorio.IDA_E_VOLTA
                    ? TipoConfronto.JOGO_UNICO : TipoConfronto.IDA_E_VOLTA);
            terceiro.setOrigemUm(finalConfronto.getOrigemUm());
            terceiro.setOrigemDois(finalConfronto.getOrigemDois());
            fase.getConfrontos().add(fase.getConfrontos().size() - 1, terceiro);
        }
        if (fase.getFormaGeracaoMataMata() == null) fase.setFormaGeracaoMataMata(FormaGeracaoMataMata.AUTOMATICA);
        if (primeiraRodada == null) {
            var ordem = new ArrayList<>(participantes);
            if (fase.getFormaGeracaoMataMata() == FormaGeracaoMataMata.ALEATORIA) Collections.shuffle(ordem);
            atualizarParticipantes(fase, ordem);
        }
        else {
            definirConfrontosManuais(fase, participantes, primeiraRodada, tamanho / 2);
            fase.setConfrontosManuais(true);
            prepararRodada(fase, fase.getRodadas().getFirst());
        }
    }

    private void definirConfrontosManuais(Fase fase, List<Participante> participantes,
            List<ConfrontoInicialRequest> primeiraRodada, int quantidade) {
        if (primeiraRodada.size() != quantidade)
            throw new RegraCampeonatoException("Informe todos os confrontos da primeira rodada, incluindo folgas: " + quantidade + ".");
        Map<Long, Participante> porId = new HashMap<>();
        participantes.forEach(p -> porId.put(p.getId(), p));
        Set<Long> usados = new HashSet<>();
        for (int i = 0; i < primeiraRodada.size(); i++) {
            var par = primeiraRodada.get(i);
            if (par == null || (par.mandanteId() == null && par.visitanteId() == null))
                throw new RegraCampeonatoException("Cada confronto deve conter pelo menos um participante.");
            for (Long id : Arrays.asList(par.mandanteId(), par.visitanteId())) {
                if (id != null && (!porId.containsKey(id) || !usados.add(id)))
                    throw new RegraCampeonatoException("Informe apenas participantes elegíveis, sem repetição nos confrontos.");
            }
            var confronto = fase.getConfrontos().get(i);
            confronto.setParticipanteUm(porId.get(par.mandanteId()));
            confronto.setParticipanteDois(porId.get(par.visitanteId()));
        }
        if (!usados.equals(porId.keySet()))
            throw new RegraCampeonatoException("Todos os participantes elegíveis devem aparecer uma única vez na primeira rodada.");
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
        prepararRodada(fase, rodada, Set.of());
    }

    public void prepararRodada(Fase fase, Rodada rodada, Set<Long> desistentes) {
        int quantidade = 1 << (fase.getTotalRodadas() - rodada.getNumero());
        int inicio = (1 << fase.getTotalRodadas()) - 2 * quantidade;
        List<Confronto> chave = fase.getConfrontos().stream().filter(c -> !c.isTerceiroLugar()).toList();
        List<Confronto> etapa = new ArrayList<>(chave.subList(inicio, inicio + quantidade));
        if (rodada.getNumero().equals(fase.getTotalRodadas()))
            fase.getConfrontos().stream().filter(Confronto::isTerceiroLugar).forEach(etapa::add);
        for (Confronto confronto : etapa) {
            if (confronto.getOrigemUm() != null)
                confronto.setParticipanteUm(confronto.isTerceiroLugar() ? confrontos.perdedor(confronto.getOrigemUm(), desistentes)
                        : confrontos.vencedor(confronto.getOrigemUm(), desistentes));
            if (confronto.getOrigemDois() != null)
                confronto.setParticipanteDois(confronto.isTerceiroLugar() ? confrontos.perdedor(confronto.getOrigemDois(), desistentes)
                        : confrontos.vencedor(confronto.getOrigemDois(), desistentes));
            if (confronto.getPartidas().isEmpty()) {
                confrontos.gerarPartidas(confronto, desistentes);
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

    public Participante campeao(Fase fase, Set<Long> desistentes) {
        return confrontos.vencedor(fase.getConfrontos().getLast(), desistentes);
    }

    public Participante terceiroColocado(Fase fase, Set<Long> desistentes) {
        return confrontos.vencedor(fase.getConfrontos().stream().filter(Confronto::isTerceiroLugar).findFirst().orElseThrow(), desistentes);
    }

    public Participante terceiroColocado(Fase fase) {
        return confrontos.vencedor(fase.getConfrontos().stream().filter(Confronto::isTerceiroLugar).findFirst().orElseThrow());
    }

}
