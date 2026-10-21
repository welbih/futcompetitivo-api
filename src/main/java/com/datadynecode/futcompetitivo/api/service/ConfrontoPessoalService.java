package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraConfrontoPessoalException;
import com.datadynecode.futcompetitivo.api.mapper.ConfrontoPessoalMapper;
import com.datadynecode.futcompetitivo.api.repository.ConfrontoPessoalRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfrontoPessoalService {
    private final ConfrontoPessoalRepository repository;
    private final ParticipanteService participantes;
    private final ConfrontoPessoalMapper mapper;

    public List<ConfrontoPessoalResponse> listar() {
        return repository.findAll(Sort.by("id")).stream().map(mapper::toResponse).toList();
    }

    public ConfrontoPessoalResponse buscar(Long id) {
        return mapper.toResponse(repository.findById(id).orElseThrow(() -> naoEncontrado("Confronto pessoal", id)));
    }

    @Transactional
    public ConfrontoPessoalResponse criar(ConfrontoPessoalRequest request) {
        exigir(request.participanteUm() != null, "participanteUm é obrigatório.");
        exigir(request.participanteDois() != null, "participanteDois é obrigatório.");
        exigir(request.tipo() != null, "tipo é obrigatório.");
        validarParticipante(request.participanteUm());
        validarParticipante(request.participanteDois());
        exigir(!Objects.equals(request.participanteUm().jogadorId(), request.participanteDois().jogadorId()), "Informe dois jogadores diferentes.");
        ConfrontoPessoal confronto = new ConfrontoPessoal();
        confronto.setParticipanteUm(participantes.criarEntidade(request.participanteUm()));
        confronto.setParticipanteDois(participantes.criarEntidade(request.participanteDois()));
        confronto.setTipo(request.tipo());
        confronto.setEncerrarAntecipadamente(Boolean.TRUE.equals(request.encerrarAntecipadamente()));
        for (int numero = 1; numero <= request.tipo().getQuantidadePartidas(); numero++) {
            PartidaPessoal partida = new PartidaPessoal();
            partida.setConfronto(confronto);
            partida.setNumero(numero);
            partida.setMandante(numero % 2 == 1 ? confronto.getParticipanteUm() : confronto.getParticipanteDois());
            partida.setVisitante(numero % 2 == 1 ? confronto.getParticipanteDois() : confronto.getParticipanteUm());
            confronto.getPartidas().add(partida);
        }
        return mapper.toResponse(repository.saveAndFlush(confronto));
    }

    @Transactional
    public ConfrontoPessoalResponse registrarResultado(Long id, Long partidaId, ResultadoRequest request) {
        ConfrontoPessoal confronto = buscarParaAtualizar(id);
        PartidaPessoal partida = confronto.getPartidas().stream().filter(p -> p.getId().equals(partidaId))
                .findFirst().orElseThrow(() -> naoEncontrado("Partida do confronto pessoal", partidaId));
        exigir(partida.getStatus() != StatusPartidaPessoal.CANCELADA, "Não é possível registrar resultado em partida cancelada.");
        exigir(confronto.getPartidas().stream().filter(p -> p.getNumero() < partida.getNumero())
                .allMatch(p -> p.getStatus() == StatusPartidaPessoal.FINALIZADA), "Registre as partidas na ordem.");
        validarResultado(request);
        partida.setResultado(new Resultado(false, request.golsMandante(), request.golsVisitante(),
                request.penaltisMandante(), request.penaltisVisitante()));
        partida.setStatus(StatusPartidaPessoal.FINALIZADA);
        atualizarSituacao(confronto);
        return mapper.toResponse(repository.saveAndFlush(confronto));
    }

    @Transactional
    public ConfrontoPessoalResponse encerrar(Long id) {
        ConfrontoPessoal confronto = buscarParaAtualizar(id);
        exigir(atingiuMaioria(confronto), "Nenhum participante alcançou o número de vitórias necessário.");
        confronto.setEncerrarAntecipadamente(true);
        atualizarSituacao(confronto);
        return mapper.toResponse(repository.saveAndFlush(confronto));
    }

    private void atualizarSituacao(ConfrontoPessoal confronto) {
        boolean cancelar = confronto.isEncerrarAntecipadamente() && atingiuMaioria(confronto);
        confronto.getPartidas().stream().filter(p -> p.getStatus() != StatusPartidaPessoal.FINALIZADA).forEach(p -> {
            p.setResultado(null);
            p.setStatus(cancelar ? StatusPartidaPessoal.CANCELADA : StatusPartidaPessoal.AGENDADA);
        });
        confronto.setStatus(confronto.getPartidas().stream().anyMatch(p -> p.getStatus() == StatusPartidaPessoal.AGENDADA)
                ? StatusConfrontoPessoal.EM_ANDAMENTO : StatusConfrontoPessoal.FINALIZADO);
    }

    private boolean atingiuMaioria(ConfrontoPessoal confronto) {
        return Math.max(confronto.vitorias(confronto.getParticipanteUm()), confronto.vitorias(confronto.getParticipanteDois()))
                >= confronto.getTipo().getVitoriasNecessarias();
    }

    private void validarParticipante(ParticipanteRequest participante) {
        exigir(participante.jogadorId() != null, "jogador é obrigatório.");
        exigir(participante.equipeId() != null, "equipe é obrigatório.");
        exigir(participante.jogadorId() > 0, "jogadorId deve ser positivo.");
        exigir(participante.equipeId() > 0, "equipeId deve ser positivo.");
    }

    private void validarResultado(ResultadoRequest r) {
        exigir(r != null && r.golsMandante() != null && r.golsVisitante() != null
                && r.golsMandante() >= 0 && r.golsVisitante() >= 0, "Gols devem ser não negativos.");
        exigir(!Boolean.TRUE.equals(r.golDeOuro()), "Gol de ouro não é utilizado no confronto pessoal.");
        if (r.golsMandante().equals(r.golsVisitante())) {
            if (r.penaltisMandante() == null && r.penaltisVisitante() == null) return;
            exigir(r.penaltisMandante() != null && r.penaltisVisitante() != null
                    && r.penaltisMandante() >= 0 && r.penaltisVisitante() >= 0
                    && !r.penaltisMandante().equals(r.penaltisVisitante()),
                    "Partida empatada exige pênaltis válidos e um vencedor.");
        } else exigir(r.penaltisMandante() == null && r.penaltisVisitante() == null,
                "Pênaltis só são permitidos em partidas empatadas.");
    }

    private ConfrontoPessoal buscarParaAtualizar(Long id) {
        return repository.buscarParaAtualizar(id).orElseThrow(() -> naoEncontrado("Confronto pessoal", id));
    }

    private ResponseStatusException naoEncontrado(String entidade, Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, entidade + " não encontrado: " + id);
    }

    private void exigir(boolean condicao, String mensagem) {
        if (!condicao) throw new RegraConfrontoPessoalException(mensagem);
    }
}

