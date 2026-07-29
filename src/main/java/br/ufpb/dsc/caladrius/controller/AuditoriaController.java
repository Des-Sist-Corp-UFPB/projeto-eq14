package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.domain.LogAuditoria;
import br.ufpb.dsc.caladrius.service.AuditoriaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Visões da trilha de auditoria (#19):
 * <ul>
 *   <li>{@code /admin/auditoria} — trilha completa (SYSADMIN).</li>
 *   <li>{@code /historico} — histórico de operação de negócio (GERENTE).</li>
 * </ul>
 */
@Controller
public class AuditoriaController {

    private static final int TAMANHO_PAGINA = 20;

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/admin/auditoria")
    public String trilhaCompleta(@RequestParam(name = "pagina", defaultValue = "0") int pagina, Model model) {
        Page<LogAuditoria> logs = auditoriaService.listarTudo(PageRequest.of(pagina, TAMANHO_PAGINA));
        preencher(model, logs, pagina, "Auditoria do sistema",
                "Trilha completa: acessos, operações e configurações.", "/admin/auditoria");
        return "admin/auditoria";
    }

    /**
     * Histórico das <strong>solicitações</strong> — o que o gerente acompanha no dia a
     * dia: pedido criado, cancelado pelo passageiro, aprovado ou recusado. O restante
     * da trilha (cadastros, acessos, configuração) fica na central de logs
     * ({@code /logs}), para esta tela não virar um "tudo" que ninguém lê.
     */
    @GetMapping("/historico")
    public String historicoSolicitacoes(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                        Model model) {
        Page<LogAuditoria> logs = auditoriaService.listarSolicitacoes(PageRequest.of(pagina, TAMANHO_PAGINA));
        preencher(model, logs, pagina, "Histórico de solicitações",
                "Pedidos de transporte: criação, cancelamento, aprovação e recusa. "
                        + "A trilha completa do sistema fica em Logs.", "/historico");
        return "admin/auditoria";
    }

    private void preencher(Model model, Page<LogAuditoria> logs, int pagina,
                           String titulo, String subtitulo, String baseUrl) {
        model.addAttribute("logs", logs);
        model.addAttribute("paginaAtual", pagina);
        model.addAttribute("titulo", titulo);
        model.addAttribute("subtitulo", subtitulo);
        model.addAttribute("baseUrl", baseUrl);
    }
}
