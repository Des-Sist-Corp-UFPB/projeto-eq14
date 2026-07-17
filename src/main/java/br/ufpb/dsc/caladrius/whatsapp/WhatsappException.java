package br.ufpb.dsc.caladrius.whatsapp;

/**
 * Falha na comunicação com o provedor de WhatsApp (SPEC-10).
 *
 * <p>Exceção <strong>da porta</strong>: os adaptadores traduzem os erros do
 * provedor (HTTP 4xx/5xx, timeout, payload inesperado) para ela, de modo que
 * os chamadores nunca dependam de exceções específicas da Evolution. Quem
 * envia notificações a captura e segue (RN-WPP-01).
 */
public class WhatsappException extends RuntimeException {

    public WhatsappException(String mensagem) {
        super(mensagem);
    }

    public WhatsappException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
