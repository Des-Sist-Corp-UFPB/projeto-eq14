package br.ufpb.dsc.caladrius.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Seção WhatsApp — exclusiva do GERENTE. As funcionalidades (notificações e,
 * possivelmente, recebimento de solicitações via Evolution API) ainda estão em
 * avaliação; por ora é um espaço reservado.
 */
@Controller
@RequestMapping("/whatsapp")
public class WhatsappController {

    @GetMapping
    public String inicio(Model model) {
        model.addAttribute("titulo", "WhatsApp");
        return "whatsapp/inicio";
    }
}
