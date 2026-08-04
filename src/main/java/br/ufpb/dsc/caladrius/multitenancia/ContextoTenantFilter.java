package br.ufpb.dsc.caladrius.multitenancia;

import br.ufpb.dsc.caladrius.domain.Vinculo;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.VinculoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Estabelece o {@link ContextoTenant} da requisição a partir do vínculo escolhido no
 * login (SPEC-PLT-02 §5.3) e, quando há mais de um vínculo, leva a pessoa à tela de
 * escolha antes de liberar o sistema.
 *
 * <p>Regras, em ordem:
 *
 * <ol>
 *   <li><strong>nenhum vínculo ⇒ tenant legado</strong> — é a situação de 100% das
 *       contas hoje, e nada muda para elas. A fase 1 desta spec é aditiva: não pode
 *       alterar o comportamento de produção;</li>
 *   <li><strong>um vínculo ⇒ assumido em silêncio</strong> — perguntar seria fricção
 *       sem escolha;</li>
 *   <li><strong>dois ou mais ⇒ {@code /entrar/onde}</strong> — a pessoa decide se entra
 *       como passageira em uma secretaria ou motorista em outra.</li>
 * </ol>
 *
 * <p>A decisão fica na <strong>sessão</strong>, inclusive quando o resultado é "não tem
 * vínculo": sem isso, cada requisição faria uma consulta a mais — e o pool de produção
 * tem cinco conexões (Art. XIV).
 *
 * <p>Não é um {@code @Component}: como o {@code PerfilIncompletoFilter}, é instanciado
 * dentro da cadeia de segurança, depois que o {@code SecurityContext} está populado.
 */
public class ContextoTenantFilter extends OncePerRequestFilter {

    /** Organização escolhida (UUID) — ausente quando se opera no tenant legado. */
    public static final String ATRIBUTO_ORGANIZACAO = "CALADRIUS_CONTEXTO_ORGANIZACAO";

    /** Marca que a escolha já foi resolvida nesta sessão (mesmo que sem vínculo). */
    public static final String ATRIBUTO_RESOLVIDO = "CALADRIUS_CONTEXTO_RESOLVIDO";

    private final VinculoService vinculoService;

    public ContextoTenantFilter(VinculoService vinculoService) {
        this.vinculoService = vinculoService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            UsuarioAutenticado usuario = autenticado();
            if (usuario == null) {
                filterChain.doFilter(request, response);
                return;
            }
            HttpSession sessao = request.getSession();
            if (!Boolean.TRUE.equals(sessao.getAttribute(ATRIBUTO_RESOLVIDO)) && !resolver(usuario, sessao)) {
                if (!caminhoLiberado(request)) {
                    response.sendRedirect(request.getContextPath() + "/entrar/onde");
                    return;
                }
            }
            ContextoTenant.definir((UUID) sessao.getAttribute(ATRIBUTO_ORGANIZACAO));
            filterChain.doFilter(request, response);
        } finally {
            // A thread volta ao pool do servidor: deixar contexto aqui é vazar tenant.
            ContextoTenant.limpar();
        }
    }

    /**
     * Tenta resolver o contexto sem perguntar nada.
     *
     * @return {@code true} se resolveu (sem vínculo ou com um só); {@code false} quando
     *         há mais de um e a pessoa precisa escolher
     */
    private boolean resolver(UsuarioAutenticado usuario, HttpSession sessao) {
        List<Vinculo> ativos = vinculoService.ativosDe(usuario.getId());
        if (ativos.size() > 1) {
            return false;
        }
        if (ativos.size() == 1) {
            sessao.setAttribute(ATRIBUTO_ORGANIZACAO, ativos.get(0).getOrganizacao().getId());
        }
        sessao.setAttribute(ATRIBUTO_RESOLVIDO, Boolean.TRUE);
        return true;
    }

    private UsuarioAutenticado autenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario;
        }
        return null;
    }

    /** Caminhos que não podem ser interceptados (senão haveria laço de redirect). */
    private boolean caminhoLiberado(HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        return uri.startsWith("/entrar/onde")
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
