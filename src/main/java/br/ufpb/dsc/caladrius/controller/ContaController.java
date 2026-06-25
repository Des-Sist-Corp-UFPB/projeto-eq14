package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Ações da própria conta do usuário autenticado.
 *
 * <p><strong>Suspensão (DT-02):</strong> qualquer usuário pode solicitar a
 * suspensão da própria conta, mas nunca a exclusão — a remoção é responsabilidade
 * de um cargo superior. A trava do último gerente ativo é aplicada no serviço.
 *
 * <p><strong>Completar perfil (SPEC-08):</strong> contas criadas por login social
 * (Google) nascem sem telefone; aqui o usuário o informa para liberar o acesso.
 */
@Controller
@RequestMapping("/conta")
public class ContaController {

    private final UsuarioService usuarioService;

    public ContaController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /** Suspende a conta do próprio usuário autenticado e encerra a sessão. */
    @PostMapping("/suspensao")
    public String solicitarSuspensao(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                                     RedirectAttributes redirect) {
        if (autenticado == null) {
            return "redirect:/login";
        }
        try {
            usuarioService.solicitarSuspensao(autenticado.getId());
            // Conta suspensa: apenas usuários ATIVO autenticam, então encerramos a sessão.
            return "redirect:/logout";
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erroConta", e.getMessage());
            return "redirect:/";
        }
    }

    /** Tela para completar o cadastro (telefone) de uma conta de login social. */
    @GetMapping("/completar")
    public String completarForm(@AuthenticationPrincipal UsuarioAutenticado autenticado, Model model) {
        if (autenticado == null) {
            return "redirect:/login";
        }
        if (!autenticado.isPerfilIncompleto()) {
            return "redirect:/";
        }
        model.addAttribute("nomeCompleto", autenticado.getNomeCompleto());
        return "conta/completar";
    }

    /** Grava o telefone, remove o marcador de perfil incompleto e atualiza a sessão. */
    @PostMapping("/completar")
    public String completar(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                            @RequestParam("telefone") String telefone,
                            HttpServletRequest request,
                            Model model) {
        if (autenticado == null) {
            return "redirect:/login";
        }
        try {
            Usuario atualizado = usuarioService.completarPerfil(autenticado.getId(), telefone);
            atualizarPrincipalNaSessao(atualizado, request);
            return "redirect:/";
        } catch (RegraNegocioException e) {
            model.addAttribute("nomeCompleto", autenticado.getNomeCompleto());
            model.addAttribute("telefone", telefone);
            model.addAttribute("erro", e.getMessage());
            return "conta/completar";
        }
    }

    /**
     * Substitui o principal autenticado por uma versão atualizada (telefone preenchido,
     * perfil completo) e persiste o contexto na sessão — sem isso, o filtro de perfil
     * incompleto continuaria redirecionando após a conclusão. Preserva o tipo de
     * autenticação (login social vs. senha) e os dados OIDC.
     */
    private void atualizarPrincipalNaSessao(Usuario usuario, HttpServletRequest request) {
        Authentication atual = SecurityContextHolder.getContext().getAuthentication();

        UsuarioAutenticado novoPrincipal = (atual.getPrincipal() instanceof UsuarioAutenticado antigo)
                ? new UsuarioAutenticado(usuario, antigo.getIdToken(), antigo.getUserInfo())
                : new UsuarioAutenticado(usuario);

        Authentication nova = (atual instanceof OAuth2AuthenticationToken oauthToken)
                ? new OAuth2AuthenticationToken(novoPrincipal, novoPrincipal.getAuthorities(),
                        oauthToken.getAuthorizedClientRegistrationId())
                : new UsernamePasswordAuthenticationToken(novoPrincipal, atual.getCredentials(),
                        novoPrincipal.getAuthorities());

        SecurityContext contexto = SecurityContextHolder.getContext();
        contexto.setAuthentication(nova);
        // Spring Security 6 exige salvar explicitamente o contexto na sessão.
        request.getSession().setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
    }
}
