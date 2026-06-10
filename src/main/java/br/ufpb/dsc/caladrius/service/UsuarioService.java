package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.dto.RegistroForm;
import br.ufpb.dsc.caladrius.dto.UsuarioForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.util.Documentos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lógica de negócio dos usuários: auto-cadastro público (passageiro) e gestão
 * completa pelo gerente.
 *
 * <p>Centraliza as regras transversais: normalização de telefone/CPF, validação
 * de CPF, unicidade (telefone/e-mail/CPF entre ativos), hashing da senha (BCrypt)
 * e soft-delete.
 */
@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ===================== Consultas =====================

    /** Lista/busca usuários ativos paginados. */
    public Page<Usuario> buscar(String busca, Pageable pageable) {
        if (!StringUtils.hasText(busca)) {
            return usuarioRepository.findByRemovidoEmIsNull(pageable);
        }
        return usuarioRepository.buscar(busca.trim(), pageable);
    }

    /** Busca um usuário ativo pelo id ou lança {@link RecursoNaoEncontradoException}. */
    public Usuario buscarPorId(UUID id) {
        return usuarioRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
    }

    /** Lista usuários ativos que possuem o papel informado (ex.: motoristas). */
    public List<Usuario> listarPorPapel(Papel papel) {
        return usuarioRepository.listarPorPapel(papel);
    }

    // ===================== Auto-cadastro (passageiro) =====================

    /**
     * Cadastra um novo passageiro a partir do formulário público de registro.
     *
     * @param form dados do cadastro (já validados quanto ao formato pelo Bean Validation)
     * @return o usuário criado
     */
    @Transactional
    public Usuario registrarPassageiro(RegistroForm form) {
        String telefone = Documentos.apenasDigitos(form.telefone());
        String cpf = normalizarCpf(form.cpf());
        String email = normalizarEmail(form.email());

        validarTelefone(telefone, null);
        validarCpf(cpf, null);
        validarEmail(email, null);

        Usuario usuario = new Usuario();
        usuario.setNomeCompleto(form.nomeCompleto().trim());
        usuario.setTelefone(telefone);
        usuario.setCpf(cpf);
        usuario.setEmail(email);
        usuario.setHashSenha(passwordEncoder.encode(form.senha()));
        usuario.setStatus(StatusUsuario.ATIVO);
        usuario.setPapeis(EnumSet.of(Papel.PASSAGEIRO));
        return usuarioRepository.save(usuario);
    }

    // ===================== Gestão (gerente) =====================

    /** Cria um usuário pelo gerente (senha obrigatória). */
    @Transactional
    public Usuario criar(UsuarioForm form) {
        if (!StringUtils.hasText(form.senha())) {
            throw new RegraNegocioException("A senha é obrigatória ao criar um usuário");
        }
        String telefone = Documentos.apenasDigitos(form.telefone());
        String cpf = normalizarCpf(form.cpf());
        String email = normalizarEmail(form.email());

        validarTelefone(telefone, null);
        validarCpf(cpf, null);
        validarEmail(email, null);

        Usuario usuario = new Usuario();
        usuario.setNomeCompleto(form.nomeCompleto().trim());
        usuario.setTelefone(telefone);
        usuario.setCpf(cpf);
        usuario.setEmail(email);
        usuario.setHashSenha(passwordEncoder.encode(form.senha()));
        usuario.setStatus(form.status() != null ? form.status() : StatusUsuario.ATIVO);
        usuario.setPapeis(papeisOuPadrao(form.papeis()));
        return usuarioRepository.save(usuario);
    }

    /** Atualiza um usuário existente (senha em branco mantém a atual). */
    @Transactional
    public Usuario atualizar(UUID id, UsuarioForm form) {
        Usuario usuario = buscarPorId(id);
        String telefone = Documentos.apenasDigitos(form.telefone());
        String cpf = normalizarCpf(form.cpf());
        String email = normalizarEmail(form.email());

        validarTelefone(telefone, usuario);
        validarCpf(cpf, usuario);
        validarEmail(email, usuario);

        usuario.setNomeCompleto(form.nomeCompleto().trim());
        usuario.setTelefone(telefone);
        usuario.setCpf(cpf);
        usuario.setEmail(email);
        if (StringUtils.hasText(form.senha())) {
            usuario.setHashSenha(passwordEncoder.encode(form.senha()));
        }
        if (form.status() != null) {
            usuario.setStatus(form.status());
        }
        usuario.setPapeis(papeisOuPadrao(form.papeis()));
        return usuarioRepository.save(usuario);
    }

    /** Exclusão lógica (soft-delete): preenche {@code removidoEm}. */
    @Transactional
    public void excluir(UUID id) {
        Usuario usuario = buscarPorId(id);
        usuario.setRemovidoEm(Instant.now());
        usuarioRepository.save(usuario);
    }

    // ===================== Helpers de validação =====================

    /**
     * Garante a unicidade de telefone entre ativos. Quando {@code atual} é
     * informado (edição), só valida se o telefone realmente mudou.
     */
    private void validarTelefone(String telefone, Usuario atual) {
        if (!StringUtils.hasText(telefone)) {
            throw new RegraNegocioException("O telefone é obrigatório");
        }
        boolean mudou = atual == null || !telefone.equals(atual.getTelefone());
        if (mudou && usuarioRepository.existsByTelefoneAndRemovidoEmIsNull(telefone)) {
            throw new RegraNegocioException("Telefone já cadastrado: " + telefone);
        }
    }

    private void validarCpf(String cpf, Usuario atual) {
        if (cpf == null) {
            return; // CPF é opcional
        }
        if (!Documentos.cpfValido(cpf)) {
            throw new RegraNegocioException("CPF inválido");
        }
        boolean mudou = atual == null || !cpf.equals(atual.getCpf());
        if (mudou && usuarioRepository.existsByCpfAndRemovidoEmIsNull(cpf)) {
            throw new RegraNegocioException("CPF já cadastrado");
        }
    }

    private void validarEmail(String email, Usuario atual) {
        if (email == null) {
            return; // e-mail é opcional
        }
        boolean mudou = atual == null || !email.equalsIgnoreCase(atual.getEmail());
        if (mudou && usuarioRepository.existsByEmailIgnoreCaseAndRemovidoEmIsNull(email)) {
            throw new RegraNegocioException("E-mail já cadastrado: " + email);
        }
    }

    private Set<Papel> papeisOuPadrao(Set<Papel> papeis) {
        if (papeis == null || papeis.isEmpty()) {
            return EnumSet.of(Papel.PASSAGEIRO);
        }
        return EnumSet.copyOf(papeis);
    }

    private String normalizarCpf(String cpf) {
        String digitos = Documentos.apenasDigitos(cpf);
        return digitos.isEmpty() ? null : digitos;
    }

    private String normalizarEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
