package br.ufpb.dsc.caladrius.notificacao;

/**
 * Abstração de um meio de envio de notificação. Implementações concretas
 * (in-app, e-mail, WhatsApp) são plugadas como beans Spring e despachadas pelo
 * {@code NotificacaoService} — mantendo os meios <strong>desacoplados</strong>.
 */
public interface CanalNotificacao {

    /** Tipo do canal, usado para o despacho seletivo. */
    CanalTipo tipo();

    /** Envia a notificação pelo meio concreto. */
    void enviar(NotificacaoDestino destino, String titulo, String mensagem);
}
