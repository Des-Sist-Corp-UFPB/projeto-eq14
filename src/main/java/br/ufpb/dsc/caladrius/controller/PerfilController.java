package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.Endereco;
import br.ufpb.dsc.caladrius.dto.EnderecoForm;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.EnderecoService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Meu perfil" do usuário autenticado — endereço estruturado (SPEC-07).
 */
@Controller
@RequestMapping("/perfil")
public class PerfilController {

    private final EnderecoService enderecoService;

    public PerfilController(EnderecoService enderecoService) {
        this.enderecoService = enderecoService;
    }

    @GetMapping
    public String form(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("titulo", "Meu perfil");
        model.addAttribute("municipios", enderecoService.listarMunicipios());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", paraForm(enderecoService.buscarPorUsuario(usuario.getId()).orElse(null)));
        }
        return "perfil/form";
    }

    @PostMapping
    public String salvar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                         @Valid @ModelAttribute("form") EnderecoForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("municipios", enderecoService.listarMunicipios());
            model.addAttribute("titulo", "Meu perfil");
            return "perfil/form";
        }
        enderecoService.salvar(usuario.getId(), form);
        redirect.addFlashAttribute("sucesso", "Endereço salvo com sucesso.");
        return "redirect:/perfil";
    }

    private EnderecoForm paraForm(Endereco e) {
        if (e == null) {
            return new EnderecoForm(null, null, null, null, null, null, null);
        }
        return new EnderecoForm(
                e.getMunicipio() != null ? e.getMunicipio().getId() : null,
                e.getBairro(), e.getLogradouro(), e.getNumero(),
                e.getComplemento(), e.getPontoReferencia(), e.getCep());
    }
}
