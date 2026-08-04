package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Ciclo de vida de uma secretaria cliente (SPEC-PLT-02 §3, SPEC-PLT-03 D2).
 *
 * <p>Só {@link #ATIVA} — e {@link #INADIMPLENTE} dentro da tolerância — permite operar.
 * Nenhum estado apaga dado: inadimplência e suspensão restringem o acesso, nunca o
 * histórico (RN-PAG-04 / RN-MT-16).
 */
public enum StatusOrganizacao {

    /** Criada, ainda sem plano escolhido. */
    RASCUNHO("Rascunho"),

    /** Plano escolhido, aguardando a confirmação servidor-a-servidor do pagamento. */
    AGUARDANDO_PAGAMENTO("Aguardando pagamento"),

    /** Operando normalmente. */
    ATIVA("Ativa"),

    /** Assinatura vencida, dentro do período de tolerância: leitura preservada. */
    INADIMPLENTE("Inadimplente"),

    /** Acesso suspenso após a tolerância. */
    SUSPENSA("Suspensa"),

    /** Contrato encerrado. */
    CANCELADA("Cancelada");

    private final String descricao;

    StatusOrganizacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** {@code true} se a secretaria pode ser usada para entrar no sistema. */
    public boolean permiteOperar() {
        return this == ATIVA || this == INADIMPLENTE;
    }
}
