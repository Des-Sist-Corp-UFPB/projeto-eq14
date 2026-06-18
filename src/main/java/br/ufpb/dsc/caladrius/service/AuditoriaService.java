package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.LogAuditoria;
import br.ufpb.dsc.caladrius.domain.enums.CategoriaAuditoria;
import br.ufpb.dsc.caladrius.repository.LogAuditoriaRepository;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

/**
 * Registra e consulta a trilha de auditoria (DT/feature #19).
 *
 * <p>Eventos de <strong>operação</strong> leem o usuário autenticado e o IP do
 * contexto da requisição; eventos de <strong>segurança</strong> (login/logout)
 * recebem os dados explicitamente dos listeners do Spring Security.
 */
@Service
@Transactional(readOnly = true)
public class AuditoriaService {

    private final LogAuditoriaRepository repository;

    public AuditoriaService(LogAuditoriaRepository repository) {
        this.repository = repository;
    }

    // ----------------------------------------------------------- registro

    /** Registro de segurança (login/logout/falha), com dados explícitos. */
    @Transactional
    public void registrarSeguranca(String acao, String resultado,
                                   UUID usuarioId, String usuarioNome, String ip) {
        LogAuditoria log = new LogAuditoria();
        log.setCategoria(CategoriaAuditoria.SEGURANCA);
        log.setAcao(acao);
        log.setResultado(resultado);
        log.setUsuarioId(usuarioId);
        log.setUsuarioNome(usuarioNome);
        log.setIp(ip);
        repository.save(log);
    }

    /**
     * Registro de operação de negócio (criar/editar/excluir). Lê o usuário
     * autenticado e o IP do contexto atual.
     */
    @Transactional
    public void registrarOperacao(String acao, String entidade, String entidadeId, String detalhe) {
        LogAuditoria log = new LogAuditoria();
        log.setCategoria(CategoriaAuditoria.OPERACAO);
        log.setAcao(acao);
        log.setResultado("SUCESSO");
        log.setEntidade(entidade);
        log.setEntidadeId(entidadeId);
        log.setDetalhe(detalhe);
        preencherAutorEContexto(log);
        repository.save(log);
    }

    /** Registro de evento do sistema (ex.: alteração de configuração). */
    @Transactional
    public void registrarSistema(String acao, String detalhe) {
        LogAuditoria log = new LogAuditoria();
        log.setCategoria(CategoriaAuditoria.SISTEMA);
        log.setAcao(acao);
        log.setResultado("SUCESSO");
        log.setDetalhe(detalhe);
        preencherAutorEContexto(log);
        repository.save(log);
    }

    // ----------------------------------------------------------- consultas

    /** Trilha completa (visão do SYSADMIN). */
    public Page<LogAuditoria> listarTudo(Pageable pageable) {
        return repository.findAllByOrderByInstanteDesc(pageable);
    }

    /** Histórico de operação de negócio (visão do GERENTE). */
    public Page<LogAuditoria> listarOperacao(Pageable pageable) {
        return repository.findByCategoriaInOrderByInstanteDesc(
                List.of(CategoriaAuditoria.OPERACAO), pageable);
    }

    // ----------------------------------------------------------- helpers

    private void preencherAutorEContexto(LogAuditoria log) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) {
            log.setUsuarioId(u.getId());
            log.setUsuarioNome(u.getNomeCompleto());
        }
        log.setIp(ipDaRequisicao());
    }

    /** IP da requisição atual, se houver contexto web. */
    public static String ipDaRequisicao() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
