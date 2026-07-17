package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Origem/modelo de uma {@code SolicitacaoViagem}:
 *
 * <ul>
 *   <li>{@link #POR_LINHA} — o passageiro escolhe uma {@code LinhaProgramada}
 *       existente (SPEC-09); a alocação deriva da designação da viagem.</li>
 *   <li>{@link #SOB_DEMANDA} — o passageiro informa destino + data + horário +
 *       condições, sem linha (SPEC-11); o gestor avalia, aprova (designando uma
 *       viagem imprevista) ou recusa.</li>
 * </ul>
 */
public enum TipoSolicitacao {

    POR_LINHA("Por linha", "primary"),
    SOB_DEMANDA("Sob demanda", "warning text-dark");

    private final String rotulo;
    private final String cor;

    TipoSolicitacao(String rotulo, String cor) {
        this.rotulo = rotulo;
        this.cor = cor;
    }

    /** Rótulo amigável para exibição na interface. */
    public String getRotulo() {
        return rotulo;
    }

    /** Sufixo de classe Bootstrap do badge (azul = rotineira; laranja = imprevista). */
    public String getCor() {
        return cor;
    }
}
