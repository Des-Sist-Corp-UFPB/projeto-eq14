package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.service.SolicitacaoViagemService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Painel do gerente para avaliar as solicitações de transporte <strong>sob
 * demanda</strong> (SPEC-11): listar as pendentes, aprovar (alocando a uma
 * viagem) ou recusar (com motivo). O passageiro é notificado do resultado pelo
 * WhatsApp/in-app (no serviço).
 *
 * <p>Rota exclusiva do GERENTE (ver {@code SecurityConfig}: {@code /gestao/**}).
 * Form-posts de página inteira com CSRF (Thymeleaf) — sem exceção de CSRF.
 */
@Controller
@RequestMapping("/gestao/solicitacoes")
public class GestaoSolicitacaoController {

    private final SolicitacaoViagemService solicitacaoService;

    public GestaoSolicitacaoController(SolicitacaoViagemService solicitacaoService) {
        this.solicitacaoService = solicitacaoService;
    }

    @GetMapping
    public String painel(Model model) {
        model.addAttribute("titulo", "Solicitações sob demanda");
        model.addAttribute("solicitacoes", solicitacaoService.listarDemandaPendente());
        return "gestao/solicitacoes";
    }

    /** Fragmento com as viagens candidatas (mesmo destino+data) para alocar. */
    @GetMapping("/{id}/candidatas")
    public String candidatas(@PathVariable("id") UUID id, Model model) {
        model.addAttribute("solicitacaoId", id);
        model.addAttribute("viagens", solicitacaoService.viagensCandidatas(id));
        return "gestao/fragments/candidatas :: candidatas";
    }

    @PostMapping("/{id}/aprovar")
    public String aprovar(@PathVariable("id") UUID id,
                          @RequestParam("viagemId") UUID viagemId,
                          RedirectAttributes redirect) {
        try {
            solicitacaoService.aprovar(id, viagemId);
            redirect.addFlashAttribute("sucesso", "Solicitação aprovada e passageiro notificado.");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/gestao/solicitacoes";
    }

    @PostMapping("/{id}/recusar")
    public String recusar(@PathVariable("id") UUID id,
                          @RequestParam("motivo") String motivo,
                          RedirectAttributes redirect) {
        try {
            solicitacaoService.recusar(id, motivo);
            redirect.addFlashAttribute("sucesso", "Solicitação recusada e passageiro notificado.");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/gestao/solicitacoes";
    }
}
