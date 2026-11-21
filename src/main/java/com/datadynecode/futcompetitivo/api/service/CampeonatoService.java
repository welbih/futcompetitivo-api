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
    private final ClassificacaoAtualService classificacoesAtuais;
    private final EventosCampeonatoService eventos;

    public List<CampeonatoResponse> listar() {
        return repository.findAll(Sort.by("id")).stream().map(mapper::toResponse).toList();
    }

    public CampeonatoResponse buscar(Long id) { return mapper.toResponse(buscarEntidade(id)); }

    @Transactional
    public void deletar(Long id) {
        repository.delete(buscarParaAtualizar(id));
        eventos.registrar(id,MotivoAtualizacaoCampeonato.CAMPEONATO_EXCLUIDO);
    }

    @Transactional
    public CampeonatoResponse criar(CampeonatoRequest request) {
        Campeonato campeonato = new Campeonato();
        preencher(campeonato, request);
        return salvarNotificando(campeonato,MotivoAtualizacaoCampeonato.CAMPEONATO_CRIADO);
    }

    @Transactional
    public CampeonatoResponse atualizar(Long id, CampeonatoRequest request) {
        Campeonato campeonato = buscarParaAtualizar(id);
        exigir(campeonato.getStatus() == CampeonatoStatus.CRIADO, "Campeonato já iniciado.");
        preencher(campeonato, request);
        return salvarNotificando(campeonato,MotivoAtualizacaoCampeonato.CAMPEONATO_ALTERADO);
    }

    @Transactional
    public CampeonatoResponse alterarIdentificacao(Long id, IdentificacaoCampeonatoRequest request) {
        Campeonato campeonato = buscarParaAtualizar(id);
        exigir(request != null && request.organizacaoId() != null && request.organizacaoId() > 0
                && request.edicao() != null && request.edicao() > 0,
                "Informe uma organização e uma edição inteira positiva.");
        var organizacao = organizacaoRepository.findById(request.organizacaoId())
                .orElseThrow(() -> naoEncontrado("Organização", request.organizacaoId()));
        boolean organizacaoAlterada = !campeonato.getOrganizacao().getId().equals(organizacao.getId());
        exigir(!organizacaoAlterada || organizacao.isAtivo(), "A nova organização deve estar ativa.");
        if (!organizacaoAlterada && Objects.equals(campeonato.getEdicao(), request.edicao()))
            return mapper.toResponse(campeonato);
        campeonato.setOrganizacao(organizacao);
        campeonato.setEdicao(request.edicao());
        return salvarNotificando(campeonato, MotivoAtualizacaoCampeonato.CAMPEONATO_ALTERADO);
    }

    @Transactional
    public CampeonatoResponse iniciar(Long id, IniciarCampeonatoRequest request) {
        Campeonato campeonato = buscarParaAtualizar(id);
        validarInicio(campeonato);
        if (request.formato() != TipoFormato.ELIMINATORIO) Collections.shuffle(campeonato.getParticipantes());
        Fase fase = geradorFases.gerar(campeonato.getParticipantes(), request);
        campeonato.getFases().add(fase);
        if (fase.getTipo() == TipoFase.SWISS)
            geradorSwiss.gerar(fase, campeonato.getParticipantes(), null);
        campeonato.setStatus(CampeonatoStatus.EM_ANDAMENTO);
        return salvarNotificando(campeonato,MotivoAtualizacaoCampeonato.CAMPEONATO_INICIADO);
    }

    @Transactional
    public CampeonatoResponse iniciarComGruposManuais(Long id, IniciarGruposManuaisRequest request) {
        Campeonato campeonato = buscarParaAtualizar(id);
        validarInicio(campeonato);
        Fase fase = geradorFases.gerarGruposManuais(campeonato.getParticipantes(), request);
        campeonato.getFases().add(fase);
        campeonato.setStatus(CampeonatoStatus.EM_ANDAMENTO);
        return salvarNotificando(campeonato,MotivoAtualizacaoCampeonato.CAMPEONATO_INICIADO);
    }

    @Transactional
    public CampeonatoResponse iniciarEliminatorioManual(Long id, IniciarEliminatorioManualRequest request) {
        Campeonato campeonato = buscarParaAtualizar(id);
        validarInicio(campeonato);
        exigir(request != null && request.confrontosPrimeiraRodada() != null
                && !request.confrontosPrimeiraRodada().isEmpty(), "Informe os confrontos da primeira rodada.");
        Fase fase = new Fase();
        fase.setOrdem(1);
        fase.setFormatoEliminatorio(request.formatoEliminatorio());
        fase.setFormaGeracaoMataMata(request.formaGeracaoMataMata());
        fase.setDisputaTerceiroLugar(Boolean.TRUE.equals(request.disputaTerceiroLugar()));
        eliminatorio.gerar(fase, campeonato.getParticipantes(), request.confrontosPrimeiraRodada());
        campeonato.getFases().add(fase);
        campeonato.setStatus(CampeonatoStatus.EM_ANDAMENTO);
        finalizarRodadasResolvidas(campeonato);
        return salvarNotificando(campeonato,MotivoAtualizacaoCampeonato.CAMPEONATO_INICIADO);
    }

    @Transactional
    public CampeonatoResponse iniciarLigaParcialManual(Long id, IniciarLigaParcialManualRequest request) {
        Campeonato campeonato=buscarParaAtualizar(id);
        validarInicio(campeonato);
        Fase fase=geradorFases.gerarLigaParcialManual(campeonato.getParticipantes(),request);
        campeonato.getFases().add(fase);
        campeonato.setStatus(CampeonatoStatus.EM_ANDAMENTO);
        return salvarNotificando(campeonato,MotivoAtualizacaoCampeonato.CAMPEONATO_INICIADO);
    }

    @Transactional
    public CampeonatoResponse iniciarMataMata(Long id) {
        return iniciarMataMata(id, null);
    }

    @Transactional
    public CampeonatoResponse iniciarMataMata(Long id, IniciarMataMataRequest request) {
        Campeonato campeonato = buscarParaAtualizar(id);
        Fase classificatoria = exigirAguardandoMataMata(campeonato);
        Map<Long, Long> substituicoes = new HashMap<>();
        if (request != null && request.substituicoes() != null) {
            for (var substituicao : request.substituicoes()) {
                exigir(substituicao != null && substituicao.desistenteId() != null && substituicao.substitutoId() != null,
                        "Informe desistenteId e substitutoId.");
                exigir(substituicoes.putIfAbsent(substituicao.desistenteId(), substituicao.substitutoId()) == null,
                        "Não repita a substituição de uma vaga.");
            }
        }
        List<Participante> classificados = selecionarClassificados(campeonato, classificatoria, substituicoes);
        Fase mataMata = new Fase();
        mataMata.setOrdem(campeonato.getFases().size() + 1);
        mataMata.setDisputaTerceiroLugar(classificatoria.isDisputaTerceiroLugar());
        mataMata.setFormatoEliminatorio(classificatoria.getFormatoEliminatorio());
        mataMata.setFormaGeracaoMataMata(classificatoria.getFormaGeracaoMataMata());
        eliminatorio.gerar(mataMata, classificados, request == null ? null : request.confrontosPrimeiraRodada());
        campeonato.getFases().add(mataMata);
        campeonato.getSubstituicoesMataMata().putAll(substituicoes);
        return salvarNotificando(campeonato,MotivoAtualizacaoCampeonato.MATA_MATA_INICIADO);
    }

    public List<VagaMataMataResponse> vagasMataMata(Long id) {
        Campeonato campeonato = buscarEntidade(id);
        Fase fase = exigirAguardandoMataMata(campeonato);
        var classificados = classificadosOriginais(campeonato, fase);
        var fixos = classificados.stream().map(Participante::getId).collect(java.util.stream.Collectors.toSet());
        var respostas = mapper.toResponse(campeonato).participantes();
        return classificados.stream().filter(p -> campeonato.getParticipantesDesistentes().contains(p.getId()))
                .map(p -> new VagaMataMataResponse(p.getId(), grupoDoParticipante(fase, p.getId()),
                        respostas.stream().filter(c -> !fixos.contains(c.id())
                                && !campeonato.getParticipantesDesistentes().contains(c.id())
                                && Objects.equals(grupoDoParticipante(fase, c.id()), grupoDoParticipante(fase, p.getId())))
                                .toList())).toList();
    }

    @Transactional
    public CampeonatoResponse iniciarRodadaMataMata(Long id, Long rodadaId) {
        Campeonato campeonato = buscarParaAtualizar(id);
        exigir(campeonato.getStatus() == CampeonatoStatus.EM_ANDAMENTO, "Campeonato não está em andamento.");
        Rodada rodada = rodadas(campeonato).stream().filter(r -> r.getId().equals(rodadaId)).findFirst()
                .orElseThrow(() -> naoEncontrado("Rodada", rodadaId));
        Fase fase = faseDaRodada(campeonato, rodada);
        exigir(fase.getTipo() == TipoFase.ELIMINATORIA, "A solicitação de início da rodada é exclusiva do mata-mata.");
        exigirFasesAnterioresFinalizadas(campeonato, fase);
        exigir(rodada.getStatus() == RodadaStatus.PENDENTE, "Rodada já iniciada ou finalizada.");
        exigir(fase.getRodadas().stream().filter(r -> r.getNumero() < rodada.getNumero())
                .allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA
                        && r.getPartidas().stream().allMatch(Partida::isFinalizada)), "Rodada anterior ainda não finalizada.");
        eliminatorio.prepararRodada(fase, rodada, campeonato.getParticipantesDesistentes());
        aplicarWo(campeonato);
        finalizarRodadasResolvidas(campeonato);
        eventos.registrar(id,MotivoAtualizacaoCampeonato.RODADA_ABERTA,rodadaId,null);
        return mapper.toResponse(repository.saveAndFlush(campeonato));
    }

    @Transactional
    public CampeonatoResponse desistirParticipante(Long id, Long participanteId) {
        Campeonato campeonato = buscarParaAtualizar(id);
        exigir(campeonato.getStatus() == CampeonatoStatus.EM_ANDAMENTO, "A desistência exige campeonato em andamento.");
        exigir(campeonato.getParticipantes().stream().anyMatch(p -> p.getId().equals(participanteId)),
                "Participante não pertence ao campeonato.");
        // Preserva as vagas ja conquistadas: um W.O. duplo posterior nao promove outro jogador automaticamente.
        if (campeonato.getClassificadosBase().isEmpty() && campeonato.getFases().size() == 1) {
            Fase classificatoria = campeonato.getFases().getFirst();
            if (classificatoria.getQuantidadeClassificados() != null && classificatoria.getRodadas().stream()
                    .allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA && r.getPartidas().stream().allMatch(Partida::isFinalizada))) {
                campeonato.getClassificadosBase().addAll(classificadosOriginais(campeonato,classificatoria).stream().map(Participante::getId).toList());
            }
        }
        boolean novaDesistencia = campeonato.getParticipantesDesistentes().add(participanteId);
        Set<Rodada> alteradas = aplicarWo(campeonato);
        // Recalcula snapshots que possam conter W.O. agora duplo (0x0), sem modificar jogos disputados.
        for (Fase fase : campeonato.getFases()) {
            var finalizadas = fase.getRodadas().stream().filter(r -> r.getStatus() == RodadaStatus.FINALIZADA).toList();
            if (finalizadas.stream().anyMatch(alteradas::contains) && fase.getTipo() != TipoFase.ELIMINATORIA) {
                if (fase.getTipo() == TipoFase.GRUPOS) classificacaoGrupos.recalcular(fase, 1);
                else recalcularClassificacoes(campeonato, fase.getRodadas(), 1);
            }
        }
        finalizarRodadasResolvidas(campeonato);
        if(novaDesistencia || !alteradas.isEmpty()) eventos.registrar(id,MotivoAtualizacaoCampeonato.DESISTENCIA_REGISTRADA);
        return mapper.toResponse(repository.saveAndFlush(campeonato));
    }

    @Transactional
    public CampeonatoResponse cancelarDesistencia(Long id, Long participanteId) {
        Campeonato campeonato = buscarParaAtualizar(id);
        exigir(campeonato.getParticipantes().stream().anyMatch(p -> p.getId().equals(participanteId)),
                "Participante não pertence ao campeonato.");
        if (!campeonato.getParticipantesDesistentes().contains(participanteId)) return mapper.toResponse(campeonato);
        exigir(!campeonato.getSubstituicoesMataMata().containsKey(participanteId),
                "O participante já foi substituído na chave do mata-mata; a desistência não pode ser retirada.");

        Map<Fase, List<Rodada>> afetadas = new LinkedHashMap<>();
        for (Fase fase : campeonato.getFases()) {
            var rodadasAfetadas = fase.getRodadas().stream().filter(r -> r.getPartidas().stream()
                    .anyMatch(p -> woDoParticipante(p, participanteId))).toList();
            validarCancelamentoDesistencia(campeonato, fase, participanteId, rodadasAfetadas);
            if (!rodadasAfetadas.isEmpty()) afetadas.put(fase, rodadasAfetadas);
        }

        campeonato.getParticipantesDesistentes().remove(participanteId);
        for (var entrada : afetadas.entrySet()) {
            Fase fase = entrada.getKey();
            Integer reabrirDesde = null;
            for (Rodada rodada : entrada.getValue()) {
                for (Partida partida : rodada.getPartidas()) {
                    if (!woDoParticipante(partida, participanteId)) continue;
                    boolean mandanteDesistente = campeonato.getParticipantesDesistentes().contains(partida.getMandante().getId());
                    boolean visitanteDesistente = campeonato.getParticipantesDesistentes().contains(partida.getVisitante().getId());
                    if (mandanteDesistente || visitanteDesistente) {
                        partida.setResultado(new Resultado(false, mandanteDesistente ? 0 : 3, visitanteDesistente ? 0 : 3, null, null));
                    } else {
                        partida.setResultado(null);
                        partida.setWo(false);
                        partida.setStatus(StatusPartida.AGENDADA);
                        reabrirDesde = reabrirDesde == null ? rodada.getNumero() : Math.min(reabrirDesde, rodada.getNumero());
                    }
                }
            }
            if (reabrirDesde != null) {
                int primeira = Math.min(reabrirDesde, fase.getRodadas().stream()
                        .filter(r -> r.getStatus() == RodadaStatus.EM_ANDAMENTO)
                        .mapToInt(Rodada::getNumero).min().orElse(reabrirDesde));
                for (Rodada rodada : fase.getRodadas()) if (rodada.getNumero() >= primeira)
                    rodada.setStatus(rodada.getNumero() == primeira ? RodadaStatus.EM_ANDAMENTO : RodadaStatus.PENDENTE);
                campeonato.setStatus(CampeonatoStatus.EM_ANDAMENTO);
                if (fase.getQuantidadeClassificados() != null) campeonato.getClassificadosBase().clear();
            }
            int desde = entrada.getValue().stream().mapToInt(Rodada::getNumero).min().orElseThrow();
            if (fase.getTipo() == TipoFase.GRUPOS) classificacaoGrupos.recalcular(fase, desde);
            else if (fase.getTipo() != TipoFase.ELIMINATORIA) recalcularClassificacoes(campeonato, fase.getRodadas(), desde);
        }
        return salvarNotificando(campeonato, MotivoAtualizacaoCampeonato.DESISTENCIA_CANCELADA);
    }

    private boolean woDoParticipante(Partida partida, Long participanteId) {
        return partida.isWo() && partida.isFinalizada()
                && (partida.getMandante().getId().equals(participanteId) || partida.getVisitante().getId().equals(participanteId));
    }

    private void validarCancelamentoDesistencia(Campeonato campeonato, Fase fase, Long participanteId, List<Rodada> afetadas) {
        if (!afetadas.isEmpty()) {
            exigir(fase.getQuantidadeClassificados() == null || campeonato.getFases().size() == 1,
                    "Os W.O.s da classificatória já deram origem ao mata-mata; a desistência não pode ser retirada.");
            int primeira = afetadas.stream().mapToInt(Rodada::getNumero).min().orElseThrow();
            if (fase.getTipo() == TipoFase.SWISS || fase.getTipo() == TipoFase.ELIMINATORIA) {
                exigir(fase.getRodadas().stream().filter(r -> r.getNumero() > primeira)
                                .allMatch(r -> r.getStatus() == RodadaStatus.PENDENTE && r.getPartidas().isEmpty()),
                        "Já existem pareamentos ou uma etapa posterior do mata-mata; a desistência não pode ser retirada.");
            } else {
                int reabrir = afetadas.stream().filter(r -> r.getPartidas().stream().filter(p -> woDoParticipante(p, participanteId))
                        .anyMatch(p -> !outroParticipanteDesistente(campeonato, p, participanteId)))
                        .mapToInt(Rodada::getNumero).min().orElse(Integer.MAX_VALUE);
                exigir(fase.getRodadas().stream().filter(r -> r.getNumero() > reabrir)
                                .flatMap(r -> r.getPartidas().stream()).noneMatch(p -> p.isFinalizada() && !p.isWo()),
                        "Há jogos disputados em rodadas posteriores ao W.O.; a desistência não pode ser retirada.");
            }
        } else if (fase.getTipo() == TipoFase.ELIMINATORIA) {
            // Avancos por folga ou por resultado ja disputado tambem dependem da elegibilidade do desistente.
            int ultima = fase.getRodadas().stream().filter(r -> r.getPartidas().stream()
                    .anyMatch(p -> p.getMandante().getId().equals(participanteId) || p.getVisitante().getId().equals(participanteId)))
                    .mapToInt(Rodada::getNumero).max().orElse(1);
            boolean presente = fase.getConfrontos().stream().anyMatch(c ->
                    (c.getParticipanteUm() != null && c.getParticipanteUm().getId().equals(participanteId))
                    || (c.getParticipanteDois() != null && c.getParticipanteDois().getId().equals(participanteId)));
            exigir(!presente || fase.getRodadas().stream().filter(r -> r.getNumero() > ultima)
                            .allMatch(r -> r.getStatus() == RodadaStatus.PENDENTE && r.getPartidas().isEmpty()),
                    "A chave já avançou após a participação do desistente; a desistência não pode ser retirada.");
        }
    }

    private boolean outroParticipanteDesistente(Campeonato campeonato, Partida partida, Long participanteId) {
        Long outro = partida.getMandante().getId().equals(participanteId) ? partida.getVisitante().getId() : partida.getMandante().getId();
        return campeonato.getParticipantesDesistentes().contains(outro);
    }

    private Set<Rodada> aplicarWo(Campeonato campeonato) {
        Set<Rodada> alteradas = new HashSet<>();
        for (Rodada rodada : rodadas(campeonato)) {
            for (Partida partida : rodada.getPartidas()) {
                if (partida.getStatus() == StatusPartida.CANCELADA || (partida.isFinalizada() && !partida.isWo())) continue;
                boolean mandante = campeonato.getParticipantesDesistentes().contains(partida.getMandante().getId());
                boolean visitante = campeonato.getParticipantesDesistentes().contains(partida.getVisitante().getId());
                if (!mandante && !visitante) continue;
                int golsMandante = mandante ? 0 : 3;
                int golsVisitante = visitante ? 0 : 3;
                if (partida.isWo() && partida.isFinalizada() && partida.getResultado().getGolsMandante() == golsMandante
                        && partida.getResultado().getGolsVisitante() == golsVisitante) continue;
                partida.setResultado(new Resultado(false, golsMandante, golsVisitante, null, null));
                partida.setStatus(StatusPartida.FINALIZADA);
                partida.setWo(true);
                alteradas.add(rodada);
            }
        }
        return alteradas;
    }

    private void finalizarRodadasResolvidas(Campeonato campeonato) {
        for (Fase fase : campeonato.getFases()) {
            for (Rodada rodada : fase.getRodadas()) {
                if (rodada.getStatus() == RodadaStatus.EM_ANDAMENTO && rodada.getPartidas().stream().allMatch(Partida::isFinalizada))
                    finalizar(campeonato, fase.getRodadas(), rodada);
            }
        }
    }

    private Fase exigirAguardandoMataMata(Campeonato campeonato) {
        exigir(campeonato.getStatus() == CampeonatoStatus.EM_ANDAMENTO, "Campeonato não está em andamento.");
        exigir(campeonato.getFases().size() == 1, "O mata-mata já foi iniciado ou não há fase classificatória.");
        Fase fase = campeonato.getFases().getFirst();
        exigir(fase.getQuantidadeClassificados() != null, "O formato do campeonato não prevê mata-mata após a classificação.");
        exigir(!fase.getRodadas().isEmpty() && fase.getRodadas().stream().allMatch(r -> r.getStatus() == RodadaStatus.FINALIZADA
                && r.getPartidas().stream().allMatch(Partida::isFinalizada)), "Todos os jogos da fase classificatória devem estar finalizados.");
        return fase;
    }

    private List<Participante> classificadosOriginais(Campeonato campeonato, Fase fase) {
        if (!campeonato.getClassificadosBase().isEmpty()) {
            Map<Long, Participante> porId = new HashMap<>();
            campeonato.getParticipantes().forEach(p -> porId.put(p.getId(),p));
            return campeonato.getClassificadosBase().stream().map(porId::get).toList();
        }
        return fase.getTipo() == TipoFase.GRUPOS ? classificacaoGrupos.classificados(fase, campeonato.getParticipantes())
                : ultima(campeonato).getTabela().stream().limit(fase.getQuantidadeClassificados())
                        .map(ClassificacaoParticipante::getParticipante).toList();
    }

    private Long grupoDoParticipante(Fase fase, Long participanteId) {
        return fase.getGrupos().stream().filter(g -> g.getParticipantes().stream().anyMatch(p -> p.getId().equals(participanteId)))
                .map(Grupo::getId).findFirst().orElse(null);
    }

    private List<Participante> selecionarClassificados(Campeonato campeonato, Fase fase, Map<Long, Long> substituicoes) {
        var originais = classificadosOriginais(campeonato, fase);
        var vagas = originais.stream().filter(p -> campeonato.getParticipantesDesistentes().contains(p.getId()))
                .map(Participante::getId).collect(java.util.stream.Collectors.toSet());
        exigir(substituicoes.keySet().equals(vagas), "Defina manualmente o substituto de cada classificado desistente, sem substituir outras vagas.");
        Map<Long, Participante> porId = new HashMap<>();
        campeonato.getParticipantes().forEach(p -> porId.put(p.getId(), p));
        List<Participante> selecionados = new ArrayList<>();
        for (Participante original : originais) {
            Long selecionadoId = substituicoes.getOrDefault(original.getId(), original.getId());
            exigir(porId.containsKey(selecionadoId) && !campeonato.getParticipantesDesistentes().contains(selecionadoId),
                    "O substituto deve pertencer ao campeonato e não pode ser desistente.");
            exigir(Objects.equals(grupoDoParticipante(fase, original.getId()), grupoDoParticipante(fase, selecionadoId)),
                    "A substituição deve preservar a vaga do mesmo grupo.");
            selecionados.add(porId.get(selecionadoId));
        }
        exigir(selecionados.stream().map(Participante::getId).distinct().count() == selecionados.size(),
                "O mesmo participante não pode ocupar duas vagas no mata-mata.");
        return selecionados;
    }

    private void validarInicio(Campeonato campeonato) {
        exigir(campeonato.getStatus() == CampeonatoStatus.CRIADO, "Campeonato já iniciado.");
        campeonato.getParticipantes().forEach(this::validarParticipante);
        exigir(campeonato.getOrganizacao().isAtivo(), "Organização deve estar ativa.");
    }

    @Transactional
    public RodadaResponse gerarProximaRodadaSwiss(Long id) {
        Campeonato campeonato = buscarParaAtualizar(id);
        exigir(campeonato.getStatus() == CampeonatoStatus.EM_ANDAMENTO, "Campeonato não está em andamento.");
        Fase fase = campeonato.getFases().getFirst();
        exigir(fase.getTipo() == TipoFase.SWISS, "Campeonato não utiliza formato Swiss.");
        geradorSwiss.gerar(fase, campeonato.getParticipantes().stream()
                .filter(p -> !campeonato.getParticipantesDesistentes().contains(p.getId())).toList(), ultima(campeonato));
        Campeonato salvo = repository.saveAndFlush(campeonato);
        eventos.registrar(id,MotivoAtualizacaoCampeonato.RODADA_ABERTA,salvo.getFases().getFirst().getRodadas().getLast().getId(),null);
        return mapper.toResponse(salvo.getFases().getFirst().getRodadas().getLast());
    }

    @Transactional
    public CampeonatoResponse registrarResultado(Long id, Long partidaId, ResultadoRequest request) {
        return registrarResultado(id, partidaId, request, true, true);
    }

    private CampeonatoResponse registrarResultado(Long id, Long partidaId, ResultadoRequest request,
            boolean finalizarAutomaticamente, boolean validarConfrontos) {
        Campeonato campeonato = buscarParaAtualizar(id);
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
        exigir(!partida.isWo() || (Objects.equals(partida.getResultado().getGolsMandante(), request.golsMandante())
                && Objects.equals(partida.getResultado().getGolsVisitante(), request.golsVisitante())
                && request.penaltisMandante() == null && request.penaltisVisitante() == null), "O placar de W.O. não pode ser alterado manualmente.");
        if (validarConfrontos && !partida.isWo()) validadorEliminatorio.validar(fase, Map.of(partidaId, request));
        boolean videoAlterado = !Objects.equals(partida.getYoutubeUrl(),request.youtubeUrl());
        partida.setYoutubeUrl(request.youtubeUrl());
        if (partida.isFinalizada()
                && Objects.equals(partida.getResultado().getGolsMandante(), request.golsMandante())
                && Objects.equals(partida.getResultado().getGolsVisitante(), request.golsVisitante())
                && Objects.equals(partida.getResultado().getPenaltisMandante(), request.penaltisMandante())
                && Objects.equals(partida.getResultado().getPenaltisVisitante(), request.penaltisVisitante())) {
            if (finalizarAutomaticamente && rodada.getStatus() == RodadaStatus.EM_ANDAMENTO
                    && rodada.getPartidas().stream().allMatch(Partida::isFinalizada)) {
                finalizar(campeonato, rodadas, rodada);
                eventos.registrar(id,MotivoAtualizacaoCampeonato.RESULTADO_ALTERADO,rodada.getId(),partidaId);
                return mapper.toResponse(repository.saveAndFlush(campeonato));
            }
            if(videoAlterado) eventos.registrar(id,MotivoAtualizacaoCampeonato.RESULTADO_ALTERADO,rodada.getId(),partidaId);
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
        eventos.registrar(id,MotivoAtualizacaoCampeonato.RESULTADO_ALTERADO,rodada.getId(),partidaId);
        if (rodada.getStatus() == RodadaStatus.FINALIZADA
                || (finalizarAutomaticamente && rodada.getPartidas().stream().allMatch(Partida::isFinalizada))) {
            finalizar(campeonato, rodadas, rodada);
        }
        return mapper.toResponse(repository.saveAndFlush(campeonato));
    }

    @Transactional
    public CampeonatoResponse registrarResultadosSemFinalizar(Long id, Long rodadaId,
            List<ResultadoPartidaRequest> resultados) {
        Campeonato campeonato = buscarParaAtualizar(id);
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
        eventos.registrar(id,MotivoAtualizacaoCampeonato.RESULTADOS_ALTERADOS,rodadaId,null);
        return mapper.toResponse(campeonato);
    }

    @Transactional
    public CampeonatoResponse finalizarRodada(Long id, Long rodadaId) {
        Campeonato campeonato = buscarParaAtualizar(id);
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
        eventos.registrar(id,MotivoAtualizacaoCampeonato.RODADA_FINALIZADA,rodadaId,null);
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
            rodadas.stream().filter(r -> r.getNumero() == rodada.getNumero() + 1
                    && r.getStatus() != RodadaStatus.FINALIZADA)
                    .findFirst().ifPresent(proxima -> {
                        eliminatorio.prepararRodada(fase, proxima, campeonato.getParticipantesDesistentes());
                        aplicarWo(campeonato);
                        if (proxima.getPartidas().stream().allMatch(Partida::isFinalizada))
                            finalizar(campeonato, rodadas, proxima);
                    });
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
                if (campeonato.getFases().size() > 1) {
                    var classificados = selecionarClassificados(campeonato, fase, campeonato.getSubstituicoesMataMata());
                    Fase mataMata = campeonato.getFases().getLast();
                    if (mataMata.isConfrontosManuais() || mataMata.getFormaGeracaoMataMata() == FormaGeracaoMataMata.ALEATORIA) {
                        var atuais = mataMata.getConfrontos().stream().filter(c -> c.getOrigemUm() == null && !c.isTerceiroLugar())
                                .flatMap(c -> java.util.stream.Stream.of(c.getParticipanteUm(), c.getParticipanteDois()))
                                .filter(Objects::nonNull).map(Participante::getId).collect(java.util.stream.Collectors.toSet());
                        exigir(atuais.equals(classificados.stream().map(Participante::getId).collect(java.util.stream.Collectors.toSet())),
                                "A correção altera os participantes de uma chave manual ou já sorteada.");
                    } else eliminatorio.atualizarParticipantes(mataMata, classificados);
                }
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
        Classificacao classificacao = classificacoesAtuais.atual(campeonato);
        if (classificacao == null) throw new RegraCampeonatoException("Não há classificação disponível.");
        return mapper.toResponse(classificacao);
    }

    public List<ParticipanteResponse> classificados(Long id, int quantidade) {
        exigir(quantidade > 0, "quantidade deve ser positiva.");
        Campeonato campeonato = buscarEntidade(id);
        if (!campeonato.getClassificadosBase().isEmpty()) {
            var participantes = mapper.toResponse(campeonato).participantes();
            return campeonato.getClassificadosBase().stream().limit(quantidade)
                    .map(participanteId -> participantes.stream().filter(p -> p.id().equals(participanteId)).findFirst().orElseThrow()).toList();
        }
        if(!campeonato.getFases().isEmpty() && campeonato.getFases().getFirst().getQuantidadeClassificados()!=null)
            exigir(campeonato.getFases().getFirst().getRodadas().stream().allMatch(r -> r.getStatus()==RodadaStatus.FINALIZADA),
                    "Fase classificatória ainda não finalizada.");
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
        Fase fase=campeonato.getFases().getFirst();
        return fase.getGrupos().stream().map(g -> mapper.toResponse(fase,g)).toList();
    }

    public ClassificacaoResponse classificacaoGrupo(Long id, Long grupoId) {
        GrupoResponse grupo = grupos(id).stream().filter(g -> g.id().equals(grupoId)).findFirst()
                .orElseThrow(() -> naoEncontrado("Grupo", grupoId));
        exigir(grupo.classificacaoAtual()!=null, "Não há classificação disponível para o grupo.");
        return grupo.classificacaoAtual();
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
        Participante campeao = eliminatorio.campeao(fase, campeonato.getParticipantesDesistentes());
        exigir(campeao != null, "Não há vencedor elegível: todos os finalistas desistiram.");
        return mapper.toResponse(campeonato).participantes().stream()
                .filter(p -> p.id().equals(campeao.getId())).findFirst().orElseThrow();
    }

    public ParticipanteResponse terceiroLugar(Long id) {
        Campeonato campeonato = buscarEntidade(id);
        exigir(campeonato.getStatus() == CampeonatoStatus.FINALIZADO, "Campeonato ainda não finalizado.");
        Fase fase = campeonato.getFases().getLast();
        exigir(fase.getTipo() == TipoFase.ELIMINATORIA && fase.isDisputaTerceiroLugar(),
                "Campeonato não possui disputa de terceiro lugar.");
        Participante terceiro = eliminatorio.terceiroColocado(fase, campeonato.getParticipantesDesistentes());
        exigir(terceiro != null, "Não há terceiro colocado elegível.");
        Long participanteId = terceiro.getId();
        return mapper.toResponse(campeonato).participantes().stream()
                .filter(p -> p.id().equals(participanteId)).findFirst().orElseThrow();
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

    private CampeonatoResponse salvarNotificando(Campeonato campeonato,MotivoAtualizacaoCampeonato motivo) {
        Campeonato salvo=repository.saveAndFlush(campeonato);
        eventos.registrar(salvo.getId(),motivo);
        return mapper.toResponse(salvo);
    }

    private Classificacao ultima(Campeonato campeonato) {
        return campeonato.getClassificacaoPorRodada().stream()
                .max(Comparator.comparingInt(Classificacao::getRodada)).orElse(null);
    }

    private Campeonato buscarEntidade(Long id) {
        return repository.findById(id).orElseThrow(() -> naoEncontrado("Campeonato", id));
    }

    private Campeonato buscarParaAtualizar(Long id) {
        return repository.buscarParaAtualizar(id).orElseThrow(() -> naoEncontrado("Campeonato", id));
    }

    private ResponseStatusException naoEncontrado(String recurso, Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " não encontrado: " + id);
    }

    private void exigir(boolean condicao, String mensagem) {
        if (!condicao) throw new RegraCampeonatoException(mensagem);
    }
}
