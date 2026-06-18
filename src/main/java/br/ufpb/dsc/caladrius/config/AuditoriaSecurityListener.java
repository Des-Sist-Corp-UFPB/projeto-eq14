package br.ufpb.dsc.caladrius.config;

import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Registra na auditoria os eventos de <strong>acesso</strong> publicados pelo
 * Spring Security: login bem-sucedido e falha de login (#19, categoria SEGURANCA).
 * O logout é tratado por um handler em {@code SecurityConfig}.
 *
 * <p>As falhas de auditoria são engolidas (apenas logadas): auditar nunca pode
 * impedir a autenticação.
 */
@Component
public class AuditoriaSecurityListener {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaSecurityListener.class);

    private final AuditoriaService auditoriaService;

    public AuditoriaSecurityListener(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @EventListener
    public void onLoginSucesso(InteractiveAuthenticationSuccessEvent event) {
        try {
            String nome = event.getAuthentication().getName();
            java.util.UUID id = null;
            if (event.getAuthentication().getPrincipal() instanceof UsuarioAutenticado u) {
                id = u.getId();
                nome = u.getNomeCompleto();
            }
            auditoriaService.registrarSeguranca("LOGIN_SUCESSO", "SUCESSO",
                    id, nome, AuditoriaService.ipDaRequisicao());
        } catch (RuntimeException e) {
            log.warn("Falha ao auditar login bem-sucedido: {}", e.getMessage());
        }
    }

    @EventListener
    public void onLoginFalha(AbstractAuthenticationFailureEvent event) {
        try {
            String tentativa = event.getAuthentication() != null
                    ? event.getAuthentication().getName() : "(desconhecido)";
            auditoriaService.registrarSeguranca("LOGIN_FALHA", "FALHA",
                    null, tentativa, AuditoriaService.ipDaRequisicao());
        } catch (RuntimeException e) {
            log.warn("Falha ao auditar tentativa de login: {}", e.getMessage());
        }
    }
}
