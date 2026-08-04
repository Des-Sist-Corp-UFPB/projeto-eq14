package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.health.BancoHealthIndicator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Health check público exigido pela disciplina.
 *
 * <p>Contrato: {@code GET /ping} → {@code 200} com JSON
 * {@code {"status":"ok","service":"eq14","database":"up","timestamp":"..."}}.
 *
 * <p>O campo {@code database} vem do {@link BancoHealthIndicator}, que executa um
 * {@code SELECT} de verdade contra o PostgreSQL (SPEC-OPE-02) — não é um valor fixo.
 *
 * <p><strong>O status HTTP é sempre 200, mesmo com o banco fora</strong> (RN-HC-03).
 * O {@code /ping} é o contrato de <em>liveness</em> da disciplina e alimenta monitores
 * de disponibilidade: devolver 503 faria a aplicação inteira ser marcada como fora do
 * ar por causa de uma dependência. Quem reporta indisponibilidade com o código HTTP
 * correto é o {@code /actuator/health} — o healthcheck do container.
 *
 * <p>É um {@code @RestController} (retorna JSON direto) e está liberado sem
 * autenticação no {@code SecurityConfig}.
 */
@RestController
public class PingController {

    private final BancoHealthIndicator banco;

    public PingController(BancoHealthIndicator banco) {
        this.banco = banco;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "service", "eq14",
                "database", banco.acessivel() ? "up" : "down",
                "timestamp", Instant.now().toString());
    }
}
