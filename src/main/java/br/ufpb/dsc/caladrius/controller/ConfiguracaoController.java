package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.service.AuditoriaService;
import br.ufpb.dsc.caladrius.service.CidadeService;
import br.ufpb.dsc.caladrius.service.ConfiguracaoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Configurações do sistema (SYSADMIN): tempo de sessão dinâmico (DT-10) e
 * cidade-sede (origem padrão das viagens, SPEC-06).
 */
@Controller
@RequestMapping("/admin/configuracoes")
public class ConfiguracaoController {

    private final ConfiguracaoService configuracaoService;
    private final CidadeService cidadeService;
    private final AuditoriaService auditoriaService;

    public ConfiguracaoController(ConfiguracaoService configuracaoService, CidadeService cidadeService,
                                  AuditoriaService auditoriaService) {
        this.configuracaoService = configuracaoService;
        this.cidadeService = cidadeService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("titulo", "Configurações do sistema");
        model.addAttribute("timeoutMinutos", configuracaoService.getTimeoutSessaoMinutos());
        model.addAttribute("cidadeSedeId", configuracaoService.getCidadeSedeId().orElse(null));
        model.addAttribute("cidades", cidadeService.listarTodas());
        return "admin/configuracoes";
    }

    @PostMapping
    public String salvar(@RequestParam("timeoutMinutos") int timeoutMinutos,
                         @RequestParam(value = "cidadeSedeId", required = false) UUID cidadeSedeId,
                         RedirectAttributes redirect) {
        configuracaoService.setTimeoutSessaoMinutos(timeoutMinutos);
        configuracaoService.setCidadeSede(cidadeSedeId);
        auditoriaService.registrarSistema("CONFIG_ALTERADA",
                "timeout=" + timeoutMinutos + "min; cidadeSede=" + (cidadeSedeId != null ? cidadeSedeId : "—"));
        redirect.addFlashAttribute("sucesso",
                "Configurações salvas. O novo tempo de sessão vale para os próximos logins.");
        return "redirect:/admin/configuracoes";
    }
}
