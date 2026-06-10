package br.ufpb.dsc.caladrius.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Disponibiliza atributos globais a todos os templates Thymeleaf.
 *
 * <p>O Thymeleaf 3.1 removeu o acesso direto a {@code #request}/{@code #session}.
 * Expomos apenas o necessário via {@code @ModelAttribute} num {@code @ControllerAdvice},
 * aplicado antes de qualquer método de qualquer controller.
 *
 * <p>Aqui publicamos a URI atual, usada pelo layout (sidebar) para destacar o
 * item de menu ativo.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    /**
     * URI da requisição atual (ex.: "/veiculos", "/viagens/nova").
     *
     * @param request requisição HTTP injetada pelo Spring
     * @return a URI atual
     */
    @ModelAttribute("requestURI")
    public String requestURI(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
