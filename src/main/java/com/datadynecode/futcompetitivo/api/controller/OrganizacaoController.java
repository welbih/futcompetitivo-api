package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.dto.OrganizacaoRequest;
import com.datadynecode.futcompetitivo.api.dto.OrganizacaoResponse;
import com.datadynecode.futcompetitivo.api.service.OrganizacaoService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/organizacoes")
@RequiredArgsConstructor
public class OrganizacaoController {

    private final OrganizacaoService service;

    @GetMapping
    public List<OrganizacaoResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public OrganizacaoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrganizacaoResponse> criar(@Valid @RequestBody OrganizacaoRequest request) {
        OrganizacaoResponse response = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrganizacaoResponse atualizar(@PathVariable Long id,
            @Valid @RequestBody OrganizacaoRequest request) {
        return service.atualizar(id, request);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OrganizacaoResponse> criarComTrofeu(
            @Valid @RequestPart("dados") OrganizacaoRequest request,
            @RequestPart("trofeu") MultipartFile trofeu) {
        var response = service.criar(request, conteudo(trofeu), trofeu.getContentType());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OrganizacaoResponse atualizarComTrofeu(@PathVariable Long id,
            @Valid @RequestPart("dados") OrganizacaoRequest request,
            @RequestPart("trofeu") MultipartFile trofeu) {
        return service.atualizar(id, request, conteudo(trofeu), trofeu.getContentType());
    }

    @GetMapping("/{id}/trofeu")
    public ResponseEntity<byte[]> buscarTrofeu(@PathVariable Long id) {
        var imagem = service.buscarTrofeu(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(imagem.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(imagem.conteudo());
    }

    private byte[] conteudo(MultipartFile arquivo) {
        if (arquivo.getSize() > com.datadynecode.futcompetitivo.api.service.TrofeuImagemService.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "O troféu deve ter no máximo 2 MB.");
        }
        try {
            return arquivo.getBytes();
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o troféu.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        service.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
