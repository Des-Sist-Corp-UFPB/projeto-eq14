package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import br.ufpb.dsc.caladrius.dto.LinhaProgramadaForm;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.service.CidadeService;
import br.ufpb.dsc.caladrius.service.LinhaProgramadaService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * CRUD das linhas programadas (templates recorrentes — SPEC-06), papel GERENTE.
 */
@Controller
@RequestMapping("/linhas")
public class LinhaController {

    private final LinhaProgramadaService linhaService;
    private final CidadeService cidadeService;

    public LinhaController(LinhaProgramadaService linhaService, CidadeService cidadeService) {
        this.linhaService = linhaService;
        this.cidadeService = cidadeService;
    }

    @ModelAttribute
    public void opcoes(Model model) {
        model.addAttribute("cidades", cidadeService.listarTodas());
        model.addAttribute("diasSemana", DiaSemana.values());
        model.addAttribute("titulo", "Linhas programadas");
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("linhas", linhaService.listar());
        return "linhas/lista";
    }

    @GetMapping("/nova")
    public String novaForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new LinhaProgramadaForm(null, null, null, null, null, null, true));
        }
        model.addAttribute("linha", null);
        return "linhas/form";
    }

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable UUID id, Model model) {
        LinhaProgramada linha = linhaService.buscarPorId(id);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new LinhaProgramadaForm(
                    linha.getCidadeOrigem() != null ? linha.getCidadeOrigem().getId() : null,
                    linha.getCidadeDestino().getId(),
                    linha.getHorarioSaida(), linha.getHorarioChegada(), linha.getHorarioRetorno(),
                    linha.getDias(), linha.isAtiva()));
        }
        model.addAttribute("linha", linha);
        return "linhas/form";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("form") LinhaProgramadaForm form,
                        BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        return salvar(null, form, bindingResult, model, redirect);
    }

    @PostMapping("/{id}/editar")
    public String atualizar(@PathVariable UUID id,
                            @Valid @ModelAttribute("form") LinhaProgramadaForm form,
                            BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        return salvar(id, form, bindingResult, model, redirect);
    }

    @PostMapping("/{id}/alternar")
    public String alternar(@PathVariable UUID id, RedirectAttributes redirect) {
        linhaService.alternarAtiva(id);
        return "redirect:/linhas";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            linhaService.excluir(id);
            redirect.addFlashAttribute("sucesso", "Linha excluída.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/linhas";
    }

    private String salvar(UUID id, LinhaProgramadaForm form, BindingResult bindingResult,
                          Model model, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("linha", id == null ? null : linhaService.buscarPorId(id));
            return "linhas/form";
        }
        try {
            if (id == null) {
                linhaService.criar(form);
            } else {
                linhaService.atualizar(id, form);
            }
        } catch (RegraNegocioException e) {
            bindingResult.reject("linha.regra", e.getMessage());
            model.addAttribute("linha", id == null ? null : linhaService.buscarPorId(id));
            return "linhas/form";
        }
        redirect.addFlashAttribute("sucesso", "Linha salva.");
        return "redirect:/linhas";
    }
}
