package br.ufpb.dsc.caladrius.config;

import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Tratamento centralizado de exceções de domínio que não são capturadas
 * localmente nos controllers.
 *
 * <p>{@link RecursoNaoEncontradoException} vira HTTP 404 com a mensagem amigável.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public String recursoNaoEncontrado(RecursoNaoEncontradoException e) {
        return e.getMessage();
    }
}
