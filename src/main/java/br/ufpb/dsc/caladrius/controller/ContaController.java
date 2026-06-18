package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.UsuarioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Ações da própria conta do usuário autenticado (DT-02).
 *
 * <p>Disponível para qualquer papel (passageiro, motorista, gerente): qualquer
 * usuário pode <strong>solicitar a suspensão</strong> da própria conta, mas
 * <strong>nunca a exclusão</strong> — a remoção é responsabilidade de um cargo
 * superior. A trava do último gerente ativo é aplicada no serviço.
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
}
