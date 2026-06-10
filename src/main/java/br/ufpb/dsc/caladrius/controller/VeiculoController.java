package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.Veiculo;
import br.ufpb.dsc.caladrius.domain.enums.StatusVeiculo;
import br.ufpb.dsc.caladrius.domain.enums.TipoVeiculo;
import br.ufpb.dsc.caladrius.dto.VeiculoForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.service.VeiculoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * CRUD de veículos via HTMX + Thymeleaf.
 *
 * <p>Segue o mesmo padrão do boilerplate: a listagem responde a página completa
 * em requisições normais e apenas o fragmento da tabela em requisições HTMX
 * (header {@code HX-Request}). Criação/edição/exclusão devolvem fragmentos.
 */
@Controller
@RequestMapping("/veiculos")
public class VeiculoController {

    private static final int TAMANHO_PAGINA = 10;
    private static final String HEADER_HTMX = "HX-Request";

    private final VeiculoService veiculoService;

    public VeiculoController(VeiculoService veiculoService) {
        this.veiculoService = veiculoService;
    }

    /** Disponibiliza as opções de enum para os selects do formulário. */
    @ModelAttribute
    public void opcoes(Model model) {
        model.addAttribute("tiposVeiculo", TipoVeiculo.values());
        model.addAttribute("statusesVeiculo", StatusVeiculo.values());
    }

    @GetMapping
    public String listar(@RequestParam(name = "busca", required = false, defaultValue = "") String busca,
                         @RequestParam(name = "pagina", defaultValue = "0") int pagina,
                         @RequestHeader(value = HEADER_HTMX, required = false) String htmx,
                         Model model) {
        carregarPagina(busca, pagina, model);
        if (htmx != null) {
            return "veiculos/fragments/tabela :: tabela";
        }
        return "veiculos/lista";
    }

    @GetMapping("/fragmento-tabela")
    public String fragmentoTabela(@RequestParam(name = "busca", required = false, defaultValue = "") String busca,
                                 @RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                 Model model) {
        carregarPagina(busca, pagina, model);
        return "veiculos/fragments/tabela :: tabela";
    }

    @GetMapping("/novo")
    public String novoForm(Model model) {
        model.addAttribute("form", new VeiculoForm(null, null, null, null, null, null, false, null));
        model.addAttribute("veiculo", null);
        return "veiculos/fragments/form :: modal";
    }

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable UUID id, Model model) {
        Veiculo veiculo = veiculoService.buscarPorId(id);
        model.addAttribute("form", paraForm(veiculo));
        model.addAttribute("veiculo", veiculo);
        return "veiculos/fragments/form :: modal";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("form") VeiculoForm form,
                       BindingResult bindingResult,
                       Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("veiculo", null);
            return "veiculos/fragments/form :: modal";
        }
        try {
            Veiculo veiculo = veiculoService.criar(form);
            model.addAttribute("veiculo", veiculo);
            return "veiculos/fragments/linha :: linha";
        } catch (RegraNegocioException e) {
            bindingResult.reject("veiculo.regra", e.getMessage());
            model.addAttribute("veiculo", null);
            return "veiculos/fragments/form :: modal";
        }
    }

    @PutMapping("/{id}")
    public String atualizar(@PathVariable UUID id,
                          @Valid @ModelAttribute("form") VeiculoForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("veiculo", veiculoService.buscarPorId(id));
            return "veiculos/fragments/form :: modal";
        }
        try {
            Veiculo veiculo = veiculoService.atualizar(id, form);
            model.addAttribute("veiculo", veiculo);
            return "veiculos/fragments/linha :: linha";
        } catch (RegraNegocioException e) {
            bindingResult.reject("veiculo.regra", e.getMessage());
            model.addAttribute("veiculo", veiculoService.buscarPorId(id));
            return "veiculos/fragments/form :: modal";
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        try {
            veiculoService.excluir(id);
            return ResponseEntity.ok().build();
        } catch (RecursoNaoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ===================== Helpers =====================

    private void carregarPagina(String busca, int pagina, Model model) {
        PageRequest pageRequest = PageRequest.of(pagina, TAMANHO_PAGINA, Sort.by("placa").ascending());
        Page<Veiculo> veiculos = veiculoService.buscar(busca, pageRequest);
        model.addAttribute("veiculos", veiculos);
        model.addAttribute("busca", busca);
        model.addAttribute("paginaAtual", pagina);
        model.addAttribute("titulo", "Veículos");
    }

    private VeiculoForm paraForm(Veiculo v) {
        return new VeiculoForm(v.getPlaca(), v.getMarca(), v.getModelo(), v.getAno(),
                v.getTipo(), v.getCapacidade(), v.isPossuiAcessibilidade(), v.getStatus());
    }
}
