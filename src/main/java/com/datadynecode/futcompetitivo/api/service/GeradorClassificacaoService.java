package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.service.classificacao.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GeradorClassificacaoService {

    // Cada rodada recebe um novo snapshot; as estatísticas anteriores nunca são alteradas.
    public Classificacao gerar(List<Participante> participantes, List<Partida> partidasAcumuladas,
            int rodada, Classificacao anterior) {
        Map<Long, ClassificacaoParticipante> tabela = new LinkedHashMap<>();
        participantes.forEach(p -> tabela.put(p.getId(), new ClassificacaoParticipante(p)));
        for (Partida partida : partidasAcumuladas) {
            if (!partida.isFinalizada()) continue;
            Resultado resultado = partida.getResultado();
            processar(tabela.get(partida.getMandante().getId()),
                    resultado.getGolsMandante(), resultado.getGolsVisitante());
            processar(tabela.get(partida.getVisitante().getId()),
                    resultado.getGolsVisitante(), resultado.getGolsMandante());
        }
        List<ClassificacaoParticipante> linhas = new ArrayList<>(tabela.values());
        // O sorteio só resolve empates após os critérios esportivos.
        linhas.sort(new ComparadorClassificacao(List.of(new CriterioPontos(), new CriterioVitorias(),
                new CriterioSaldoGols(), new CriterioGolsPro(), new CriterioSorteio()))
                .thenComparing(c -> c.getParticipante().getId()));
        Map<Long, Integer> posicoesAnteriores = new HashMap<>();
        if (anterior != null) anterior.getTabela().forEach(c ->
                posicoesAnteriores.put(c.getParticipante().getId(), c.getPosicaoAtual()));
        for (int i = 0; i < linhas.size(); i++) {
            ClassificacaoParticipante linha = linhas.get(i);
            int posicao = i + 1;
            int antiga = posicoesAnteriores.getOrDefault(linha.getParticipante().getId(), posicao);
            linha.setPosicaoAtual(posicao);
            linha.setPosicaoAnterior(antiga);
            linha.setVariacao(Math.abs(antiga - posicao));
            linha.setMovimentacao(antiga > posicao ? MovimentacaoPosicao.SUBIU
                    : antiga < posicao ? MovimentacaoPosicao.DESCEU : MovimentacaoPosicao.MANTEVE);
        }
        Classificacao classificacao = new Classificacao();
        classificacao.setRodada(rodada);
        classificacao.setTabela(linhas);
        return classificacao;
    }

    private void processar(ClassificacaoParticipante linha, int golsPro, int golsContra) {
        linha.setJogos(linha.getJogos() + 1);
        linha.setGolsPro(linha.getGolsPro() + golsPro);
        linha.setGolsContra(linha.getGolsContra() + golsContra);
        linha.setSaldoGols(linha.getGolsPro() - linha.getGolsContra());
        if (golsPro > golsContra) {
            linha.setPontos(linha.getPontos() + 3);
            linha.setVitorias(linha.getVitorias() + 1);
        } else if (golsPro == golsContra) {
            linha.setPontos(linha.getPontos() + 1);
            linha.setEmpates(linha.getEmpates() + 1);
        } else {
            linha.setDerrotas(linha.getDerrotas() + 1);
        }
    }
}
