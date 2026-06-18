package br.ufpb.dsc.caladrius.notificacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Canal de WhatsApp. <strong>Stub</strong> — a integração (Evolution API) ainda
 * está fora do escopo. Mantido como bean para que o despacho já contemple o
 * canal; a implementação real entra depois sem alterar os chamadores.
 */
@Component
public class NotificacaoWhatsappCanal implements CanalNotificacao {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoWhatsappCanal.class);

    @Override
    public CanalTipo tipo() {
        return CanalTipo.WHATSAPP;
    }

    @Override
    public void enviar(NotificacaoDestino destino, String titulo, String mensagem) {
        if (!StringUtils.hasText(destino.telefone())) {
            return;
        }
        // Integração futura (Evolution API). Por ora, não envia — apenas registra.
        log.info("[WHATSAPP/stub — não implementado] para {} — {}: {}",
                destino.telefone(), titulo, mensagem);
    }
}
