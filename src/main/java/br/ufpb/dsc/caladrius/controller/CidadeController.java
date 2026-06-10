package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.enums.TipoCidade;
import br.ufpb.dsc.caladrius.dto.CidadeForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.service.CidadeService;
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
 * CRUD de cidades via HTMX + Thymeleaf (mesmo padrão de {@link VeiculoController}).
 */
@Controller
@RequestMapping("/cidades")
public class CidadeController {

    private static final int TAMANHO_PAGINA = 10;
    private static final String HEADER_HTMX = "HX-Request";

    private final CidadeService cidadeService;

    public CidadeController(CidadeService cidadeService) {
        this.cidadeService = cidadeService;
    }

    @ModelAttribute
    public void opcoes(Model model) {
        model.addAttribute("tiposCidade", TipoCidade.values());
    }

    @GetMapping
    public String listar(@RequestParam(name = "busca", required = false, defaultValue = "") String busca,
                         @RequestParam(name = "pagina", defaultValue = "0") int pagina,
                         @RequestHeader(value = HEADER_HTMX, required = false) String htmx,
                         Model model) {
        carregarPagina(busca, pagina, model);
        if (htmx != null) {
            return "cidades/fragments/tabela :: tabela";
        }
        return "cidades/lista";
    }

    @GetMapping("/fragmento-tabela")
    public String fragmentoTabela(@RequestParam(name = "busca", required = false, defaultValue = "") String busca,
                                 @RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                 Model model) {
        carregarPagina(busca, pagina, model);
        return "cidades/fragments/tabela :: tabela";
    }

    @GetMapping("/nova")
    public String novoForm(Model model) {
        model.addAttribute("form", new CidadeForm(null, null, null));
        model.addAttribute("cidade", null);
        return "cidades/fragments/form :: modal";
    }

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable UUID id, Model model) {
        Cidade cidade = cidadeService.buscarPorId(id);
        model.addAttribute("form", new CidadeForm(cidade.getNome(), cidade.getUf(), cidade.getTipo()));
        model.addAttribute("cidade", cidade);
        return "cidades/fragments/form :: modal";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("form") CidadeForm form,
                       BindingResult bindingResult,
                       Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("cidade", null);
            return "cidades/fragments/form :: modal";
        }
        Cidade cidade = cidadeService.criar(form);
        model.addAttribute("cidade", cidade);
        return "cidades/fragments/linha :: linha";
    }

    @PutMapping("/{id}")
    public String atualizar(@PathVariable UUID id,
                          @Valid @ModelAttribute("form") CidadeForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("cidade", cidadeService.buscarPorId(id));
            return "cidades/fragments/form :: modal";
        }
        Cidade cidade = cidadeService.atualizar(id, form);
        model.addAttribute("cidade", cidade);
        return "cidades/fragments/linha :: linha";
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        try {
            cidadeService.excluir(id);
            return ResponseEntity.ok().build();
        } catch (RecursoNaoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private void carregarPagina(String busca, int pagina, Model model) {
        PageRequest pageRequest = PageRequest.of(pagina, TAMANHO_PAGINA, Sort.by("nome").ascending());
        Page<Cidade> cidades = cidadeService.buscar(busca, pageRequest);
        model.addAttribute("cidades", cidades);
        model.addAttribute("busca", busca);
        model.addAttribute("paginaAtual", pagina);
        model.addAttribute("titulo", "Cidades");
    }
}
