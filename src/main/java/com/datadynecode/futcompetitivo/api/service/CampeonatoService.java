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
    private final ValidadorResultadoEliminatorioService validadorEliminatorio;
    private final ClassificacaoGruposService classificacaoGrupos;

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
        return registrarResultado(id, partidaId, request, true, true);
    }

    private CampeonatoResponse registrarResultado(Long id, Long partidaId, ResultadoRequest request,
            boolean finalizarAutomaticamente, boolean validarConfrontos) {
        Campeonato campeonato = buscarEntidade(id);
        exigir(campeonato.getStatus() != CampeonatoStatus.CRIADO, "Campeonato ainda não iniciado.");
        List<Rodada> rodadas = rodadas(campeonato);
        Rodada rodada = rodadas.stream().filter(r -> r.getPartidas().stream()
                .anyMatch(p -> p.getId().equals(partidaId))).findFirst()
                .orElseThrow(() -> naoEncontrado("Partida", partidaId));
        Partida partida = rodada.getPartidas().stream().filter(p -> p.getId().equals(partidaId)).findFirst().orElseThrow();
        exigir(partida.isFinalizada() || rodada.getStatus() == RodadaStatus.EM_ANDAMENTO,
                "Só é possível adicionar resultado em uma rodada em andamento.");
        Fase fase = faseDaRodada(campeonato, rodada);
        rodadas = fase.getRodadas();
        exigirFasesAnterioresFinalizadas(campeonato, fase);
        exigir(rodadas.stream().filter(r -> r.getNumero() < rodada.getNumero())
                .allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA), "Rodada anterior ainda não finalizada.");
        validarResultado(request, fase.getTipo());
        if (validarConfrontos) validadorEliminatorio.validar(fase, Map.of(partidaId, request));
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
        if (fase.getQuantidadeClassificados() != null && campeonato.getFases().size() > 1) {
            exigir(campeonato.getFases().getLast().getRodadas().stream()
                            .flatMap(r -> r.getPartidas().stream()).noneMatch(Partida::isFinalizada),
                    "Não é possível corrigir a fase classificatória após registrar resultados no mata-mata.");
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
        resultados.forEach(r -> validarResultado(r.resultado(), faseDaRodada(campeonato, rodada).getTipo()));
        Map<Long, ResultadoRequest> novosResultados = new HashMap<>();
        resultados.forEach(r -> novosResultados.put(r.partidaId(), r.resultado()));
        validadorEliminatorio.validar(faseDaRodada(campeonato, rodada), novosResultados);
        resultados.forEach(r -> registrarResultado(id, r.partidaId(), r.resultado(), false, false));
        return mapper.toResponse(campeonato);
    }

    @Transactional
    public CampeonatoResponse finalizarRodada(Long id, Long rodadaId) {
        Campeonato campeonato = buscarEntidade(id);
        List<Rodada> rodadas = rodadas(campeonato);
        Rodada rodada = rodadas.stream().filter(r -> r.getId().equals(rodadaId)).findFirst()
                .orElseThrow(() -> naoEncontrado("Rodada", rodadaId));
        Fase fase = faseDaRodada(campeonato, rodada);
        rodadas = fase.getRodadas();
        exigirFasesAnterioresFinalizadas(campeonato, fase);
        exigir(rodada.getStatus() == RodadaStatus.EM_ANDAMENTO, "Rodada deve estar em andamento.");
        exigir(rodadas.stream().filter(r -> r.getNumero() < rodada.getNumero())
                .allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA), "Rodada anterior ainda não finalizada.");
        exigir(rodada.getPartidas().stream().allMatch(Partida::isFinalizada),
                "Todas as partidas devem ter resultado antes de finalizar a rodada.");
        finalizar(campeonato, rodadas, rodada);
        return mapper.toResponse(repository.saveAndFlush(campeonato));
    }

    private List<Rodada> rodadas(Campeonato campeonato) {
        return campeonato.getFases().stream().sorted(Comparator.comparing(Fase::getOrdem))
                .flatMap(f -> f.getRodadas().stream().sorted(Comparator.comparing(Rodada::getNumero))).toList();
    }

    private Fase faseDaRodada(Campeonato campeonato, Rodada rodada) {
        return campeonato.getFases().stream().filter(f -> f.getRodadas().contains(rodada)).findFirst().orElseThrow();
    }

    private void exigirFasesAnterioresFinalizadas(Campeonato campeonato, Fase fase) {
        exigir(campeonato.getFases().stream().filter(f -> f.getOrdem() < fase.getOrdem())
                .flatMap(f -> f.getRodadas().stream()).allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA),
                "Fase anterior ainda não finalizada.");
    }

    private void finalizar(Campeonato campeonato, List<Rodada> rodadas, Rodada rodada) {
        rodada.setStatus(RodadaStatus.FINALIZADA);
        Fase fase = faseDaRodada(campeonato, rodada);
        if (fase.getTipo() == TipoFase.ELIMINATORIA) {
            rodadas.stream().filter(r -> r.getNumero() == rodada.getNumero() + 1).findFirst()
                    .ifPresent(proxima -> eliminatorio.prepararRodada(fase, proxima));
            if (rodadas.stream().allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA))
                campeonato.setStatus(CampeonatoStatus.FINALIZADO);
            return;
        }
        if (fase.getTipo() == TipoFase.GRUPOS) classificacaoGrupos.recalcular(fase, rodada.getNumero());
        else recalcularClassificacoes(campeonato, rodadas, rodada.getNumero());
        boolean todasGeradas = fase.getTipo() != TipoFase.SWISS
                || fase.getRodadas().size() == fase.getTotalRodadas();
        if (todasGeradas && rodadas.stream().allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA)) {
            if (fase.getQuantidadeClassificados() != null) {
                List<Participante> classificados = fase.getTipo() == TipoFase.GRUPOS
                        ? classificacaoGrupos.classificados(fase, campeonato.getParticipantes())
                        : ultima(campeonato).getTabela().stream().limit(fase.getQuantidadeClassificados())
                                .map(ClassificacaoParticipante::getParticipante).toList();
                if (campeonato.getFases().size() == 1) {
                    Fase mataMata = new Fase();
                    mataMata.setOrdem(2);
                    eliminatorio.gerar(mataMata, classificados);
                    campeonato.getFases().add(mataMata);
                } else eliminatorio.atualizarParticipantes(campeonato.getFases().getLast(), classificados);
            } else campeonato.setStatus(CampeonatoStatus.FINALIZADO);
        }
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
        Campeonato campeonato = buscarEntidade(id);
        exigir(campeonato.getFases().isEmpty() || campeonato.getFases().getFirst().getTipo() != TipoFase.GRUPOS,
                "Consulte a classificação de cada grupo pelo endpoint de grupos.");
        Classificacao classificacao = ultima(campeonato);
        if (classificacao == null) throw new RegraCampeonatoException("Não há classificação disponível.");
        return mapper.toResponse(classificacao);
    }

    public List<ParticipanteResponse> classificados(Long id, int quantidade) {
        exigir(quantidade > 0, "quantidade deve ser positiva.");
        Campeonato campeonato = buscarEntidade(id);
        if (!campeonato.getFases().isEmpty() && campeonato.getFases().getFirst().getTipo() == TipoFase.GRUPOS) {
            exigir(campeonato.getFases().getFirst().getRodadas().stream()
                    .allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA), "Fase de grupos ainda não finalizada.");
            List<Long> ids = classificacaoGrupos.classificados(campeonato.getFases().getFirst(), campeonato.getParticipantes())
                    .stream().limit(quantidade).map(Participante::getId).toList();
            var participantes = mapper.toResponse(campeonato).participantes();
            return ids.stream().map(participanteId -> participantes.stream()
                    .filter(p -> p.id().equals(participanteId)).findFirst().orElseThrow()).toList();
        }
        return classificacao(id).tabela().stream().limit(quantidade)
                .map(ClassificacaoParticipanteResponse::participante).toList();
    }

    public List<GrupoResponse> grupos(Long id) {
        Campeonato campeonato = buscarEntidade(id);
        exigir(!campeonato.getFases().isEmpty() && campeonato.getFases().getFirst().getTipo() == TipoFase.GRUPOS,
                "Campeonato não utiliza fase de grupos.");
        return campeonato.getFases().getFirst().getGrupos().stream().map(mapper::toResponse).toList();
    }

    public ClassificacaoResponse classificacaoGrupo(Long id, Long grupoId) {
        GrupoResponse grupo = grupos(id).stream().filter(g -> g.id().equals(grupoId)).findFirst()
                .orElseThrow(() -> naoEncontrado("Grupo", grupoId));
        exigir(!grupo.classificacaoPorRodada().isEmpty(), "Não há classificação disponível para o grupo.");
        return grupo.classificacaoPorRodada().getLast();
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
        Fase fase = campeonato.getFases().getLast();
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
