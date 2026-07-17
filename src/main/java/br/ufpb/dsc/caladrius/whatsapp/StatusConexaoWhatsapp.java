package br.ufpb.dsc.caladrius.whatsapp;

/**
 * Estado da conexão da conta WhatsApp no provedor (SPEC-10).
 *
 * <p>Vocabulário da porta {@link ProvedorWhatsapp} — independente do provedor
 * (na Evolution: {@code open} → CONECTADO, {@code connecting} → AGUARDANDO_QR,
 * {@code close} → DESCONECTADO).
 */
public enum StatusConexaoWhatsapp {

    CONECTADO("Conectado", "success"),
    AGUARDANDO_QR("Aguardando leitura do QR code", "warning text-dark"),
    DESCONECTADO("Desconectado", "secondary");

    private final String rotulo;
    private final String cor;

    StatusConexaoWhatsapp(String rotulo, String cor) {
        this.rotulo = rotulo;
        this.cor = cor;
    }

    /** Rótulo amigável para exibição na interface. */
    public String getRotulo() {
        return rotulo;
    }

    /** Sufixo de classe Bootstrap do badge (ex.: {@code success} → {@code bg-success}). */
    public String getCor() {
        return cor;
    }
}
