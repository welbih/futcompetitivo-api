package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import com.datadynecode.futcompetitivo.api.mapper.CampeonatoMapper;
import com.datadynecode.futcompetitivo.api.repository.CampeonatoRepository;
import com.datadynecode.futcompetitivo.api.repository.ParticipanteRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampeonatoService {
    private final CampeonatoRepository repository;
    private final ParticipanteRepository participanteRepository;
    private final CampeonatoMapper mapper;
    private final GeradorFasesService geradorFases;
    private final GeradorClassificacaoService geradorClassificacao;
    private final GeradorRodadaSwissService geradorSwiss;

    public List<CampeonatoResponse> listar() {
        return repository.findAll(Sort.by("id")).stream().map(mapper::toResponse).toList();
    }

    public CampeonatoResponse buscar(Long id) { return mapper.toResponse(buscarEntidade(id)); }

    @Transactional
    public CampeonatoResponse criar(CampeonatoRequest request) {
        Campeonato campeonato = new Campeonato();
        preencher(campeonato, request);
        return mapper.toResponse(repository.saveAndFlush(campeonato));
    }

    @Transactional
    public CampeonatoResponse atualizar(Long id, CampeonatoRequest request) {
        Campeonato campeonato = buscarEntidade(id);
        exigir(campeonato.getStatus() == CampeonatoStatus.CRIADO, "Campeonato já iniciado.");
        preencher(campeonato, request);
        return mapper.toResponse(repository.saveAndFlush(campeonato));
    }

    @Transactional
    public CampeonatoResponse iniciar(Long id, IniciarCampeonatoRequest request) {
        Campeonato campeonato = buscarEntidade(id);
        exigir(campeonato.getStatus() == CampeonatoStatus.CRIADO, "Campeonato já iniciado.");
        campeonato.getParticipantes().forEach(this::validarParticipante);
        Collections.shuffle(campeonato.getParticipantes());
        Fase fase = geradorFases.gerar(campeonato.getParticipantes(), request);
        campeonato.getFases().add(fase);
        if (fase.getTipo() == TipoFase.SWISS)
            geradorSwiss.gerar(fase, campeonato.getParticipantes(), null);
        campeonato.setStatus(CampeonatoStatus.EM_ANDAMENTO);
        return mapper.toResponse(repository.saveAndFlush(campeonato));
    }

    @Transactional
    public RodadaResponse gerarProximaRodadaSwiss(Long id) {
        Campeonato campeonato = buscarEntidade(id);
        exigir(campeonato.getStatus() == CampeonatoStatus.EM_ANDAMENTO, "Campeonato não está em andamento.");
        Fase fase = campeonato.getFases().getFirst();
        exigir(fase.getTipo() == TipoFase.SWISS, "Campeonato não utiliza formato Swiss.");
        geradorSwiss.gerar(fase, campeonato.getParticipantes(), ultima(campeonato));
        Campeonato salvo = repository.saveAndFlush(campeonato);
        return mapper.toResponse(salvo.getFases().getFirst().getRodadas().getLast());
    }

    @Transactional
    public CampeonatoResponse registrarResultado(Long id, Long partidaId, ResultadoRequest request) {
        Campeonato campeonato = buscarEntidade(id);
        exigir(campeonato.getStatus() != CampeonatoStatus.CRIADO, "Campeonato ainda não iniciado.");
        List<Rodada> rodadas = campeonato.getFases().stream().flatMap(f -> f.getRodadas().stream())
                .sorted(Comparator.comparing(Rodada::getNumero)).toList();
        Rodada rodada = rodadas.stream().filter(r -> r.getPartidas().stream()
                .anyMatch(p -> p.getId().equals(partidaId))).findFirst()
                .orElseThrow(() -> naoEncontrado("Partida", partidaId));
        Partida partida = rodada.getPartidas().stream().filter(p -> p.getId().equals(partidaId)).findFirst().orElseThrow();
        exigir(rodadas.stream().filter(r -> r.getNumero() < rodada.getNumero())
                .allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA), "Rodada anterior ainda não finalizada.");
        validarResultado(request);
        if (partida.isFinalizada()
                && Objects.equals(partida.getResultado().getGolsMandante(), request.golsMandante())
                && Objects.equals(partida.getResultado().getGolsVisitante(), request.golsVisitante())) {
            return mapper.toResponse(campeonato);
        }
        partida.setResultado(new Resultado(Boolean.TRUE.equals(request.golDeOuro()), request.golsMandante(), request.golsVisitante(),
                request.penaltisMandante(), request.penaltisVisitante()));
        partida.setStatus(StatusPartida.FINALIZADA);
        boolean finalizada = rodada.getPartidas().stream().allMatch(Partida::isFinalizada);
        rodada.setStatus(finalizada ? RodadaStatus.FINALIZADA : RodadaStatus.EM_ANDAMENTO);
        if (finalizada) {
            recalcularClassificacoes(campeonato, rodadas, rodada.getNumero());
            Fase fase = campeonato.getFases().getFirst();
            boolean todasGeradas = fase.getTipo() != TipoFase.SWISS
                    || fase.getRodadas().size() == fase.getTotalRodadas();
            if (todasGeradas && rodadas.stream().allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA))
                campeonato.setStatus(CampeonatoStatus.FINALIZADO);
        }
        return mapper.toResponse(repository.saveAndFlush(campeonato));
    }

    private void recalcularClassificacoes(Campeonato campeonato, List<Rodada> rodadas, int aPartirDaRodada) {
        campeonato.getClassificacaoPorRodada().removeIf(c -> c.getRodada() >= aPartirDaRodada);
        List<Partida> acumuladas = new ArrayList<>();
        for (Rodada rodada : rodadas) {
            if (rodada.getStatus() != RodadaStatus.FINALIZADA) break;
            acumuladas.addAll(rodada.getPartidas());
            if (rodada.getNumero() >= aPartirDaRodada) {
                campeonato.getClassificacaoPorRodada().add(geradorClassificacao.gerar(
                        campeonato.getParticipantes(), acumuladas, rodada.getNumero(), ultima(campeonato)));
            }
        }
    }

    public ClassificacaoResponse classificacao(Long id) {
        Classificacao classificacao = ultima(buscarEntidade(id));
        if (classificacao == null) throw new RegraCampeonatoException("Não há classificação disponível.");
        return mapper.toResponse(classificacao);
    }

    public List<ParticipanteResponse> classificados(Long id, int quantidade) {
        exigir(quantidade > 0, "quantidade deve ser positiva.");
        return classificacao(id).tabela().stream().limit(quantidade)
                .map(ClassificacaoParticipanteResponse::participante).toList();
    }

    private void preencher(Campeonato campeonato, CampeonatoRequest request) {
        exigir(new HashSet<>(request.participanteIds()).size() == request.participanteIds().size(),
                "A lista de participantes não pode conter duplicatas.");
        List<Participante> participantes = request.participanteIds().stream().map(id ->
                participanteRepository.findByIdAndAtivoTrue(id)
                        .orElseThrow(() -> naoEncontrado("Participante", id))).toList();
        participantes.forEach(this::validarParticipante);
        campeonato.setNome(request.nome());
        campeonato.setEdicao(request.edicao());
        campeonato.getParticipantes().clear();
        campeonato.getParticipantes().addAll(participantes);
    }

    private void validarParticipante(Participante participante) {
        exigir(participante.isAtivo() && participante.getJogador().isAtivo()
                && participante.getEquipe().isAtivo(), "Participante, jogador e equipe devem estar ativos.");
    }

    private void validarResultado(ResultadoRequest request) {
        exigir(request.golsMandante() != null && request.golsVisitante() != null
                && request.golsMandante() >= 0 && request.golsVisitante() >= 0, "Gols devem ser não negativos.");
        boolean temPenaltis = request.penaltisMandante() != null || request.penaltisVisitante() != null;
        exigir(!temPenaltis, "Pênaltis não são utilizados nas fases de liga ou Swiss.");
        exigir(!Boolean.TRUE.equals(request.golDeOuro()), "Gol de ouro não é utilizado nas fases de liga ou Swiss.");
    }

    private Classificacao ultima(Campeonato campeonato) {
        return campeonato.getClassificacaoPorRodada().stream()
                .max(Comparator.comparingInt(Classificacao::getRodada)).orElse(null);
    }

    private Campeonato buscarEntidade(Long id) {
        return repository.findById(id).orElseThrow(() -> naoEncontrado("Campeonato", id));
    }

    private ResponseStatusException naoEncontrado(String recurso, Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " não encontrado: " + id);
    }

    private void exigir(boolean condicao, String mensagem) {
        if (!condicao) throw new RegraCampeonatoException(mensagem);
    }
}
