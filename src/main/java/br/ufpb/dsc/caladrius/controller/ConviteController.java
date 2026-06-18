package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.ConviteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Convites por token (#20):
 * <ul>
 *   <li>{@code /admin/convites} (SYSADMIN) — convida um GERENTE.</li>
 *   <li>{@code /usuarios/convidar} (GERENTE) — convida um MOTORISTA.</li>
 * </ul>
 * O usuário nasce PENDENTE e ativa a conta pelo token (define a senha).
 */
@Controller
public class ConviteController {

    private final ConviteService conviteService;

    public ConviteController(ConviteService conviteService) {
        this.conviteService = conviteService;
    }

    // -------------------------------------------------- SYSADMIN → GERENTE

    @GetMapping("/admin/convites")
    public String formGerente(Model model) {
        prepararForm(model, "Convidar gestor", "Gerente", "/admin/convites");
        return "convites/form";
    }

    @PostMapping("/admin/convites")
    public String convidarGerente(@RequestParam("nome") String nome,
                                  @RequestParam("telefone") String telefone,
                                  @RequestParam(value = "email", required = false) String email,
                                  @AuthenticationPrincipal UsuarioAutenticado autenticado,
                                  RedirectAttributes redirect) {
        convidar(nome, telefone, email, Papel.GERENTE, autenticado, redirect);
        return "redirect:/admin/convites";
    }

    // -------------------------------------------------- GERENTE → MOTORISTA

    @GetMapping("/usuarios/convidar")
    public String formMotorista(Model model) {
        prepararForm(model, "Convidar motorista", "Motorista", "/usuarios/convidar");
        return "convites/form";
    }

    @PostMapping("/usuarios/convidar")
    public String convidarMotorista(@RequestParam("nome") String nome,
                                    @RequestParam("telefone") String telefone,
                                    @RequestParam(value = "email", required = false) String email,
                                    @AuthenticationPrincipal UsuarioAutenticado autenticado,
                                    RedirectAttributes redirect) {
        convidar(nome, telefone, email, Papel.MOTORISTA, autenticado, redirect);
        return "redirect:/usuarios/convidar";
    }

    // -------------------------------------------------------------- helpers

    private void convidar(String nome, String telefone, String email, Papel papel,
                          UsuarioAutenticado autenticado, RedirectAttributes redirect) {
        try {
            UUID criadoPorId = autenticado != null ? autenticado.getId() : null;
            String link = conviteService.convidar(nome, telefone, email, papel, criadoPorId);
            redirect.addFlashAttribute("sucesso",
                    "Convite gerado e enviado. Link de ativação (válido por 7 dias): " + link);
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
    }

    private void prepararForm(Model model, String titulo, String papelRotulo, String acao) {
        model.addAttribute("titulo", titulo);
        model.addAttribute("papelRotulo", papelRotulo);
        model.addAttribute("acao", acao);
    }
}
