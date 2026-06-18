package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.service.ConviteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Ativação de conta por convite (público): o convidado define a senha pelo token.
 */
@Controller
@RequestMapping("/ativar")
public class AtivacaoController {

    private final ConviteService conviteService;

    public AtivacaoController(ConviteService conviteService) {
        this.conviteService = conviteService;
    }

    @GetMapping
    public String form(@RequestParam(value = "token", required = false) String token, Model model) {
        model.addAttribute("token", token);
        return "auth/ativar";
    }

    @PostMapping
    public String ativar(@RequestParam("token") String token,
                         @RequestParam("senha") String senha,
                         RedirectAttributes redirect) {
        try {
            conviteService.ativar(token, senha);
            return "redirect:/login?ativado";
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            redirect.addAttribute("token", token);
            return "redirect:/ativar";
        }
    }
}
