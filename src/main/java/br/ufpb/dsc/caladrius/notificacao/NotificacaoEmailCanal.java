package br.ufpb.dsc.caladrius.notificacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Canal de e-mail. <strong>Stub</strong> por enquanto: registra no log o e-mail
 * que seria enviado. A integração real (JavaMailSender/SMTP) entra depois sem
 * mexer no resto — basta trocar esta implementação.
 */
@Component
public class NotificacaoEmailCanal implements CanalNotificacao {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoEmailCanal.class);

    @Override
    public CanalTipo tipo() {
        return CanalTipo.EMAIL;
    }

    @Override
    public void enviar(NotificacaoDestino destino, String titulo, String mensagem) {
        if (!StringUtils.hasText(destino.email())) {
            return;
        }
        // TODO: integrar JavaMailSender/SMTP. Por ora, apenas registra.
        log.info("[E-MAIL/stub] para {} — {}: {}", destino.email(), titulo, mensagem);
    }
}
