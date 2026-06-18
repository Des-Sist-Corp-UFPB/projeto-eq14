package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.dto.UsuarioForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * CRUD de usuários (gestão pelo gerente) via HTMX + Thymeleaf.
 *
 * <p>Permite definir os papéis (RBAC) e usa soft-delete na exclusão.
 */
@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private static final int TAMANHO_PAGINA = 10;
    private static final String HEADER_HTMX = "HX-Request";

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @ModelAttribute
    public void opcoes(Model model) {
        model.addAttribute("papeis", Papel.values());
        model.addAttribute("statusesUsuario", StatusUsuario.values());
    }

    @GetMapping
    public String listar(@RequestParam(name = "busca", required = false, defaultValue = "") String busca,
                         @RequestParam(name = "pagina", defaultValue = "0") int pagina,
                         @RequestHeader(value = HEADER_HTMX, required = false) String htmx,
                         Model model) {
        carregarPagina(busca, pagina, model);
        if (htmx != null) {
            return "usuarios/fragments/tabela :: tabela";
        }
        return "usuarios/lista";
    }

    @GetMapping("/fragmento-tabela")
    public String fragmentoTabela(@RequestParam(name = "busca", required = false, defaultValue = "") String busca,
                                 @RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                 Model model) {
        carregarPagina(busca, pagina, model);
        return "usuarios/fragments/tabela :: tabela";
    }

    @GetMapping("/novo")
    public String novoForm(Model model) {
        model.addAttribute("form", new UsuarioForm(null, null, null, null, null, null, null));
        model.addAttribute("usuario", null);
        return "usuarios/fragments/form :: modal";
    }

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable UUID id, Model model) {
        Usuario usuario = usuarioService.buscarPorId(id);
        model.addAttribute("form", paraForm(usuario));
        model.addAttribute("usuario", usuario);
        return "usuarios/fragments/form :: modal";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("form") UsuarioForm form,
                       BindingResult bindingResult,
                       Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("usuario", null);
            return "usuarios/fragments/form :: modal";
        }
        try {
            Usuario usuario = usuarioService.criar(form);
            model.addAttribute("usuario", usuario);
            return "usuarios/fragments/linha :: linha";
        } catch (RegraNegocioException e) {
            bindingResult.reject("usuario.regra", e.getMessage());
            model.addAttribute("usuario", null);
            return "usuarios/fragments/form :: modal";
        }
    }

    @PutMapping("/{id}")
    public String atualizar(@PathVariable UUID id,
                          @Valid @ModelAttribute("form") UsuarioForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("usuario", usuarioService.buscarPorId(id));
            return "usuarios/fragments/form :: modal";
        }
        try {
            Usuario usuario = usuarioService.atualizar(id, form);
            model.addAttribute("usuario", usuario);
            return "usuarios/fragments/linha :: linha";
        } catch (RegraNegocioException e) {
            bindingResult.reject("usuario.regra", e.getMessage());
            model.addAttribute("usuario", usuarioService.buscarPorId(id));
            return "usuarios/fragments/form :: modal";
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<String> excluir(@PathVariable UUID id,
                                          @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        try {
            UUID solicitanteId = autenticado != null ? autenticado.getId() : null;
            usuarioService.excluir(id, solicitanteId);
            return ResponseEntity.ok().build();
        } catch (RecursoNaoEncontradoException e) {
            return ResponseEntity.notFound().build();
        } catch (RegraNegocioException e) {
            // DT-02: ex.: tentar excluir a si mesmo ou o último gerente ativo.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    private void carregarPagina(String busca, int pagina, Model model) {
        PageRequest pageRequest = PageRequest.of(pagina, TAMANHO_PAGINA, Sort.by("nomeCompleto").ascending());
        Page<Usuario> usuarios = usuarioService.buscar(busca, pageRequest);
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("busca", busca);
        model.addAttribute("paginaAtual", pagina);
        model.addAttribute("titulo", "Usuários");
    }

    /** Monta um {@link UsuarioForm} a partir da entidade (sem expor o hash da senha). */
    private UsuarioForm paraForm(Usuario u) {
        return new UsuarioForm(u.getNomeCompleto(), u.getTelefone(), u.getCpf(),
                u.getEmail(), null, u.getPapeis(), u.getStatus());
    }
}
