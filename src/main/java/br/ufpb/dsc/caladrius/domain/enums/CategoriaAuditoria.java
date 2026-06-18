package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Categoria de um evento de auditoria, usada para separar as visões:
 * o SYSADMIN vê tudo; o GERENTE vê apenas {@link #OPERACAO}.
 */
public enum CategoriaAuditoria {

    /** Acessos e segurança: login, falha de login, logout. */
    SEGURANCA("Segurança"),
    /** Operação de negócio: criação/edição/exclusão de cadastros e viagens. */
    OPERACAO("Operação"),
    /** Eventos do próprio sistema: alterações de configuração, etc. */
    SISTEMA("Sistema");

    private final String rotulo;

    CategoriaAuditoria(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
