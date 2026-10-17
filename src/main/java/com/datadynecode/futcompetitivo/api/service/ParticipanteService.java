package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.ParticipanteRequest;
import com.datadynecode.futcompetitivo.api.dto.ParticipanteResponse;
import com.datadynecode.futcompetitivo.api.entity.Participante;
import com.datadynecode.futcompetitivo.api.mapper.ParticipanteMapper;
import com.datadynecode.futcompetitivo.api.repository.EquipeRepository;
import com.datadynecode.futcompetitivo.api.repository.JogadorRepository;
import com.datadynecode.futcompetitivo.api.repository.ParticipanteRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipanteService {

    private final ParticipanteRepository repository;
    private final JogadorRepository jogadorRepository;
    private final EquipeRepository equipeRepository;
    private final ParticipanteMapper mapper;

    public List<ParticipanteResponse> listar() {
        return repository.findAllByAtivoTrueOrderByIdAsc().stream()
                .map(mapper::toResponse).toList();
    }

    public ParticipanteResponse buscar(Long id) {
        return mapper.toResponse(buscarAtivo(id));
    }

    @Transactional
    public ParticipanteResponse criar(ParticipanteRequest request) {
        Participante participante = new Participante();
        definirRelacionamentos(participante, request);
        return mapper.toResponse(repository.save(participante));
    }

    @Transactional
    public ParticipanteResponse atualizar(Long id, ParticipanteRequest request) {
        Participante participante = buscarAtivo(id);
        definirRelacionamentos(participante, request);
        return mapper.toResponse(repository.save(participante));
    }

    private Participante buscarAtivo(Long id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> naoEncontrado("Participante", id));
    }

    private void definirRelacionamentos(Participante participante, ParticipanteRequest request) {
        var jogador = jogadorRepository.findByIdAndAtivoTrue(request.jogadorId())
                .orElseThrow(() -> naoEncontrado("Jogador", request.jogadorId()));
        var equipe = equipeRepository.findByIdAndAtivoTrue(request.equipeId())
                .orElseThrow(() -> naoEncontrado("Equipe", request.equipeId()));
        participante.setJogador(jogador);
        participante.setEquipe(equipe);
    }

    private ResponseStatusException naoEncontrado(String recurso, Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                recurso + " inexistente ou inativo: " + id);
    }
}
