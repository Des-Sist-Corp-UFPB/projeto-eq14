# AI Agents Context — CALADRIUS (eq14)

> Contexto para ferramentas de IA (Cursor, Copilot, etc.).
> Para Claude Code, veja [CLAUDE.md](CLAUDE.md) (mais completo).

## Projeto
CALADRIUS — agendamento de transporte municipal de saúde. Spring Boot (Java 21), PostgreSQL,
Thymeleaf + HTMX + Bootstrap. RBAC: PASSAGEIRO, MOTORISTA, GERENTE. Adaptado do boilerplate
"Mercado" da disciplina (equipe eq14).

## Pacote base
`br.ufpb.dsc.caladrius`

## Padrões importantes
- DTOs são Records Java imutáveis com Bean Validation.
- Service layer com `@Transactional`; consultas com `@Transactional(readOnly = true)`.
- Controllers devolvem fragmentos Thymeleaf para HTMX (header `HX-Request`).
- Autenticação no banco (Spring Security); **login por e-mail OU telefone**; senhas BCrypt.
- Enums persistidos como VARCHAR (`@Enumerated(STRING)`); PKs UUID (`gen_random_uuid()` nativo).
- Soft-delete (`removido_em`) em usuários e veículos — filtrar `removido_em IS NULL`.
- Migrations Flyway em `src/main/resources/db/migration/` — **nunca editar V1**; criar V4, V5...
- Em produção `ddl-auto: validate`: entidades devem bater com o schema do Flyway.
- Variáveis sensíveis via ambiente (`.env`); NUNCA commitar `.env`.

## Comandos rápidos
```bash
mvn spring-boot:run                                   # rodar local (perfil dev)
mvn test                                              # testes (requer Docker)
mvn verify -Psecurity                                 # SAST + CVE check
docker build -f docker/Dockerfile -t caladrius .      # build igual ao CI (valida compilação)
```

Leia [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) para detalhes arquiteturais.
