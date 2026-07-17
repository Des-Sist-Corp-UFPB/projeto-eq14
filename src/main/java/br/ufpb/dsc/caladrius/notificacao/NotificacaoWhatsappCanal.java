package br.ufpb.dsc.caladrius.notificacao;

import br.ufpb.dsc.caladrius.service.WhatsappService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Canal de WhatsApp do {@code NotificacaoService} (SPEC-10). Delega o envio à
 * fachada {@link WhatsappService}, que fala com a porta {@code ProvedorWhatsapp}
 * e registra o log — a interface {@link CanalNotificacao} e todos os chamadores
 * ficaram intactos em relação à fase de stub.
 *
 * <p>Sem a integração configurada, o envio é no-op (RN-WPP-02); falha do
 * provedor nunca derruba o chamador (RN-WPP-01) — ambas garantidas dentro do
 * {@code WhatsappService}.
 */
@Component
public class NotificacaoWhatsappCanal implements CanalNotificacao {

    private final WhatsappService whatsappService;

    public NotificacaoWhatsappCanal(WhatsappService whatsappService) {
        this.whatsappService = whatsappService;
    }

    @Override
    public CanalTipo tipo() {
        return CanalTipo.WHATSAPP;
    }

    @Override
    public void enviar(NotificacaoDestino destino, String titulo, String mensagem) {
        if (!StringUtils.hasText(destino.telefone())) {
            return;
        }
        whatsappService.enviarTexto(destino.telefone(), "*" + titulo + "*\n" + mensagem);
    }
}
