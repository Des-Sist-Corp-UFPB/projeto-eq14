package br.ufpb.dsc.caladrius.config;

import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Redireciona usuários com <strong>perfil incompleto</strong> para
 * {@code /conta/completar} até que informem os dados obrigatórios (telefone) —
 * SPEC-08 (FR-AUT-14).
 *
 * <p>Aplica-se a contas criadas por login social (Google), que nascem sem telefone.
 * Caminhos essenciais (a própria tela de completar, logout, recursos estáticos,
 * health check) ficam liberados para evitar laço de redirecionamento.
 *
 * <p>Não é um {@code @Component}: é instanciado e adicionado <em>dentro</em> da
 * cadeia de segurança ({@code SecurityConfig}), pois depende do
 * {@code SecurityContext} já populado pelos filtros de autenticação.
 */
public class PerfilIncompletoFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (deveRedirecionar(request)) {
            response.sendRedirect(request.getContextPath() + "/conta/completar");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean deveRedirecionar(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return false;
        }
        if (!usuario.isPerfilIncompleto()) {
            return false;
        }
        return !caminhoLiberado(request);
    }

    /** Caminhos que NÃO devem ser interceptados (senão haveria laço de redirect). */
    private boolean caminhoLiberado(HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        return uri.startsWith("/conta/completar")
                || uri.startsWith("/logout")
                || uri.startsWith("/ping")
                || uri.startsWith("/actuator")
                || uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/webjars/")
                || uri.equals("/favicon.ico")
                || uri.equals("/error");
    }
}
