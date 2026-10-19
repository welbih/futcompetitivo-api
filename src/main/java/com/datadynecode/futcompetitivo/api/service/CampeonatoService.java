package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import com.datadynecode.futcompetitivo.api.mapper.CampeonatoMapper;
import com.datadynecode.futcompetitivo.api.repository.CampeonatoRepository;
import com.datadynecode.futcompetitivo.api.repository.OrganizacaoRepository;
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
    private final ParticipanteService participanteService;
    private final OrganizacaoRepository organizacaoRepository;
    private final CampeonatoMapper mapper;
    private final GeradorFasesService geradorFases;
    private final GeradorClassificacaoService geradorClassificacao;
    private final GeradorRodadaSwissService geradorSwiss;
    private final GeradorEliminatorioService eliminatorio;

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
        exigir(campeonato.getOrganizacao().isAtivo(), "Organização deve estar ativa.");
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
        return registrarResultado(id, partidaId, request, true);
    }

    private CampeonatoResponse registrarResultado(Long id, Long partidaId, ResultadoRequest request,
            boolean finalizarAutomaticamente) {
        Campeonato campeonato = buscarEntidade(id);
        exigir(campeonato.getStatus() != CampeonatoStatus.CRIADO, "Campeonato ainda não iniciado.");
        List<Rodada> rodadas = campeonato.getFases().stream().flatMap(f -> f.getRodadas().stream())
                .sorted(Comparator.comparing(Rodada::getNumero)).toList();
        Rodada rodada = rodadas.stream().filter(r -> r.getPartidas().stream()
                .anyMatch(p -> p.getId().equals(partidaId))).findFirst()
                .orElseThrow(() -> naoEncontrado("Partida", partidaId));
        Partida partida = rodada.getPartidas().stream().filter(p -> p.getId().equals(partidaId)).findFirst().orElseThrow();
        exigir(partida.isFinalizada() || rodada.getStatus() == RodadaStatus.EM_ANDAMENTO,
                "Só é possível adicionar resultado em uma rodada em andamento.");
        exigir(rodadas.stream().filter(r -> r.getNumero() < rodada.getNumero())
                .allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA), "Rodada anterior ainda não finalizada.");
        Fase fase = campeonato.getFases().getFirst();
        validarResultado(request, fase.getTipo());
        if (fase.getTipo() == TipoFase.ELIMINATORIA && partida.isFinalizada()) {
            Resultado anterior = partida.getResultado();
            var antigo = new ResultadoRequest(anterior.getGolsMandante(), anterior.getGolsVisitante(),
                    anterior.getPenaltisMandante(), anterior.getPenaltisVisitante(), anterior.isGolDeOuro());
            if (!eliminatorio.vencedor(partida, antigo).getId().equals(eliminatorio.vencedor(partida, request).getId())) {
                exigir(fase.getConfrontos().stream()
                        .filter(c -> c.getOrigemUm() != null && c.getOrigemUm().getPartidas().contains(partida)
                                || c.getOrigemDois() != null && c.getOrigemDois().getPartidas().contains(partida))
                        .flatMap(c -> c.getPartidas().stream()).noneMatch(Partida::isFinalizada),
                        "Não é possível alterar o vencedor: o confronto seguinte já possui resultado.");
            }
        }
        if (partida.isFinalizada()
                && Objects.equals(partida.getResultado().getGolsMandante(), request.golsMandante())
                && Objects.equals(partida.getResultado().getGolsVisitante(), request.golsVisitante())
                && Objects.equals(partida.getResultado().getPenaltisMandante(), request.penaltisMandante())
                && Objects.equals(partida.getResultado().getPenaltisVisitante(), request.penaltisVisitante())) {
            if (finalizarAutomaticamente && rodada.getStatus() == RodadaStatus.EM_ANDAMENTO
                    && rodada.getPartidas().stream().allMatch(Partida::isFinalizada)) {
                finalizar(campeonato, rodadas, rodada);
                return mapper.toResponse(repository.saveAndFlush(campeonato));
            }
            return mapper.toResponse(campeonato);
        }
        partida.setResultado(new Resultado(Boolean.TRUE.equals(request.golDeOuro()), request.golsMandante(), request.golsVisitante(),
                request.penaltisMandante(), request.penaltisVisitante()));
        partida.setStatus(StatusPartida.FINALIZADA);
        if (rodada.getStatus() == RodadaStatus.FINALIZADA
                || (finalizarAutomaticamente && rodada.getPartidas().stream().allMatch(Partida::isFinalizada))) {
            finalizar(campeonato, rodadas, rodada);
        }
        return mapper.toResponse(repository.saveAndFlush(campeonato));
    }

    @Transactional
    public CampeonatoResponse registrarResultadosSemFinalizar(Long id, Long rodadaId,
            List<ResultadoPartidaRequest> resultados) {
        Campeonato campeonato = buscarEntidade(id);
        Rodada rodada = rodadas(campeonato).stream().filter(r -> r.getId().equals(rodadaId)).findFirst()
                .orElseThrow(() -> naoEncontrado("Rodada", rodadaId));
        exigir(rodada.getStatus() == RodadaStatus.EM_ANDAMENTO, "Rodada deve estar em andamento.");
        exigir(resultados != null && !resultados.isEmpty(), "resultados é obrigatório.");
        var ids = resultados.stream().map(ResultadoPartidaRequest::partidaId).toList();
        exigir(new HashSet<>(ids).size() == ids.size(), "A lista de resultados não pode conter partidas duplicadas.");
        exigir(new HashSet<>(rodada.getPartidas().stream().map(Partida::getId).toList()).containsAll(ids),
                "Informe apenas partidas pertencentes à rodada.");
        resultados.forEach(r -> validarResultado(r.resultado(), campeonato.getFases().getFirst().getTipo()));
        resultados.forEach(r -> registrarResultado(id, r.partidaId(), r.resultado(), false));
        return mapper.toResponse(campeonato);
    }

    @Transactional
    public CampeonatoResponse finalizarRodada(Long id, Long rodadaId) {
        Campeonato campeonato = buscarEntidade(id);
        List<Rodada> rodadas = rodadas(campeonato);
        Rodada rodada = rodadas.stream().filter(r -> r.getId().equals(rodadaId)).findFirst()
                .orElseThrow(() -> naoEncontrado("Rodada", rodadaId));
        exigir(rodada.getStatus() == RodadaStatus.EM_ANDAMENTO, "Rodada deve estar em andamento.");
        exigir(rodadas.stream().filter(r -> r.getNumero() < rodada.getNumero())
                .allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA), "Rodada anterior ainda não finalizada.");
        exigir(rodada.getPartidas().stream().allMatch(Partida::isFinalizada),
                "Todas as partidas devem ter resultado antes de finalizar a rodada.");
        finalizar(campeonato, rodadas, rodada);
        return mapper.toResponse(repository.saveAndFlush(campeonato));
    }

    private List<Rodada> rodadas(Campeonato campeonato) {
        return campeonato.getFases().stream().flatMap(f -> f.getRodadas().stream())
                .sorted(Comparator.comparing(Rodada::getNumero)).toList();
    }

    private void finalizar(Campeonato campeonato, List<Rodada> rodadas, Rodada rodada) {
        rodada.setStatus(RodadaStatus.FINALIZADA);
        Fase fase = campeonato.getFases().getFirst();
        if (fase.getTipo() == TipoFase.ELIMINATORIA) {
            rodadas.stream().filter(r -> r.getNumero() == rodada.getNumero() + 1).findFirst()
                    .ifPresent(proxima -> eliminatorio.prepararRodada(fase, proxima));
            if (rodadas.stream().allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA))
                campeonato.setStatus(CampeonatoStatus.FINALIZADO);
            return;
        }
        recalcularClassificacoes(campeonato, rodadas, rodada.getNumero());
        boolean todasGeradas = fase.getTipo() != TipoFase.SWISS
                || fase.getRodadas().size() == fase.getTotalRodadas();
        if (todasGeradas && rodadas.stream().allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA))
            campeonato.setStatus(CampeonatoStatus.FINALIZADO);
        rodadas.stream().filter(r -> r.getNumero() > rodada.getNumero()
                        && r.getStatus() == RodadaStatus.PENDENTE)
                .findFirst().filter(proxima -> rodadas.stream()
                        .filter(r -> r.getNumero() < proxima.getNumero())
                        .allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA))
                .ifPresent(proxima -> proxima.setStatus(RodadaStatus.EM_ANDAMENTO));
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
        exigir(request.participantes() != null && request.participantes().size() >= 4,
                "O campeonato deve ter no mínimo 4 participantes.");
        exigir(request.participantes().stream().map(ParticipanteRequest::jogadorId).distinct().count()
                        == request.participantes().size(),
                "O mesmo jogador não pode participar mais de uma vez no campeonato.");
        campeonato.setOrganizacao(organizacaoRepository.findByIdAndAtivoTrue(request.organizacaoId())
                .orElseThrow(() -> naoEncontrado("Organização ativa", request.organizacaoId())));
        List<Participante> participantes = request.participantes().stream()
                .map(participanteService::criarEntidade).toList();
        participantes.forEach(this::validarParticipante);
        campeonato.setEdicao(request.edicao());
        campeonato.getParticipantes().clear();
        campeonato.getParticipantes().addAll(participantes);
    }

    private void validarParticipante(Participante participante) {
        exigir(participante.isAtivo() && participante.getJogador().isAtivo()
                && participante.getEquipe().isAtivo(), "Participante, jogador e equipe devem estar ativos.");
    }

    public ParticipanteResponse vencedor(Long id) {
        Campeonato campeonato = buscarEntidade(id);
        exigir(campeonato.getStatus() == CampeonatoStatus.FINALIZADO, "Campeonato ainda não finalizado.");
        Fase fase = campeonato.getFases().getFirst();
        exigir(fase.getTipo() == TipoFase.ELIMINATORIA, "Campeonato não utiliza formato eliminatório.");
        return mapper.toResponse(campeonato).participantes().stream()
                .filter(p -> p.id().equals(eliminatorio.campeao(fase).getId())).findFirst().orElseThrow();
    }

    private void validarResultado(ResultadoRequest request, TipoFase tipo) {
        exigir(request.golsMandante() != null && request.golsVisitante() != null
                && request.golsMandante() >= 0 && request.golsVisitante() >= 0, "Gols devem ser não negativos.");
        boolean temPenaltis = request.penaltisMandante() != null || request.penaltisVisitante() != null;
        if (tipo == TipoFase.ELIMINATORIA) {
            exigir(!Boolean.TRUE.equals(request.golDeOuro()), "Gol de ouro não é utilizado no eliminatório.");
            if (request.golsMandante().equals(request.golsVisitante())) {
                exigir(request.penaltisMandante() != null && request.penaltisVisitante() != null
                                && request.penaltisMandante() >= 0 && request.penaltisVisitante() >= 0
                                && !request.penaltisMandante().equals(request.penaltisVisitante()),
                        "Partida empatada exige pênaltis válidos e um vencedor.");
            } else exigir(!temPenaltis, "Pênaltis só são permitidos em partidas empatadas.");
            return;
        }
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
