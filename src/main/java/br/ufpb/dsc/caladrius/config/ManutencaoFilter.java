package br.ufpb.dsc.caladrius.config;

import br.ufpb.dsc.caladrius.service.ChaveFeature;
import br.ufpb.dsc.caladrius.service.FeatureFlagService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Modo de manutenção (SPEC-13, RN-FLG-03): com a flag
 * {@link ChaveFeature#MODO_MANUTENCAO} ligada, toda requisição de usuário comum
 * recebe a <strong>página de manutenção</strong> com HTTP <strong>503</strong>, e
 * só o <strong>SYSADMIN</strong> continua navegando — é ele quem desliga a flag e
 * põe o sistema de volta no ar.
 *
 * <p>Ficam de fora do bloqueio: {@code /login}, {@code /logout}, a própria página de
 * manutenção, os estáticos e — por contrato da disciplina (Art. XIII) — {@code /ping}
 * e {@code /actuator/health}, que devem continuar respondendo 200 para o monitoramento
 * externo não interpretar a manutenção como queda.
 *
 * <p>Como o {@link PerfilIncompletoFilter}, não é um {@code @Component}: é instanciado
 * dentro da cadeia de segurança ({@code SecurityConfig}), depois da autorização, pois
 * depende do {@code SecurityContext} já populado.
 */
public class ManutencaoFilter extends OncePerRequestFilter {

    /** Página pública renderizada no lugar do conteúdo bloqueado. */
    static final String PAGINA_MANUTENCAO = "/manutencao";

    private final FeatureFlagService featureFlags;

    public ManutencaoFilter(FeatureFlagService featureFlags) {
        this.featureFlags = featureFlags;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!deveBloquear(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        // Requisição HTMX: um 503 com a página inteira seria injetado dentro de um
        // fragmento — o header manda o htmx navegar para a página de manutenção.
        if (StringUtils.hasText(request.getHeader("HX-Request"))) {
            response.setHeader("HX-Redirect", request.getContextPath() + PAGINA_MANUTENCAO);
            return;
        }
        request.getRequestDispatcher(PAGINA_MANUTENCAO).forward(request, response);
    }

    private boolean deveBloquear(HttpServletRequest request) {
        // A leitura da flag é cacheada (RN-FLG-01) — não custa uma consulta por requisição.
        if (!featureFlags.ativo(ChaveFeature.MODO_MANUTENCAO)) {
            return false;
        }
        return !caminhoLiberado(request) && !ehSysadmin();
    }

    /** O SYSADMIN opera normalmente durante a manutenção (é quem religa o sistema). */
    private boolean ehSysadmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority autoridade : auth.getAuthorities()) {
            if ("ROLE_SYSADMIN".equals(autoridade.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** Caminhos que continuam respondendo mesmo em manutenção (RN-FLG-03). */
    private boolean caminhoLiberado(HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        return uri.equals(PAGINA_MANUTENCAO)
                || uri.startsWith("/login")
                || uri.startsWith("/logout")
                // Contrato público da disciplina (Art. XIII) — não pode cair na manutenção.
                || uri.startsWith("/ping")
                || uri.startsWith("/actuator")
                || uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/webjars/")
                || uri.equals("/favicon.ico")
                || uri.equals("/error");
    }
}
