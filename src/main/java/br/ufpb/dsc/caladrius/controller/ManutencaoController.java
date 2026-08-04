package br.ufpb.dsc.caladrius.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Página pública de manutenção (SPEC-PLT-01, RN-FLG-03) — o destino para onde o
 * {@code ManutencaoFilter} encaminha as requisições enquanto a flag
 * {@code feature.modo_manutencao} está ligada.
 *
 * <p>Responde <strong>503 Service Unavailable</strong>: é a semântica correta de
 * "fora do ar temporariamente" e evita que buscadores/monitoramento tratem a
 * página como conteúdo normal (200).
 */
@Controller
public class ManutencaoController {

    @GetMapping("/manutencao")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String manutencao() {
        return "manutencao";
    }
}
