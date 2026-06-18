package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.dto.RegistroForm;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.service.EnderecoService;
import br.ufpb.dsc.caladrius.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Autenticação e cadastro público.
 *
 * <p>O Spring Security processa o {@code POST /login} internamente; este
 * controller apenas serve a <strong>página</strong> de login (que aceita e-mail
 * OU telefone) e cuida do auto-cadastro de passageiros (incluindo o endereço
 * opcional — SPEC-07).
 */
@Controller
public class AuthController {

    private final UsuarioService usuarioService;
    private final EnderecoService enderecoService;

    public AuthController(UsuarioService usuarioService, EnderecoService enderecoService) {
        this.usuarioService = usuarioService;
        this.enderecoService = enderecoService;
    }

    /** Página de login (e-mail ou telefone + senha). */
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    /** Formulário público de cadastro de passageiro. */
    @GetMapping("/registrar")
    public String registroForm(Model model) {
        if (!model.containsAttribute("registroForm")) {
            model.addAttribute("registroForm",
                    new RegistroForm(null, null, null, null, null, null, null, null, null, null, null, null));
        }
        model.addAttribute("municipios", enderecoService.listarMunicipios());
        return "auth/registro";
    }

    /**
     * Processa o cadastro. Em caso de erro de formato (Bean Validation) ou de
     * regra de negócio (telefone/e-mail em uso, CPF inválido), reexibe o
     * formulário com as mensagens. Em sucesso, redireciona ao login.
     */
    @PostMapping("/registrar")
    public String registrar(@Valid @ModelAttribute("registroForm") RegistroForm form,
                            BindingResult bindingResult,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("municipios", enderecoService.listarMunicipios());
            return "auth/registro";
        }
        try {
            Usuario novo = usuarioService.registrarPassageiro(form);
            // SPEC-07: salva o endereço, se algum campo foi informado (opcional).
            enderecoService.salvar(novo.getId(), form.paraEnderecoForm());
        } catch (RegraNegocioException e) {
            bindingResult.reject("cadastro.invalido", e.getMessage());
            model.addAttribute("municipios", enderecoService.listarMunicipios());
            return "auth/registro";
        }
        redirectAttributes.addFlashAttribute("cadastroSucesso", true);
        return "redirect:/login?cadastro";
    }
}
