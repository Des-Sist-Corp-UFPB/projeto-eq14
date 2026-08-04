package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Estado do vínculo entre uma pessoa e uma secretaria (SPEC-PLT-02 §4, ADR-22).
 *
 * <p><strong>RN-MT-08:</strong> o vínculo nasce {@link #PENDENTE} — escolher uma
 * secretaria na tela não concede acesso a ela; quem ativa é um gestor da organização.
 */
public enum StatusVinculo {

    /** Solicitado, aguardando aprovação de um gestor da organização. */
    PENDENTE("Pendente"),

    /** Ativo: a pessoa pode entrar nesta secretaria com este papel. */
    ATIVO("Ativo"),

    /** Encerrado. O registro permanece — o histórico não se apaga (RN-MT-16). */
    REVOGADO("Revogado");

    private final String descricao;

    StatusVinculo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
