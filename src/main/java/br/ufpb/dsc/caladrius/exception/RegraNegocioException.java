package br.ufpb.dsc.caladrius.exception;

/**
 * Lançada quando uma regra de negócio é violada — por exemplo, telefone/e-mail
 * já em uso, ou CPF inválido no cadastro.
 *
 * <p>Diferente das validações de formato (Bean Validation, que ocorrem na borda
 * com {@code @Valid}), esta exceção cobre regras que dependem do estado do banco
 * ou de lógica do serviço. A mensagem é amigável e pode ser exibida ao usuário.
 */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
