package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.EquipeRequest;
import com.datadynecode.futcompetitivo.api.dto.EquipeResponse;
import com.datadynecode.futcompetitivo.api.entity.Equipe;
import com.datadynecode.futcompetitivo.api.mapper.EquipeMapper;
import com.datadynecode.futcompetitivo.api.repository.EquipeRepository;
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
public class EquipeService {

    private final EquipeRepository repository;
    private final EquipeMapper mapper;

    public List<EquipeResponse> listar() {
        return listar(SituacaoCadastro.ATIVOS);
    }

    public List<EquipeResponse> listar(SituacaoCadastro situacao) {
        List<Equipe> registros = switch (situacao) {
            case ATIVOS -> repository.findAllByAtivoOrderByIdAsc(true);
            case INATIVOS -> repository.findAllByAtivoOrderByIdAsc(false);
            case AMBOS -> repository.findAllByOrderByIdAsc();
        };
        return registros.stream().map(mapper::toResponse).toList();
    }

    public EquipeResponse buscar(Long id) {
        return mapper.toResponse(buscarAtivo(id));
    }

    @Transactional
    public EquipeResponse criar(EquipeRequest request) {
        Equipe equipe = new Equipe();
        mapper.atualizar(equipe, request);
        return mapper.toResponse(repository.save(equipe));
    }

    @Transactional
    public EquipeResponse atualizar(Long id, EquipeRequest request) {
        Equipe equipe = buscarAtivo(id);
        mapper.atualizar(equipe, request);
        return mapper.toResponse(repository.save(equipe));
    }

    @Transactional
    public EquipeResponse ativar(Long id) {
        Equipe equipe = repository.findById(id)
                .orElseThrow(() -> naoEncontrado(id));
        equipe.setAtivo(true);
        return mapper.toResponse(repository.save(equipe));
    }

    @Transactional
    public void desativar(Long id) {
        Equipe equipe = repository.findById(id)
                .orElseThrow(() -> naoEncontrado(id));
        equipe.setAtivo(false);
        repository.save(equipe);
    }

    private Equipe buscarAtivo(Long id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> naoEncontrado(id));
    }

    private ResponseStatusException naoEncontrado(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Equipe nao encontrada: " + id);
    }
}
