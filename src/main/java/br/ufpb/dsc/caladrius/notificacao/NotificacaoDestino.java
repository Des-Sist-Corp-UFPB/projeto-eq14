package br.ufpb.dsc.caladrius.notificacao;

import java.util.UUID;

/**
 * Destinatário de uma notificação. Cada canal usa o que precisa: o in-app usa
 * {@code usuarioId}; o e-mail usa {@code email}; o WhatsApp usa {@code telefone}.
 */
public record NotificacaoDestino(UUID usuarioId, String email, String telefone) {

    public static NotificacaoDestino paraUsuario(UUID usuarioId, String email, String telefone) {
        return new NotificacaoDestino(usuarioId, email, telefone);
    }
}
