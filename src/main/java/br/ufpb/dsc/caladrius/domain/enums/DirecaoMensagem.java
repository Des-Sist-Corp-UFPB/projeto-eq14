package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Direção de uma mensagem WhatsApp registrada no log (SPEC-10, RN-WPP-10).
 */
public enum DirecaoMensagem {

    RECEBIDA("Recebida", "primary"),
    ENVIADA("Enviada", "success");

    private final String rotulo;
    private final String cor;

    DirecaoMensagem(String rotulo, String cor) {
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
