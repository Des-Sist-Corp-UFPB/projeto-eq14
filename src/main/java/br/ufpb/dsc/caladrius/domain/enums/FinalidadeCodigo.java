package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Finalidade de um código OTP (SPEC-12): o que a validação do código autoriza.
 *
 * <ul>
 *   <li>{@link #VERIFICAR_TELEFONE} — confirma que o telefone pertence à pessoa
 *       (no cadastro / ao completar perfil); sucesso marca {@code telefone_verificado_em}
 *       e promove o cadastro PENDENTE→ATIVO (RN-VER-02).</li>
 *   <li>{@link #RESET_SENHA} — libera a redefinição de senha ("esqueci a senha").</li>
 * </ul>
 *
 * <p>O e-mail é verificado por <strong>link mágico</strong> (reusa {@code TokenAtivacao}),
 * não por OTP — por isso não há {@code VERIFICAR_EMAIL} aqui.
 */
public enum FinalidadeCodigo {

    VERIFICAR_TELEFONE("verificação do seu telefone"),
    RESET_SENHA("redefinição de senha");

    private final String rotulo;

    FinalidadeCodigo(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Trecho amigável para compor a mensagem enviada ("Seu código para ..."). */
    public String getRotulo() {
        return rotulo;
    }
}
