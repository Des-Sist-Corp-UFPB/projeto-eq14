package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.enums.MetodoVerificacao;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.service.RecuperacaoSenhaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telas públicas de recuperação de senha (SPEC-ACE-03): "esqueci a senha" (escolha do
 * método) e a redefinição com o código. Respostas <strong>genéricas</strong>
 * (anti-enumeração — RN-REC-02).
 */
@Controller
public class RecuperacaoSenhaController {

    private final RecuperacaoSenhaService recuperacaoSenhaService;

    public RecuperacaoSenhaController(RecuperacaoSenhaService recuperacaoSenhaService) {
        this.recuperacaoSenhaService = recuperacaoSenhaService;
    }

    /** Tela onde a pessoa escolhe o método (E-mail/Telefone) e informa o valor. */
    @GetMapping("/esqueci-senha")
    public String form(Model model) {
        if (!model.containsAttribute("metodo")) {
            model.addAttribute("metodo", MetodoVerificacao.TELEFONE);
        }
        return "auth/esqueci-senha";
    }

    /** Dispara o código de reset e leva para a tela de redefinição (resposta genérica). */
    @PostMapping("/esqueci-senha")
    public String solicitar(@RequestParam("metodo") MetodoVerificacao metodo,
                            @RequestParam("valor") String valor,
                            HttpServletRequest request, RedirectAttributes redirect) {
        recuperacaoSenhaService.solicitarReset(metodo, valor, request.getRemoteAddr());
        redirect.addFlashAttribute("metodo", metodo);
        redirect.addFlashAttribute("valor", valor);
        redirect.addFlashAttribute("info", "Se houver uma conta com esses dados, enviamos um código.");
        return "redirect:/redefinir-senha";
    }

    /** Tela com código + nova senha (o método/valor vêm da etapa anterior ou são reinformados). */
    @GetMapping("/redefinir-senha")
    public String redefinirForm(Model model) {
        if (!model.containsAttribute("metodo")) {
            model.addAttribute("metodo", MetodoVerificacao.TELEFONE);
        }
        return "auth/redefinir-senha";
    }

    /** Aplica a nova senha após validar o código. */
    @PostMapping("/redefinir-senha")
    public String redefinir(@RequestParam("metodo") MetodoVerificacao metodo,
                            @RequestParam("valor") String valor,
                            @RequestParam("codigo") String codigo,
                            @RequestParam("novaSenha") String novaSenha,
                            @RequestParam("confirmarSenha") String confirmarSenha,
                            RedirectAttributes redirect) {
        try {
            if (novaSenha == null || !novaSenha.equals(confirmarSenha)) {
                throw new RegraNegocioException("As senhas não conferem.");
            }
            recuperacaoSenhaService.redefinir(metodo, valor, codigo, novaSenha);
            return "redirect:/login?senhaRedefinida";
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            redirect.addFlashAttribute("metodo", metodo);
            redirect.addFlashAttribute("valor", valor);
            return "redirect:/redefinir-senha";
        }
    }
}
