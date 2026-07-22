package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.service.ConviteService;
import br.ufpb.dsc.caladrius.service.VerificacaoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telas públicas de verificação de contato (SPEC-12): confirmar o telefone por
 * código (OTP) e o e-mail por link mágico.
 */
@Controller
public class VerificacaoController {

    private final VerificacaoService verificacaoService;
    private final ConviteService conviteService;

    public VerificacaoController(VerificacaoService verificacaoService, ConviteService conviteService) {
        this.verificacaoService = verificacaoService;
        this.conviteService = conviteService;
    }

    /** Tela para digitar o código de verificação do telefone. */
    @GetMapping("/verificar-telefone")
    public String form(@RequestParam(value = "tel", required = false) String tel, Model model) {
        if (!model.containsAttribute("telefone")) {
            model.addAttribute("telefone", tel);
        }
        return "auth/verificar-telefone";
    }

    /** Confirma o telefone com o código; em sucesso a conta é ativada. */
    @PostMapping("/verificar-telefone")
    public String verificar(@RequestParam("telefone") String telefone,
                            @RequestParam("codigo") String codigo,
                            RedirectAttributes redirect) {
        try {
            verificacaoService.confirmarTelefonePorTelefone(telefone, codigo);
            return "redirect:/login?verificado";
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            redirect.addFlashAttribute("telefone", telefone);
            return "redirect:/verificar-telefone";
        }
    }

    /** Reenvia o código (respeita o cooldown do serviço). */
    @PostMapping("/verificar-telefone/reenviar")
    public String reenviar(@RequestParam("telefone") String telefone,
                           HttpServletRequest request, RedirectAttributes redirect) {
        try {
            verificacaoService.reenviarPorTelefone(telefone, request.getRemoteAddr());
            redirect.addFlashAttribute("info", "Enviamos um novo código para o seu WhatsApp.");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        redirect.addFlashAttribute("telefone", telefone);
        return "redirect:/verificar-telefone";
    }

    /** Consome o link mágico de verificação de e-mail. */
    @GetMapping("/verificar-email")
    public String verificarEmail(@RequestParam(value = "token", required = false) String token, Model model) {
        try {
            conviteService.verificarEmail(token);
            model.addAttribute("ok", true);
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            model.addAttribute("erro", e.getMessage());
        }
        return "auth/verificar-email";
    }
}
