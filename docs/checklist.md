# Checklist de Requisitos — CALADRIUS (eq14)

> Pontos levantados (mensagem de 2026-07-21) e o **estado atual** de cada um no projeto.
> Legenda: ✅ feito · 🟡 parcial · ⬜ pendente.

## Itens

- [x] **Logs, LGPD, auditoria, área administrativa** — ✅ _(com ressalva de LGPD)_
  - **Auditoria**: `AuditoriaService` + `LogAuditoria` (migration V6), tela `/historico`, categorias
    `SEGURANCA` (login/logout) e `OPERACAO` (CRUD).
  - **Área administrativa**: papel `SYSADMIN` + `/admin` (configuração de sessão dinâmica, auditoria, convites).
  - **Logs**: SLF4J/Logback; log de mensagens do WhatsApp (`mensagens_whatsapp`).
  - 🟡 **LGPD**: há os **fundamentos** (BCrypt, soft-delete, CSRF, auditoria, isolamento por dono/papel,
    consentimento no onboarding do bot); **falta** um programa formal (política de privacidade,
    exportação/eliminação de dados a pedido, gestão de consentimento).

- [x] **Integração com serviços externos** — ✅
  - **Google** OAuth/OIDC (SPEC-08) e **WhatsApp/Evolution API** (SPEC-10 — código pronto, infra pendente).
- [ ] **Pontos extras: integração com pagamento** — ⬜ não existe.

- [ ] **Testar Mercado Pago** — ⬜ depende da integração de pagamento (inexistente hoje).

- [x] **Versionamento de banco** — ✅
  - **Flyway** V1→V14, forward-only, política de checksum, banco compartilhado
    (ver `docs/sdd/02-plano-tecnico.md` §2.5).

- [ ] **Feature toggle (ligar/desligar coisas no sistema)** — 🟡 parcial
  - **Existe**: config dinâmica no banco (`ConfiguracaoSistema`/`ConfiguracaoService` + sessão dinâmica)
    e **beans condicionais por variável de ambiente** (Google `GOOGLE_CLIENT_ID`; WhatsApp
    `@ConditionalOnExpression` em `EVOLUTION_URL`/`API_KEY`; flag da SPEC-12
    `caladrius.verificacao.exigir-telefone`).
  - **Falta**: um mecanismo de *feature flags* de propósito geral (ligar/desligar features arbitrárias,
    idealmente pela área administrativa, sem redeploy).

- [ ] **Testes automatizados cobrindo ≥ 85%** — 🟡 parcial
  - **Existe**: 223 testes (JUnit 5 + Mockito + Testcontainers); **JaCoCo** configurado
    (`prepare-agent` + `report`).
  - **Falta**: regra `check` com limiar de **85%** (hoje a cobertura é relatada, mas **não é medida
    contra meta nem enforçada** no build/CI).

- [x] **Checagens de segurança e dependências** — ✅
  - `mvn verify -Psecurity`: **SpotBugs + FindSecBugs** (SAST) + **OWASP Dependency-Check** (CVEs);
    **Trivy** no compose de dev; `docs/SECURITY.md`.

## Resumo

| Situação | Itens |
|---|---|
| ✅ **Feito** | Logs/auditoria/área admin · Integração com serviços externos · Versionamento de banco · Segurança e dependências |
| 🟡 **Parcial** | LGPD formal · Feature toggle · Cobertura de testes ≥ 85% |
| ⬜ **Pendente** | Integração com pagamento (Mercado Pago) |
