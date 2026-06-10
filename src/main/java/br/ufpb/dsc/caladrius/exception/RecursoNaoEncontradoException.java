package br.ufpb.dsc.caladrius.exception;

/**
 * Lançada quando um recurso (veículo, cidade, usuário, viagem...) não é
 * encontrado pelo identificador informado.
 *
 * <p>É uma exceção de domínio genérica e reutilizável — em vez de uma exceção
 * por entidade. Capturada nos controllers para devolver HTTP 404.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

    /**
     * Cria a exceção com uma mensagem padronizada a partir do tipo e do id.
     *
     * @param recurso nome do recurso (ex.: "Veículo")
     * @param id      identificador procurado
     */
    public RecursoNaoEncontradoException(String recurso, Object id) {
        super(recurso + " não encontrado(a) com id: " + id);
    }
}
