package br.ufpb.dsc.caladrius.domain.enums;

import br.ufpb.dsc.caladrius.notificacao.CanalTipo;

/**
 * Método escolhido pela pessoa na recuperação de senha (SPEC-12): define o
 * <strong>campo de busca</strong> do usuário e o <strong>canal</strong> do OTP.
 * O login usa a heurística "{@code @}" — aqui a escolha é <em>explícita</em>
 * (RN-REC-01).
 */
public enum MetodoVerificacao {

    EMAIL("E-mail", CanalTipo.EMAIL),
    TELEFONE("Telefone", CanalTipo.WHATSAPP);

    private final String rotulo;
    private final CanalTipo canal;

    MetodoVerificacao(String rotulo, CanalTipo canal) {
        this.rotulo = rotulo;
        this.canal = canal;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Canal por onde o código é enviado (Telefone→WhatsApp; E-mail→e-mail). */
    public CanalTipo getCanal() {
        return canal;
    }
}
