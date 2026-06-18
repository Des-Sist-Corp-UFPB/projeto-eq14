package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.Viagem;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusViagem;
import br.ufpb.dsc.caladrius.dto.DesignacaoForm;
import br.ufpb.dsc.caladrius.dto.ViagemForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.CidadeService;
import br.ufpb.dsc.caladrius.service.UsuarioService;
import br.ufpb.dsc.caladrius.service.VeiculoService;
import br.ufpb.dsc.caladrius.service.ViagemService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Criação e listagem de viagens via HTMX + Thymeleaf.
 *
 * <p>O gerente autenticado é registrado como {@code criadoPor} da viagem. Os
 * selects do formulário são alimentados com veículos ativos, motoristas
 * (usuários com o papel {@code MOTORISTA}) e cidades.
 */
@Controller
@RequestMapping("/viagens")
public class ViagemController {

    private static final int TAMANHO_PAGINA = 10;
    private static final String HEADER_HTMX = "HX-Request";

    private final ViagemService viagemService;
    private final VeiculoService veiculoService;
    private final UsuarioService usuarioService;
    private final CidadeService cidadeService;

    public ViagemController(ViagemService viagemService,
                           VeiculoService veiculoService,
                           UsuarioService usuarioService,
                           CidadeService cidadeService) {
        this.viagemService = viagemService;
        this.veiculoService = veiculoService;
        this.usuarioService = usuarioService;
        this.cidadeService = cidadeService;
    }

    @GetMapping
    public String listar(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                         @RequestHeader(value = HEADER_HTMX, required = false) String htmx,
                         Model model) {
        carregarPagina(pagina, model);
        if (htmx != null) {
            return "viagens/fragments/tabela :: tabela";
        }
        return "viagens/lista";
    }

    @GetMapping("/nova")
    public String novoForm(Model model) {
        model.addAttribute("form", new ViagemForm(null, null, null, null, null, null));
        carregarOpcoes(model);
        return "viagens/fragments/form :: modal";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("form") ViagemForm form,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                       Model model) {
        if (bindingResult.hasErrors()) {
            carregarOpcoes(model);
            return "viagens/fragments/form :: modal";
        }
        try {
            Viagem viagem = viagemService.criar(form, usuarioLogado.getId());
            model.addAttribute("viagem", viagem);
            return "viagens/fragments/linha :: linha";
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            bindingResult.reject("viagem.regra", e.getMessage());
            carregarOpcoes(model);
            return "viagens/fragments/form :: modal";
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        try {
            viagemService.excluir(id);
            return ResponseEntity.ok().build();
        } catch (RecursoNaoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ===================== SPEC-06: painel semanal =====================

    @GetMapping("/semana")
    public String painelSemana(@RequestParam(name = "ref", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ref,
                               Model model) {
        model.addAttribute("painel", viagemService.painelSemana(ref));
        model.addAttribute("designacao", new DesignacaoForm(null, null, null, null));
        carregarOpcoes(model);
        model.addAttribute("titulo", "Painel da semana");
        return "viagens/semana";
    }

    @PostMapping("/designar")
    public String designar(@Valid @ModelAttribute("designacao") DesignacaoForm form,
                           BindingResult bindingResult,
                           @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                           RedirectAttributes redirect) {
        try {
            if (bindingResult.hasErrors()) {
                throw new RegraNegocioException("Selecione veículo e motorista para designar.");
            }
            viagemService.designar(form, usuarioLogado.getId());
            redirect.addFlashAttribute("sucesso", "Viagem designada.");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/viagens/semana?ref=" + form.dataViagem();
    }

    @PostMapping("/{id}/status")
    public String alterarStatus(@PathVariable UUID id,
                                @RequestParam("status") StatusViagem status,
                                @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                                RedirectAttributes redirect) {
        try {
            viagemService.alterarStatus(id, status, usuarioLogado.getId(), true);
            redirect.addFlashAttribute("sucesso", "Status atualizado.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/viagens";
    }

    // ===================== Helpers =====================

    private void carregarPagina(int pagina, Model model) {
        Page<Viagem> viagens = viagemService.listar(PageRequest.of(pagina, TAMANHO_PAGINA));
        model.addAttribute("viagens", viagens);
        model.addAttribute("paginaAtual", pagina);
        model.addAttribute("titulo", "Viagens");
    }

    /** Alimenta os selects do formulário (veículos, motoristas, cidades). */
    private void carregarOpcoes(Model model) {
        model.addAttribute("veiculosDisponiveis", veiculoService.listarDisponiveis());
        model.addAttribute("motoristas", usuarioService.listarPorPapel(Papel.MOTORISTA));
        model.addAttribute("cidades", cidadeService.listarTodas());
    }
}
