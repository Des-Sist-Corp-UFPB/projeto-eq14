package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.service.EnderecoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Aba de análise de passageiros (GERENTE) — contagem por bairro e por município
 * (SPEC-07).
 */
@Controller
@RequestMapping("/analise")
public class AnaliseController {

    private final EnderecoService enderecoService;

    public AnaliseController(EnderecoService enderecoService) {
        this.enderecoService = enderecoService;
    }

    @GetMapping
    public String passageiros(Model model) {
        model.addAttribute("titulo", "Análise de passageiros");
        model.addAttribute("porBairro", enderecoService.contarPorBairro());
        model.addAttribute("porMunicipio", enderecoService.contarPorMunicipio());
        return "analise/passageiros";
    }
}
