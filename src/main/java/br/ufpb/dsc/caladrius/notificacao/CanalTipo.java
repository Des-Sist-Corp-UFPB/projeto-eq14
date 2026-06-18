package br.ufpb.dsc.caladrius.notificacao;

/**
 * Meios de notificação suportados. O WhatsApp é um <em>stub</em> por enquanto
 * (integração via Evolution API ainda fora do escopo).
 */
public enum CanalTipo {
    IN_APP,
    EMAIL,
    WHATSAPP
}
