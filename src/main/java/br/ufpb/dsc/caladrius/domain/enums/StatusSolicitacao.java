package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Ciclo de vida de uma {@code SolicitacaoViagem} do passageiro (SPEC-09).
 *
 * <p>Fluxo: {@code PENDENTE} (aguardando a designação da viagem pelo gerente) →
 * {@code ALOCADA} (a viagem da linha+data foi designada e o passageiro está
 * vinculado a ela). O passageiro pode {@code CANCELAR} a qualquer momento;
 * {@code RECUSADA} fica reservada para uma futura recusa pelo gestor.
 */
public enum StatusSolicitacao {

    PENDENTE("Pendente", "warning text-dark"),
    ALOCADA("Alocada", "success"),
    CANCELADA("Cancelada", "secondary"),
    RECUSADA("Recusada", "danger");

    private final String rotulo;
    private final String cor;

    StatusSolicitacao(String rotulo, String cor) {
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
