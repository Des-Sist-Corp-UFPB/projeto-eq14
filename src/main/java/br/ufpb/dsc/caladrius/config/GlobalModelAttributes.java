package br.ufpb.dsc.caladrius.config;

import br.ufpb.dsc.caladrius.domain.Notificacao;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.NotificacaoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Disponibiliza atributos globais a todos os templates Thymeleaf.
 *
 * <p>O Thymeleaf 3.1 removeu o acesso direto a {@code #request}/{@code #session}.
 * Expomos apenas o necessário via {@code @ModelAttribute} num {@code @ControllerAdvice},
 * aplicado antes de qualquer método de qualquer controller.
 *
 * <p>Publicamos a URI atual (destaque do menu) e as notificações in-app do
 * usuário logado (sino da barra superior).
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final NotificacaoService notificacaoService;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;

    public GlobalModelAttributes(NotificacaoService notificacaoService,
                                 ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository) {
        this.notificacaoService = notificacaoService;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    /**
     * {@code true} quando o login social com Google está configurado (há
     * credenciais e, portanto, um {@code ClientRegistrationRepository}). Usado na
     * tela de login para exibir/ocultar o botão "Continuar com Google" — SPEC-08.
     */
    @ModelAttribute("googleHabilitado")
    public boolean googleHabilitado() {
        return clientRegistrationRepository.getIfAvailable() != null;
    }

    /**
     * URI da requisição atual (ex.: "/veiculos", "/viagens/nova").
     */
    @ModelAttribute("requestURI")
    public String requestURI(HttpServletRequest request) {
        return request.getRequestURI();
    }

    /** Notificações não lidas do usuário logado (sino). Vazio se anônimo. */
    @ModelAttribute("notificacoes")
    public List<Notificacao> notificacoes(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (usuario == null) {
            return List.of();
        }
        return notificacaoService.naoLidas(usuario.getId());
    }
}
