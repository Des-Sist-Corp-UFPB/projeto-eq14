package br.ufpb.dsc.caladrius.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Painel de administração do sistema (papel SYSADMIN, isolado).
 *
 * <p>Reúne as configurações técnicas do CALADRIUS — sessão, auditoria/logs e
 * convites — separadas da operação de negócio (que é do GERENTE). As sub-telas
 * são adicionadas pelas features de configuração (#18), auditoria (#19) e
 * convites (#20).
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    @GetMapping
    public String home(Model model) {
        model.addAttribute("titulo", "Administração");
        return "admin/home";
    }
}
