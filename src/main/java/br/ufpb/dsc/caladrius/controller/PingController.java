package br.ufpb.dsc.caladrius.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Health check público exigido pela disciplina.
 *
 * <p>Contrato: {@code GET /ping} → {@code 200} com JSON
 * {@code {"status":"ok","service":"eq14","timestamp":"..."}}.
 *
 * <p>É um {@code @RestController} (retorna JSON direto) e está liberado sem
 * autenticação no {@code SecurityConfig}.
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "service", "eq14",
                "timestamp", Instant.now().toString());
    }
}
