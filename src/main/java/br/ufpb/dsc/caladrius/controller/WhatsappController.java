package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.dto.ConfiguracaoEnvioWhatsapp;
import br.ufpb.dsc.caladrius.service.WhatsappService;
import br.ufpb.dsc.caladrius.util.Documentos;
import br.ufpb.dsc.caladrius.whatsapp.ConexaoWhatsapp;
import br.ufpb.dsc.caladrius.whatsapp.StatusConexaoWhatsapp;
import br.ufpb.dsc.caladrius.whatsapp.WhatsappException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Painel WhatsApp do GERENTE (SPEC-10 §9) — o "manager dissolvido": status da
 * conexão, QR code de pareamento (com polling HTMX), conta conectada, teste de
 * envio e as últimas mensagens do log.
 *
 * <p>Tudo passa pela fachada {@link WhatsappService} (que fala com a porta) —
 * a view nunca vê URL/apikey da Evolution (RN-WPP-09). Sem a integração
 * configurada no ambiente, o painel informa e nada quebra (RN-WPP-02).
 *
 * <p>As mutações (conectar/desconectar/teste) são form-posts comuns com token
 * CSRF (via Thymeleaf) — {@code /whatsapp} não entra na lista de exceção do
 * CSRF; o HTMX é usado só no polling GET do status.
 */
@Controller
@RequestMapping("/whatsapp")
public class WhatsappController {

    private final WhatsappService whatsappService;

    public WhatsappController(WhatsappService whatsappService) {
        this.whatsappService = whatsappService;
    }

    @GetMapping
    public String painel(Model model) {
        model.addAttribute("titulo", "WhatsApp");
        carregarStatus(model);
        model.addAttribute("configEnvio", whatsappService.configuracaoEnvio());
        model.addAttribute("mensagens", whatsappService.ultimasMensagens());
        return "whatsapp/inicio";
    }

    @PostMapping("/configuracoes")
    public String salvarConfiguracoes(@RequestParam(value = "nomeExibicao", required = false) String nomeExibicao,
                                      @RequestParam(value = "mensagemConfirmacao", required = false) String mensagemConfirmacao,
                                      @RequestParam(value = "atendimentoInicio", required = false) String atendimentoInicio,
                                      @RequestParam(value = "atendimentoFim", required = false) String atendimentoFim,
                                      RedirectAttributes redirect) {
        whatsappService.salvarConfiguracaoEnvio(new ConfiguracaoEnvioWhatsapp(
                nomeExibicao, mensagemConfirmacao, atendimentoInicio, atendimentoFim));
        redirect.addFlashAttribute("sucesso", "Configurações de envio salvas.");
        return "redirect:/whatsapp";
    }

    /** Fragmento do card de status — polling HTMX (~3 s) enquanto aguarda o QR. */
    @GetMapping("/status")
    public String status(Model model) {
        carregarStatus(model);
        return "whatsapp/fragments/status :: status";
    }

    @PostMapping("/conectar")
    public String conectar(RedirectAttributes redirect) {
        try {
            ConexaoWhatsapp conexao = whatsappService.conectar();
            redirect.addFlashAttribute("sucesso", conexao.status() == StatusConexaoWhatsapp.CONECTADO
                    ? "A conta já está conectada."
                    : "Conexão iniciada — escaneie o QR code com o WhatsApp do número da secretaria.");
        } catch (WhatsappException e) {
            redirect.addFlashAttribute("erro", "Não foi possível iniciar a conexão. Verifique a integração e tente novamente.");
        }
        return "redirect:/whatsapp";
    }

    @PostMapping("/desconectar")
    public String desconectar(RedirectAttributes redirect) {
        try {
            whatsappService.desconectar();
            redirect.addFlashAttribute("sucesso", "Sessão desconectada.");
        } catch (WhatsappException e) {
            redirect.addFlashAttribute("erro", "Não foi possível desconectar. Tente novamente.");
        }
        return "redirect:/whatsapp";
    }

    @PostMapping("/teste")
    public String teste(@RequestParam("telefone") String telefone,
                        @RequestParam("texto") String texto,
                        RedirectAttributes redirect) {
        String digitos = Documentos.apenasDigitos(telefone);
        if (digitos.length() < 10 || texto == null || texto.isBlank()) {
            redirect.addFlashAttribute("erro", "Informe um telefone válido (DDD + número) e o texto da mensagem.");
            return "redirect:/whatsapp";
        }
        boolean enviado = whatsappService.enviarTexto(digitos, texto.trim());
        if (enviado) {
            redirect.addFlashAttribute("sucesso", "Mensagem de teste enviada para " + digitos + ".");
        } else {
            redirect.addFlashAttribute("erro", "O envio falhou — confira a conexão no card de status.");
        }
        return "redirect:/whatsapp";
    }

    private void carregarStatus(Model model) {
        boolean configurada = whatsappService.configurada();
        ConexaoWhatsapp conexao = whatsappService.estadoConexao();
        model.addAttribute("configurada", configurada);
        model.addAttribute("conexao", conexao);
        // Número/perfil só interessam (e só custam uma chamada) quando conectado.
        model.addAttribute("conta", conexao.status() == StatusConexaoWhatsapp.CONECTADO
                ? whatsappService.contaConectada().orElse(null) : null);
    }
}
