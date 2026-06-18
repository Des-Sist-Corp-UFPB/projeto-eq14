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
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
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
        usuario = usuarioRepository.save(usuario);
        auditoriaService.registrarOperacao("USUARIO_CRIADO", "Usuario",
                usuario.getId().toString(), usuario.getNomeCompleto());
        return usuario;
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

        // DT-02: antes de aplicar status/papéis, garante que não se está removendo
        // o último gerente ativo (perda do papel GERENTE ou status != ATIVO).
        StatusUsuario novoStatus = form.status() != null ? form.status() : usuario.getStatus();
        Set<Papel> novosPapeis = papeisOuPadrao(form.papeis());
        boolean continuaGerenteAtivo =
                novoStatus == StatusUsuario.ATIVO && novosPapeis.contains(Papel.GERENTE);
        protegerUltimoGerente(usuario, continuaGerenteAtivo);

        usuario.setStatus(novoStatus);
        usuario.setPapeis(novosPapeis);
        usuario = usuarioRepository.save(usuario);
        auditoriaService.registrarOperacao("USUARIO_ATUALIZADO", "Usuario",
                usuario.getId().toString(), usuario.getNomeCompleto());
        return usuario;
    }

    /**
     * Exclusão lógica (soft-delete): preenche {@code removidoEm}.
     *
     * <p>DT-02: a exclusão é responsabilidade de um cargo superior — um usuário
     * não pode excluir a própria conta (deve solicitar suspensão). Também não é
     * possível excluir o último gerente ativo do sistema.
     *
     * @param id            usuário a remover
     * @param solicitanteId usuário autenticado que disparou a ação (null em fluxos internos)
     */
    @Transactional
    public void excluir(UUID id, UUID solicitanteId) {
        if (solicitanteId != null && solicitanteId.equals(id)) {
            throw new RegraNegocioException(
                    "Você não pode excluir a própria conta. Solicite a suspensão; "
                  + "a exclusão é responsabilidade de um cargo superior.");
        }
        Usuario usuario = buscarPorId(id);
        protegerUltimoGerente(usuario, false);
        usuario.setRemovidoEm(Instant.now());
        usuarioRepository.save(usuario);
        auditoriaService.registrarOperacao("USUARIO_EXCLUIDO", "Usuario",
                id.toString(), usuario.getNomeCompleto());
    }

    /**
     * Solicitação de suspensão da própria conta (DT-02): qualquer usuário pode
     * suspender a si mesmo, mas a trava do último gerente impede deixar o sistema
     * sem gestor ativo.
     */
    @Transactional
    public void solicitarSuspensao(UUID id) {
        Usuario usuario = buscarPorId(id);
        protegerUltimoGerente(usuario, false);
        usuario.setStatus(StatusUsuario.SUSPENSO);
        usuarioRepository.save(usuario);
        auditoriaService.registrarOperacao("CONTA_SUSPENSA", "Usuario",
                id.toString(), usuario.getNomeCompleto());
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

    // ===================== Regras de RBAC / DT-02 =====================

    /** Um usuário é "gerente ativo" se está ativo, não removido e tem o papel GERENTE. */
    private boolean ehGerenteAtivo(Usuario u) {
        return u.getRemovidoEm() == null
                && u.getStatus() == StatusUsuario.ATIVO
                && u.getPapeis().contains(Papel.GERENTE);
    }

    /**
     * Impede que uma operação remova o ÚLTIMO gerente ativo (DT-02). Bloqueia
     * quando o alvo é gerente ativo, deixaria de sê-lo (perda do papel, status
     * != ATIVO ou remoção) e não há outro gerente ativo no sistema.
     */
    private void protegerUltimoGerente(Usuario alvo, boolean continuaGerenteAtivo) {
        if (ehGerenteAtivo(alvo) && !continuaGerenteAtivo
                && usuarioRepository.contarPorStatusEPapel(StatusUsuario.ATIVO, Papel.GERENTE) <= 1) {
            throw new RegraNegocioException(
                    "Não é possível inativar, suspender ou remover o último gerente ativo. "
                  + "Habilite outro gerente antes.");
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
