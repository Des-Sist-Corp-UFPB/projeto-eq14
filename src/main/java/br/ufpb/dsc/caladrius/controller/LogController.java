package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.LogAuditoria;
import br.ufpb.dsc.caladrius.domain.enums.AreaSistema;
import br.ufpb.dsc.caladrius.service.AuditoriaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Central de logs do sistema ({@code /logs}) — item de primeiro nível no menu,
 * sem submenus.
 *
 * <p>Reúne <strong>tudo</strong> o que o sistema registra (acessos, cadastros,
 * viagens, solicitações, WhatsApp, configuração), cada evento com a etiqueta da
 * {@link AreaSistema} que ele tocou. Complementa — e não substitui — as duas telas
 * que já existiam: {@code /historico} (o ciclo das solicitações, visão do gerente)
 * e {@code /admin/auditoria} (a mesma trilha dentro da área do SYSADMIN).
 *
 * <p>Quando existir organização/secretaria ([SPEC-PLT-03]), esta listagem passa a ser
 * filtrada pelo tenant — é o mesmo ponto único de leitura.
 */
@Controller
public class LogController {

    private static final int TAMANHO_PAGINA = 25;

    private final AuditoriaService auditoriaService;

    public LogController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/logs")
    public String logs(@RequestParam(name = "area", required = false) AreaSistema area,
                       @RequestParam(name = "busca", required = false, defaultValue = "") String busca,
                       @RequestParam(name = "pagina", defaultValue = "0") int pagina,
                       Model model) {
        Page<LogAuditoria> logs =
                auditoriaService.listar(area, busca, PageRequest.of(pagina, TAMANHO_PAGINA));

        model.addAttribute("titulo", "Logs do sistema");
        model.addAttribute("logs", logs);
        model.addAttribute("areas", AreaSistema.values());
        model.addAttribute("area", area);
        model.addAttribute("busca", busca);
        model.addAttribute("paginaAtual", pagina);
        return "logs/lista";
    }
}
