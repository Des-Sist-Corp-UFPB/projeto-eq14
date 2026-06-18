package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.NotificacaoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Ações do sino de notificações (in-app).
 */
@Controller
@RequestMapping("/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    /** Marca todas as notificações do usuário como lidas. */
    @PostMapping("/marcar-lidas")
    public String marcarLidas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (usuario != null) {
            notificacaoService.marcarTodasLidas(usuario.getId());
        }
        return "redirect:/";
    }
}
