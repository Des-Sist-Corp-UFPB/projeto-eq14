package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.Vinculo;
import br.ufpb.dsc.caladrius.multitenancia.ContextoTenantFilter;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.VinculoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Escolha de contexto ao entrar (SPEC-PLT-02 §5.3): por qual secretaria — e com qual
 * papel — a pessoa vai operar nesta sessão.
 *
 * <p>A tela lista <strong>somente</strong> os vínculos de quem está logado
 * (FR-MT-09): não existe, em nenhum ponto do sistema, uma listagem de secretarias
 * para o usuário final. Escolher o vínculo de outra pessoa responde 404 — não 403 —
 * para não confirmar que aquele vínculo existe.
 */
@Controller
public class ContextoController {

    private final VinculoService vinculoService;

    public ContextoController(VinculoService vinculoService) {
        this.vinculoService = vinculoService;
    }

    @GetMapping("/entrar/onde")
    public String escolher(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        List<Vinculo> vinculos = vinculoService.ativosDe(usuario.getId());
        model.addAttribute("vinculos", vinculos);
        return "contexto/escolher";
    }

    @PostMapping("/entrar/onde")
    public String entrar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                         @RequestParam("vinculo") UUID vinculoId,
                         HttpSession sessao) {
        // Lança RecursoNaoEncontradoException (404) se o vínculo não for desta pessoa.
        Vinculo vinculo = vinculoService.doUsuario(vinculoId, usuario.getId());
        sessao.setAttribute(ContextoTenantFilter.ATRIBUTO_ORGANIZACAO, vinculo.getOrganizacao().getId());
        sessao.setAttribute(ContextoTenantFilter.ATRIBUTO_RESOLVIDO, Boolean.TRUE);
        return "redirect:/";
    }
}
