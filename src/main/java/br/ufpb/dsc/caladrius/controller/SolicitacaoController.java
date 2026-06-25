package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.SolicitacaoViagemService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Visão do passageiro (SPEC-09): vê as linhas disponíveis, solicita transporte
 * numa data e acompanha as suas solicitações/viagens alocadas.
 *
 * <p>Exclusiva do papel {@code PASSAGEIRO} (ver {@code SecurityConfig}). Cada
 * passageiro só enxerga e altera as <strong>próprias</strong> solicitações —
 * nunca dados de outros passageiros.
 */
@Controller
@RequestMapping("/solicitacoes")
public class SolicitacaoController {

    private final SolicitacaoViagemService solicitacaoService;

    public SolicitacaoController(SolicitacaoViagemService solicitacaoService) {
        this.solicitacaoService = solicitacaoService;
    }

    @GetMapping
    public String pagina(@RequestParam(value = "data", required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                         @AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        LocalDate hoje = LocalDate.now();
        // A data selecionada no calendário; nunca no passado (mínimo = hoje).
        LocalDate dataSelecionada = (data != null && !data.isBefore(hoje)) ? data : hoje;

        model.addAttribute("titulo", "Transporte");
        model.addAttribute("hoje", hoje);
        model.addAttribute("dataSelecionada", dataSelecionada);
        model.addAttribute("diaSelecionado", DiaSemana.de(dataSelecionada.getDayOfWeek()));
        model.addAttribute("linhasDoDia", solicitacaoService.linhasQueOperamEm(dataSelecionada));
        model.addAttribute("grade", solicitacaoService.gradeSemanal());
        model.addAttribute("solicitacoes", solicitacaoService.listarDoPassageiro(usuario.getId()));
        return "passageiro/solicitacoes";
    }

    @PostMapping
    public String solicitar(@RequestParam("linhaId") UUID linhaId,
                            @RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                            @RequestParam(value = "observacao", required = false) String observacao,
                            @AuthenticationPrincipal UsuarioAutenticado usuario,
                            RedirectAttributes redirect) {
        try {
            solicitacaoService.solicitar(usuario.getId(), linhaId, data, observacao);
            redirect.addFlashAttribute("sucesso", "Solicitação registrada. Acompanhe em \"Minhas viagens\".");
            redirect.addFlashAttribute("aba", "minhas");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/solicitacoes";
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable("id") UUID id,
                           @AuthenticationPrincipal UsuarioAutenticado usuario,
                           RedirectAttributes redirect) {
        try {
            solicitacaoService.cancelar(id, usuario.getId());
            redirect.addFlashAttribute("sucesso", "Solicitação cancelada.");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        redirect.addFlashAttribute("aba", "minhas");
        return "redirect:/solicitacoes";
    }
}
