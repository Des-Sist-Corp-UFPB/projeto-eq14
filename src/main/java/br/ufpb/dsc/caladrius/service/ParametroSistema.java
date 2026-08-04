package br.ufpb.dsc.caladrius.service;

import java.util.Optional;

/**
 * Catálogo dos <strong>parâmetros de negócio</strong> externalizados (SPEC-PLT-01 §5.2,
 * D4) — constantes que antes viviam "chumbadas" no código e passaram a ser
 * configuráveis em runtime pelo SYSADMIN.
 *
 * <p>Cada parâmetro declara o <strong>default</strong> (o valor que o código usava
 * antes) e o <strong>intervalo válido</strong>. Valor ausente, não-numérico ou fora
 * do intervalo ⇒ vale o default (RN-FLG-02/RN-FLG-06) — o sistema nunca quebra por
 * configuração faltante ou torta.
 */
public enum ParametroSistema {

    /** Validade do código OTP, em minutos (SPEC-ACE-03). */
    OTP_VALIDADE_MINUTOS("param.otp.validade_minutos", 10, 1, 60,
            "Validade do código OTP (minutos)"),

    /** Tentativas erradas até o <em>lockout</em> do código (SPEC-ACE-03, RN-VER-05). */
    OTP_MAX_TENTATIVAS("param.otp.max_tentativas", 5, 1, 10,
            "Tentativas até bloquear o código OTP"),

    /** Espera mínima entre dois pedidos de código (SPEC-ACE-03, RN-VER-06). */
    OTP_COOLDOWN_SEGUNDOS("param.otp.cooldown_segundos", 60, 0, 600,
            "Espera entre reenvios de OTP (segundos)"),

    /** Validade do link de convite/ativação, em dias (#20, ADR-11). */
    CONVITE_VALIDADE_DIAS("param.convite.validade_dias", 7, 1, 90,
            "Validade do convite (dias)"),

    /** Tamanho mínimo de senha aceito no convite/definição de senha. */
    SENHA_MIN("param.senha.min", 6, 6, 72,
            "Tamanho mínimo da senha (caracteres)");

    private final String chave;
    private final int padrao;
    private final int minimo;
    private final int maximo;
    private final String rotulo;

    ParametroSistema(String chave, int padrao, int minimo, int maximo, String rotulo) {
        this.chave = chave;
        this.padrao = padrao;
        this.minimo = minimo;
        this.maximo = maximo;
        this.rotulo = rotulo;
    }

    /** Chave em {@code configuracoes_sistema} (ex.: {@code param.otp.max_tentativas}). */
    public String getChave() {
        return chave;
    }

    /** Valor de fábrica — usado sempre que o configurado for ausente ou inválido. */
    public int getPadrao() {
        return padrao;
    }

    public int getMinimo() {
        return minimo;
    }

    public int getMaximo() {
        return maximo;
    }

    /** Nome exibido na tela de administração. */
    public String getRotulo() {
        return rotulo;
    }

    /** {@code true} se o valor está no intervalo aceito (RN-FLG-06). */
    public boolean valido(int valor) {
        return valor >= minimo && valor <= maximo;
    }

    /** Resolve o parâmetro pela chave persistida (vazio se desconhecida). */
    public static Optional<ParametroSistema> porChave(String chave) {
        for (ParametroSistema p : values()) {
            if (p.chave.equals(chave)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
