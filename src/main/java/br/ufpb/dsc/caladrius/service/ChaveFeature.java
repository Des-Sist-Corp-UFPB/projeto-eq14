package br.ufpb.dsc.caladrius.service;

import java.util.Optional;

/**
 * Catálogo das <strong>flags globais</strong> de funcionalidade (SPEC-13 §5.2).
 *
 * <p>Cada flag é uma linha chave/valor em {@code configuracoes_sistema} (D1 — não
 * há tabela nova) e carrega, aqui no código, o seu <strong>default seguro</strong>:
 * chave ausente ou valor inválido ⇒ vale o default (RN-FLG-02, <em>fail-safe</em>).
 *
 * <p>Ligar/desligar é feito pelo SYSADMIN em {@code /admin/features}, em runtime e
 * sem redeploy — ver {@link FeatureFlagService}.
 */
public enum ChaveFeature {

    /**
     * Atendimento automático do bot no WhatsApp (<em>kill switch</em> de operação).
     * Desligada, o webhook segue registrando as mensagens recebidas, mas não aciona
     * o bot (RN-FLG-04).
     */
    BOT_WHATSAPP("feature.bot_whatsapp", true,
            "Bot do WhatsApp",
            "Atendimento automático no WhatsApp. Desligado, as mensagens continuam sendo "
                    + "registradas, mas ninguém recebe resposta do bot."),

    /**
     * Modo de manutenção (<em>kill switch</em> global): usuários comuns recebem a
     * página de manutenção (503) e só o SYSADMIN continua navegando (RN-FLG-03).
     */
    MODO_MANUTENCAO("feature.modo_manutencao", false,
            "Modo de manutenção",
            "Coloca o sistema fora do ar para todos os papéis, exceto o SYSADMIN — "
                    + "que continua entrando para religar.");

    private final String chave;
    private final boolean padrao;
    private final String rotulo;
    private final String descricao;

    ChaveFeature(String chave, boolean padrao, String rotulo, String descricao) {
        this.chave = chave;
        this.padrao = padrao;
        this.rotulo = rotulo;
        this.descricao = descricao;
    }

    /** Chave em {@code configuracoes_sistema} (ex.: {@code feature.bot_whatsapp}). */
    public String getChave() {
        return chave;
    }

    /** Valor usado quando a chave está ausente ou o valor gravado é inválido. */
    public boolean isPadrao() {
        return padrao;
    }

    /** Nome exibido na tela de administração. */
    public String getRotulo() {
        return rotulo;
    }

    /** Explicação do efeito da flag, exibida junto ao interruptor. */
    public String getDescricao() {
        return descricao;
    }

    /** Resolve a flag pela chave persistida (vazio se não for uma chave conhecida). */
    public static Optional<ChaveFeature> porChave(String chave) {
        for (ChaveFeature f : values()) {
            if (f.chave.equals(chave)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
