package br.ufpb.dsc.caladrius.whatsapp;

/**
 * Consumidor das mensagens recebidas pelo webhook (SPEC-10).
 *
 * <p>Interface entre o {@code WhatsappWebhookController} e o bot de
 * atendimento: o controller entrega a {@link MensagemRecebida} normalizada e
 * não sabe quem responde. Hoje o processamento acontece na própria requisição
 * (volume baixo); trocar para fila/assíncrono depois não muda o controller.
 */
public interface ProcessadorMensagemRecebida {

    void processar(MensagemRecebida mensagem);
}
