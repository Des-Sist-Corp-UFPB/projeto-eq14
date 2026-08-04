# Checklist de Requisitos — CALADRIUS (eq14)

> Pontos levantados (mensagem de 2026-07-21) e o **estado atual** de cada um no projeto.
> Legenda: ✅ feito · 🟡 parcial · ⬜ pendente. **Atualizado em 2026-07-29.**

## Itens

- [x] **Logs, LGPD, auditoria, área administrativa** — ✅ _(com ressalva de LGPD)_
  - **Auditoria**: `AuditoriaService` + `LogAuditoria` (migration V6), tela `/historico`, categorias
    `SEGURANCA` (login/logout), `OPERACAO` (CRUD) e `SISTEMA` (configuração/feature flags).
  - **Área administrativa**: papel `SYSADMIN` + `/admin` (configuração de sessão dinâmica, auditoria,
    convites e — novo — **funcionalidades**/feature toggle).
  - **Logs**: SLF4J/Logback + envio ao **Loki** via OpenTelemetry (SPEC-OPE-01); log de mensagens do
    WhatsApp (`mensagens_whatsapp`).
  - 🟡 **LGPD**: há os **fundamentos** (BCrypt, soft-delete, CSRF, auditoria, isolamento por dono/papel,
    consentimento no onboarding do bot); **falta** um programa formal (política de privacidade,
    exportação/eliminação de dados a pedido, gestão de consentimento).

- [x] **Integração com serviços externos** — ✅
  - **Google** OAuth/OIDC (SPEC-ACE-02) e **WhatsApp/Evolution API** (SPEC-WPP-01 — código pronto, infra
    pendente: falta subir a Evolution na VPS e preencher `EVOLUTION_URL`, `EVOLUTION_API_KEY`,
    `WHATSAPP_WEBHOOK_TOKEN`, `APP_URL_PUBLICA` no `.env` do servidor — ver `.env.example`).
  - **OpenTelemetry** → backend central da disciplina (SPEC-OPE-01, em produção).

- [ ] **Pontos extras: integração com pagamento** — ⬜ **especificado, não implementado**
  - [SPEC-PLT-03](sdd/specs/plataforma/SPEC-PLT-03-organizacao-planos-e-pagamento.md) (proposta): cadastro de gestor com
    escolha de plano → checkout → o papel `GERENTE` só é concedido pela **confirmação
    servidor-a-servidor** do pagamento (RN-PAG-01). Entidades `Organizacao`/`Assinatura`/`Pagamento`.
  - **Base já pronta**: o *entitlement* por município (`municipios.pagamento_habilitado`, V15) que a
    integração vai consultar (SPEC-PLT-01, RN-FLG-05).

- [ ] **Testar Mercado Pago** — ⬜ depende da SPEC-PLT-03 (Checkout Pro + sandbox + webhook assinado).

- [x] **Versionamento de banco** — ✅
  - **Flyway** V1→**V15**, forward-only, política de checksum, banco compartilhado
    (ver `docs/sdd/02-plano-tecnico.md` §2.5).

- [x] **Feature toggle (ligar/desligar coisas no sistema)** — ✅ **implementado**
  - **[SPEC-PLT-01](sdd/specs/plataforma/SPEC-PLT-01-feature-toggle.md)** (ADR-17): `FeatureFlagService` sobre a
    `configuracoes_sistema` (sem tabela nova), com **cache**, **default seguro** e **auditoria**.
  - **Kill switches**: `feature.bot_whatsapp` (webhook registra a mensagem e não aciona o bot) e
    `feature.modo_manutencao` (`ManutencaoFilter` → 503 para todos menos o SYSADMIN; `/ping` e o
    health check ficam imunes).
  - **Parâmetros de negócio** (`param.*`): validade/tentativas/cooldown do OTP, validade do convite e
    tamanho mínimo de senha, editáveis em `/admin/features` com validação de intervalo.
  - **Entitlement por município**: `municipios.pagamento_habilitado` (**migration V15**) + tela
    `/admin/municipios`.
  - **Continuam valendo** os toggles por ambiente que já existiam: Google (`GOOGLE_CLIENT_ID`),
    WhatsApp (`EVOLUTION_URL`/`API_KEY`) e OpenTelemetry (`JAVA_TOOL_OPTIONS`).

- [x] **Testes automatizados cobrindo ≥ 85%** — ✅ **atingido e enforçado**
  - **346 testes** (JUnit 5 + Mockito + Testcontainers), 0 falhas.
  - **87,3% de linhas** · 87,5% de instruções · 86,8% de métodos · 69,7% de ramos.
  - **Gate no build**: `jacoco:check` no `mvn verify` **falha** abaixo de **85%** (linhas e
    instruções) e **65%** (ramos). Exclusões declaradas: `DevSeed` (dados de exemplo, `@Profile("dev")`)
    e `CaladriusApplication` (só o `main`).
  - **Documentação dos cenários**: [`docs/testes/`](testes/) — um arquivo por área, cada teste com o
    seu propósito.

- [x] **Checagens de segurança e dependências** — ✅
  - `mvn verify -Psecurity`: **SpotBugs + FindSecBugs** (SAST) + **OWASP Dependency-Check** (CVEs);
    **Trivy** no compose de dev; `docs/SECURITY.md`.

## Resumo

| Situação | Itens |
|---|---|
| ✅ **Feito** | Logs/auditoria/área admin · Integração com serviços externos · Versionamento de banco · **Feature toggle** · **Cobertura ≥ 85% (com gate)** · Segurança e dependências |
| 🟡 **Parcial** | LGPD formal · WhatsApp (código pronto, **infra** da Evolution pendente) |
| ⬜ **Pendente** | Integração com pagamento / Mercado Pago — **especificado** na [SPEC-PLT-03](sdd/specs/plataforma/SPEC-PLT-03-organizacao-planos-e-pagamento.md), aguardando aprovação das decisões em aberto |
