package com.datadynecode.futcompetitivo.api.service;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import com.datadynecode.futcompetitivo.api.exception.UsuarioNaoEncontradoException;
import com.datadynecode.futcompetitivo.api.dto.UsuarioPageResponse;

import java.nio.charset.StandardCharsets;

import com.datadynecode.futcompetitivo.api.dto.UsuarioRequest;
import com.datadynecode.futcompetitivo.api.dto.AlterarSenhaRequest;
import com.datadynecode.futcompetitivo.api.exception.UsuarioAutenticadoNaoEncontradoException;
import com.datadynecode.futcompetitivo.api.dto.UsuarioResponse;
import com.datadynecode.futcompetitivo.api.exception.CadastroInvalidoException;
import com.datadynecode.futcompetitivo.api.exception.EmailJaCadastradoException;
import com.datadynecode.futcompetitivo.api.mapper.UsuarioMapper;
import com.datadynecode.futcompetitivo.api.entity.Role;
import com.datadynecode.futcompetitivo.api.entity.Usuario;
import com.datadynecode.futcompetitivo.api.repository.RoleRepository;
import com.datadynecode.futcompetitivo.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    @Transactional(readOnly = true)
    public UsuarioPageResponse listar(int page, int size) {
        var usuarios = usuarioRepository.findAll(PageRequest.of(page, size,
            Sort.by("nome", "id"))).map(UsuarioMapper::toAdministrativoResponse);
        return new UsuarioPageResponse(usuarios.getContent(), usuarios.getNumber(),
            usuarios.getSize(), usuarios.getTotalElements(), usuarios.getTotalPages());
    }
    @Transactional
    public void inativar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(UsuarioNaoEncontradoException::new);
        usuario.setAtivo(false);
        usuarioRepository.saveAndFlush(usuario);
    }

    @Transactional
    public void alterarSenha(AlterarSenhaRequest request, String emailAutenticado) {
        Usuario usuario = usuarioRepository.findByEmail(emailAutenticado)
            .orElseThrow(UsuarioAutenticadoNaoEncontradoException::new);
        validarTamanhoSenha(request.senhaAtual());
        validarTamanhoSenha(request.novaSenha());
        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenha())) {
            throw new CadastroInvalidoException("Senha atual incorreta");
        }
        usuario.setSenha(passwordEncoder.encode(request.novaSenha()));
        usuarioRepository.saveAndFlush(usuario);
    }
    @Transactional
    public void ativar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(UsuarioNaoEncontradoException::new);
        usuario.setAtivo(true);
        usuarioRepository.saveAndFlush(usuario);
    }

    private void validarTamanhoSenha(String senha) {
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new CadastroInvalidoException("Senha deve ter no maximo 72 bytes em UTF-8");
        }
    }

    @Transactional
    public UsuarioResponse cadastrar(UsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new EmailJaCadastradoException();
        }
        validarTamanhoSenha(request.senha());
        Role role = roleRepository.findByAuthority("ROLE_USER")
            .orElseThrow(() -> new IllegalStateException("ROLE_USER nao cadastrada no banco"));
        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setSobrenome(request.sobrenome());
        usuario.setEmail(request.email());
        usuario.setSenha(passwordEncoder.encode(request.senha()));
        usuario.getRoles().adicionar(role);
        return UsuarioMapper.toResponse(usuarioRepository.saveAndFlush(usuario));
    }
}
