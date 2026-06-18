package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.enums.StatusViagem;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.ViagemService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * Visão do motorista (SPEC-06, item 6): vê as suas viagens e altera o status das
 * próprias (PLANEJADA→…→CONCLUIDA|CANCELADA).
 */
@Controller
@RequestMapping("/minhas-viagens")
public class MotoristaViagemController {

    private final ViagemService viagemService;

    public MotoristaViagemController(ViagemService viagemService) {
        this.viagemService = viagemService;
    }

    @GetMapping
    public String minhas(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("viagens", viagemService.listarDoMotorista(usuario.getId()));
        model.addAttribute("titulo", "Minhas viagens");
        return "motorista/minhas-viagens";
    }

    @PostMapping("/{id}/status")
    public String alterarStatus(@PathVariable UUID id,
                                @RequestParam("status") StatusViagem status,
                                @AuthenticationPrincipal UsuarioAutenticado usuario,
                                RedirectAttributes redirect) {
        try {
            viagemService.alterarStatus(id, status, usuario.getId(), false);
            redirect.addFlashAttribute("sucesso", "Status atualizado.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/minhas-viagens";
    }
}
