package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.JogadorRequest;
import com.datadynecode.futcompetitivo.api.dto.JogadorResponse;
import com.datadynecode.futcompetitivo.api.entity.Jogador;
import com.datadynecode.futcompetitivo.api.mapper.JogadorMapper;
import com.datadynecode.futcompetitivo.api.repository.JogadorRepository;
import java.util.List;
import com.datadynecode.futcompetitivo.api.dto.SituacaoCadastro;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JogadorService {

    private final JogadorRepository repository;
    private final JogadorMapper mapper;

    public List<JogadorResponse> listar() {
        return listar(SituacaoCadastro.ATIVOS);
    }

    public List<JogadorResponse> listar(SituacaoCadastro situacao) {
        List<Jogador> registros = switch (situacao) {
            case ATIVOS -> repository.findAllByAtivoOrderByIdAsc(true);
            case INATIVOS -> repository.findAllByAtivoOrderByIdAsc(false);
            case AMBOS -> repository.findAllByOrderByIdAsc();
        };
        return registros.stream().map(mapper::toResponse).toList();
    }

    public JogadorResponse buscar(Long id) {
        return mapper.toResponse(buscarAtivo(id));
    }

    @Transactional
    public JogadorResponse criar(JogadorRequest request) {
        Jogador jogador = new Jogador();
        mapper.atualizar(jogador, request);
        return mapper.toResponse(repository.save(jogador));
    }

    @Transactional
    public JogadorResponse atualizar(Long id, JogadorRequest request) {
        Jogador jogador = buscarAtivo(id);
        mapper.atualizar(jogador, request);
        return mapper.toResponse(repository.save(jogador));
    }

    @Transactional
    public JogadorResponse ativar(Long id) {
        Jogador jogador = repository.findById(id)
                .orElseThrow(() -> naoEncontrado(id));
        jogador.setAtivo(true);
        return mapper.toResponse(repository.save(jogador));
    }

    @Transactional
    public void desativar(Long id) {
        Jogador jogador = repository.findById(id)
                .orElseThrow(() -> naoEncontrado(id));
        jogador.setAtivo(false);
        repository.save(jogador);
    }

    private Jogador buscarAtivo(Long id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> naoEncontrado(id));
    }

    private ResponseStatusException naoEncontrado(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Jogador nao encontrado: " + id);
    }
}
