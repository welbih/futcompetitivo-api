package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.Organizacao;
import com.datadynecode.futcompetitivo.api.mapper.OrganizacaoMapper;
import com.datadynecode.futcompetitivo.api.repository.OrganizacaoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizacaoService {
    private final OrganizacaoRepository repository;
    private final OrganizacaoMapper mapper;

    public List<OrganizacaoResponse> listar() {
        return repository.findAll(Sort.by("id")).stream().map(mapper::toResponse).toList();
    }

    public OrganizacaoResponse buscar(Long id) { return mapper.toResponse(buscarEntidade(id)); }

    @Transactional
    public OrganizacaoResponse criar(OrganizacaoRequest request) {
        Organizacao organizacao = new Organizacao();
        mapper.atualizar(organizacao, request);
        return mapper.toResponse(repository.save(organizacao));
    }

    @Transactional
    public OrganizacaoResponse atualizar(Long id, OrganizacaoRequest request) {
        Organizacao organizacao = buscarEntidade(id);
        mapper.atualizar(organizacao, request);
        return mapper.toResponse(repository.save(organizacao));
    }

    @Transactional
    public void desativar(Long id) {
        Organizacao organizacao = buscarEntidade(id);
        organizacao.setAtivo(false);
        repository.save(organizacao);
    }

    private Organizacao buscarEntidade(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Organização não encontrada: " + id));
    }
}
