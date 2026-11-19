package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.dto.JogadorEstatisticasResponse.*;
import com.datadynecode.futcompetitivo.api.mapper.JogadorEstatisticasMapper;
import com.datadynecode.futcompetitivo.api.mapper.JogadorMapper;
import com.datadynecode.futcompetitivo.api.repository.JogadorRepository;
import com.datadynecode.futcompetitivo.api.repository.PartidaRepository;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToLongFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JogadorEstatisticasService {
    private final JogadorRepository jogadores;
    private final PartidaRepository partidas;
    private final JogadorMapper jogadorMapper;
    private final JogadorEstatisticasMapper mapper;

    public JogadorEstatisticasResponse buscar(Long jogadorId, OrigemPartida origem) {
        var jogador = jogadores.findById(jogadorId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Jogador nao encontrado: " + jogadorId));
        var historico = partidas.buscarHistoricoFinalizado(jogadorId, origem.name()).stream()
                .map(p -> mapper.toResponse(p, jogadorId)).toList();
        long vitorias = 0, empates = 0, derrotas = 0, marcados = 0, sofridos = 0;
        Map<Long, ContagemAdversario> contagens = new TreeMap<>();
        for (var partida : historico) {
            marcados += partida.golsMarcados();
            sofridos += partida.golsSofridos();
            var adversario = contagens.computeIfAbsent(partida.adversario().id(), id -> new ContagemAdversario(partida.adversario()));
            // Somente o placar de gols: vencedor() da Partida inclui penaltis e nao se aplica a estas estatisticas.
            if (partida.saldoGols() > 0) { vitorias++; adversario.vitorias++; }
            else if (partida.saldoGols() < 0) { derrotas++; adversario.derrotas++; }
            else { empates++; adversario.empates++; }
        }
        var adversarios = contagens.values().stream().map(ContagemAdversario::toResponse).toList();
        var aplicadas = historico.stream().filter(p -> p.saldoGols() > 0).toList();
        var sofridas = historico.stream().filter(p -> p.saldoGols() < 0).toList();
        return new JogadorEstatisticasResponse(jogadorMapper.toResponse(jogador), origem,
                new Resumo(historico.size(), vitorias, empates, derrotas, marcados, sofridos, marcados - sofridos),
                maiores(aplicadas, Goleada::golsMarcados), maiores(aplicadas, Goleada::saldoGols),
                maiores(sofridas, Goleada::golsSofridos), maiores(sofridas, p -> -p.saldoGols()),
                maiores(adversarios.stream().filter(a -> a.vitorias() > 0).toList(), Adversario::vitorias),
                maiores(adversarios.stream().filter(a -> a.derrotas() > 0).toList(), Adversario::derrotas));
    }

    private <T> List<T> maiores(List<T> registros, ToLongFunction<T> valor) {
        if (registros.isEmpty()) return List.of();
        long maximo = registros.stream().mapToLong(valor).max().orElseThrow();
        return registros.stream().filter(r -> valor.applyAsLong(r) == maximo).toList();
    }

    private static class ContagemAdversario {
        private final JogadorResponse jogador;
        private long vitorias, empates, derrotas;
        private ContagemAdversario(JogadorResponse jogador) { this.jogador = jogador; }
        private Adversario toResponse() {
            return new Adversario(jogador, vitorias + empates + derrotas, vitorias, empates, derrotas);
        }
    }
}
