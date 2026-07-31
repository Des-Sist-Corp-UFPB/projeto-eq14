# CALADRIUS — Agendamento de Transporte Municipal de Saúde

Projeto da disciplina **Desenvolvimento de Sistemas Corporativos** — equipe **eq14**.
**Professor**: Rodrigo Rebouças · **UFPB — Campus IV**

---

## 📦 Guia de avaliação

Esta seção aponta **onde no código** estão os itens cobrados na avaliação:
[Log de Auditoria](#log-de-auditoria), [Integração com Serviço Externo](#integração-com-serviço-externo),
[Cobertura de Testes](#cobertura-de-testes) e o [Healthcheck e Analytics de Uso](#healthcheck-e-analytics-de-uso).

## Log de Auditoria

**O que é auditado** — autenticação (`LOGIN_SUCESSO`, `LOGIN_FALHA`, `LOGOUT`, capturados por
listener do Spring Security), operações de cadastro e ciclo de vida do domínio (usuários,
veículos, cidades, viagens, linhas), ciclo das solicitações de transporte (criação, aprovação,
recusa, cancelamento), ações administrativas (feature flags, parâmetros, convites, configuração de
sessão) e o painel WhatsApp (conexão, desconexão, teste de envio, alteração de configuração).

**Onde fica armazenado** — tabela **`log_auditoria`** (PostgreSQL), criada pela migration
[`V6__criar_log_auditoria.sql`](src/main/resources/db/migration/V6__criar_log_auditoria.sql).
Campos principais: `id` (UUID), `criado_em`, `categoria` (`SEGURANCA`/`OPERACAO`/`SISTEMA`),
`acao`, `entidade`, `entidade_id`, `detalhe`, `usuario_id`, `usuario_nome`, `ip`.

**Como foi implementado** — *service* dedicado (`AuditoriaService`) chamado pelos serviços de
domínio, mais um **listener** de eventos de autenticação para o que é automático. A leitura é
centralizada em `AuditoriaService.listar`, e a etiqueta de área de cada evento é **derivada** da
ação/entidade pelo enum `AreaSistema` — sem coluna nova no banco.

**Quais classes/arquivos participam:**

| Item | Localização |
|---|---|
| Serviço que grava os logs (categorias `SEGURANCA` / `OPERACAO` / `SISTEMA`) | [`service/AuditoriaService.java`](src/main/java/br/ufpb/dsc/caladrius/service/AuditoriaService.java) |
| Entidade + tabela | [`domain/LogAuditoria.java`](src/main/java/br/ufpb/dsc/caladrius/domain/LogAuditoria.java) · migration [`V6__criar_log_auditoria.sql`](src/main/resources/db/migration/V6__criar_log_auditoria.sql) |
| Repositório (consultas da trilha) | [`repository/LogAuditoriaRepository.java`](src/main/java/br/ufpb/dsc/caladrius/repository/LogAuditoriaRepository.java) |
| Auditoria **automática** de login/logout | [`config/AuditoriaSecurityListener.java`](src/main/java/br/ufpb/dsc/caladrius/config/AuditoriaSecurityListener.java) |
| Etiqueta de área por evento (derivada, sem coluna nova) | [`domain/enums/AreaSistema.java`](src/main/java/br/ufpb/dsc/caladrius/domain/enums/AreaSistema.java) |
| Controllers das telas | [`controller/AuditoriaController.java`](src/main/java/br/ufpb/dsc/caladrius/controller/AuditoriaController.java) · [`controller/LogController.java`](src/main/java/br/ufpb/dsc/caladrius/controller/LogController.java) |
| **Central de logs (GERENTE/SYSADMIN)** | rota **`GET /logs`** — trilha inteira, com filtro por área e busca |
| **Tela da trilha completa (SYSADMIN)** | rota **`GET /admin/auditoria`** |
| Tela do histórico de solicitações (GERENTE) | rota `GET /historico` |
| RBAC das rotas | [`config/SecurityConfig.java`](src/main/java/br/ufpb/dsc/caladrius/config/SecurityConfig.java) (`/admin/** → SYSADMIN`) |
| Testes | [`service/AuditoriaServiceTest.java`](src/test/java/br/ufpb/dsc/caladrius/service/AuditoriaServiceTest.java) (unit) · [`domain/enums/AreaSistemaTest.java`](src/test/java/br/ufpb/dsc/caladrius/domain/enums/AreaSistemaTest.java) · [`web/LogControllerTest.java`](src/test/java/br/ufpb/dsc/caladrius/web/LogControllerTest.java) · [`web/PaginasAutenticadasTest.java`](src/test/java/br/ufpb/dsc/caladrius/web/PaginasAutenticadasTest.java) |

> ⚠️ **Para visualizar a trilha de auditoria do sistema (`/admin/auditoria`) é preciso o papel
> `SYSADMIN`.** O professor pode **criar a própria conta normalmente** (cadastro ou login com
> Google), mas o papel **SYSADMIN não é auto-concedido** — por segurança, **somente o dono do
> projeto (eq14) pode atribuí-lo** a uma conta existente. Para avaliar essa parte, **crie a conta
> e me informe o telefone/e-mail dela** que eu concedo o papel. (Sem SYSADMIN, um GERENTE ainda
> enxerga o `/historico` de operação.)

## Integração com Serviço Externo

O sistema integra **quatro** serviços externos. O principal, para efeito desta avaliação, é o
**login social com Google (OAuth2/OIDC)** — em produção. Todos seguem o mesmo padrão: o bean da
integração só é criado quando as variáveis de ambiente existem; sem elas a aplicação sobe idêntica.

| Serviço externo | Para que é usado | Configuração (variáveis) | Estado |
|---|---|---|---|
| **Google (OAuth2/OIDC)** | Login social — "Continuar com Google" | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | ✅ em produção |
| **Evolution API (WhatsApp)** | Envio de avisos e **bot de atendimento** (solicitação de viagem pelo WhatsApp) | `EVOLUTION_URL`, `EVOLUTION_API_KEY`, `EVOLUTION_INSTANCIA`, `WHATSAPP_WEBHOOK_TOKEN`, `APP_URL_PUBLICA` | ✅ código · 🟡 infra |
| **OpenTelemetry / Grafana da disciplina** | Exportação de traces, métricas e logs | `OTEL_*`, `JAVA_TOOL_OPTIONS` | ✅ em produção |
| **Umami** | Analytics de uso das páginas | `UMAMI_URL`, `UMAMI_WEBSITE_ID`, `UMAMI_DOMINIOS` | ✅ código · 🟡 variáveis |

> Nenhum segredo fica no repositório: todos vêm do `.env` do servidor (ver
> [`.env.example`](.env.example)). O PostgreSQL da disciplina **não** conta como integração externa.

### Serviço externo principal — Login social com Google (OAuth2 / OIDC)

| Item | Localização |
|---|---|
| Registro do cliente OAuth (bean condicional a `GOOGLE_CLIENT_ID`/`SECRET`) | [`config/OAuth2ClientConfig.java`](src/main/java/br/ufpb/dsc/caladrius/config/OAuth2ClientConfig.java) |
| Ativação do `oauth2Login` na cadeia de segurança | [`config/SecurityConfig.java`](src/main/java/br/ufpb/dsc/caladrius/config/SecurityConfig.java) |
| Resolução da identidade Google (vínculo → e-mail verificado → auto-provisão) | [`security/CaladriusOidcUserService.java`](src/main/java/br/ufpb/dsc/caladrius/security/CaladriusOidcUserService.java) |
| Vínculo conta ↔ provedor | [`domain/IdentidadeOauth.java`](src/main/java/br/ufpb/dsc/caladrius/domain/IdentidadeOauth.java) · [`service/IdentidadeOauthService.java`](src/main/java/br/ufpb/dsc/caladrius/service/IdentidadeOauthService.java) · migration [`V10__criar_identidades_oauth.sql`](src/main/resources/db/migration/V10__criar_identidades_oauth.sql) |
| Botão "Continuar com Google" | [`templates/auth/login.html`](src/main/resources/templates/auth/login.html) → `GET /oauth2/authorization/google` |
| Especificação | [`docs/sdd/specs/SPEC-08-login-social-google.md`](docs/sdd/specs/SPEC-08-login-social-google.md) |
| Teste | [`service/IdentidadeOauthServiceTest.java`](src/test/java/br/ufpb/dsc/caladrius/service/IdentidadeOauthServiceTest.java) |

O login social está **ativo em produção** (`https://eq14.dsc.rodrigor.com`). Sem as variáveis
`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`, a aplicação sobe normalmente só com senha e o botão
some.

### Segundo serviço externo — WhatsApp via Evolution API

| Item | Localização |
|---|---|
| Porta (interface do provedor) | [`whatsapp/ProvedorWhatsapp.java`](src/main/java/br/ufpb/dsc/caladrius/whatsapp/ProvedorWhatsapp.java) |
| Adaptador HTTP da Evolution | [`whatsapp/EvolutionApiProvedor.java`](src/main/java/br/ufpb/dsc/caladrius/whatsapp/EvolutionApiProvedor.java) |
| Bean condicional às variáveis | [`config/WhatsappConfig.java`](src/main/java/br/ufpb/dsc/caladrius/config/WhatsappConfig.java) |
| Fachada (envio + log + estado) | [`service/WhatsappService.java`](src/main/java/br/ufpb/dsc/caladrius/service/WhatsappService.java) |
| Webhook de recebimento | [`controller/WhatsappWebhookController.java`](src/main/java/br/ufpb/dsc/caladrius/controller/WhatsappWebhookController.java) → `POST /webhooks/whatsapp` |
| Bot de atendimento | [`whatsapp/bot/BotAtendimentoService.java`](src/main/java/br/ufpb/dsc/caladrius/whatsapp/bot/BotAtendimentoService.java) |
| Especificações | [SPEC-10](docs/sdd/specs/SPEC-10-integracao-whatsapp.md) · [SPEC-11](docs/sdd/specs/SPEC-11-solicitacao-sob-demanda-e-onboarding-whatsapp.md) |

## Cobertura de Testes

- **Suíte:** `src/test/java/...` — JUnit 5 + Mockito (unitários de regra de negócio) e
  Testcontainers (integração HTTP contra um PostgreSQL real).
- **Gate no build:** `jacoco:check` **falha o build abaixo de 85%** de cobertura de linhas
  (configurado no [`pom.xml`](pom.xml)) — a meta não depende de disciplina manual.
- **Relatório commitado (exigência da avaliação):** [`cobertura/jacoco/index.html`](cobertura/jacoco/index.html)
  — abra este arquivo no navegador; o resumo por pacote também está em `cobertura/jacoco/jacoco.csv`.
- **Como gerar/atualizar o relatório:**
  ```bash
  mvn clean test jacoco:report     # requer Docker (Testcontainers)
  rm -rf cobertura && mkdir -p cobertura && cp -r target/site/jacoco cobertura/
  ```
  Sem Java/Maven na máquina, o mesmo build roda em container:
  ```bash
  docker run --rm -v "$PWD":/app -w /app -v caladrius-m2:/root/.m2 \
    -v /var/run/docker.sock:/var/run/docker.sock --network host \
    maven:3.9.9-eclipse-temurin-21 mvn -B clean test jacoco:report
  ```
- **Percentual total: `88,0%` de cobertura de linhas** (3009 de 3419; 88,2% de instruções,
  71,2% de ramos), com **393 testes — 0 falhas, 0 erros**. Medição de **2026-07-31**, com o
  relatório commitado em `cobertura/`.

| Pacote | Linhas | | Pacote | Linhas |
|---|---:|---|---|---:|
| `observabilidade` | 100,0% | | `whatsapp` | 87,2% |
| `health` | 100,0% | | `config` | 84,0% |
| `dto` | 100,0% | | `domain` | 82,6% |
| `util` | 97,1% | | `controller` | 81,4% |
| `domain.enums` | 97,0% | | `whatsapp.bot` | 81,1% |
| `service` | 95,1% | | `security` | 75,0% |
| `notificacao` | 89,7% | | `exception` | 66,7% |

  Os cenários por camada estão documentados em [`docs/testes/`](docs/testes/).

## Healthcheck e Analytics de Uso

**Healthcheck que consulta o banco** ([SPEC-17](docs/sdd/specs/SPEC-17-healthcheck-de-banco-e-analytics-de-uso.md)) —
a verificação é código do projeto, não delegada ao framework:

| Item | Localização |
|---|---|
| Indicador que **executa `SELECT 1`** com timeout | [`health/BancoHealthIndicator.java`](src/main/java/br/ufpb/dsc/caladrius/health/BancoHealthIndicator.java) |
| Endpoint público com o estado do banco | [`controller/PingController.java`](src/main/java/br/ufpb/dsc/caladrius/controller/PingController.java) → `GET /ping` |
| Timeout e visibilidade dos componentes | [`application.yml`](src/main/resources/application.yml) (`caladrius.health.banco.timeout-segundos`, `show-components`) |
| Testes | [`health/BancoHealthIndicatorTest.java`](src/test/java/br/ufpb/dsc/caladrius/health/BancoHealthIndicatorTest.java) · [`web/PaginasPublicasTest.java`](src/test/java/br/ufpb/dsc/caladrius/web/PaginasPublicasTest.java) |

```bash
curl -s https://eq14.dsc.rodrigor.com/ping
# {"status":"ok","service":"eq14","database":"up","timestamp":"..."}

curl -s https://eq14.dsc.rodrigor.com/actuator/health
# {"status":"UP","components":{"banco":{"status":"UP"}, ...}}
```

O `/ping` **responde 200 mesmo com o banco fora** (é o contrato de *liveness* da disciplina) e
reporta a falha no campo `database`; quem devolve **503** é o `/actuator/health`, usado pelo
healthcheck do container.

**Analytics de uso (Umami)** — instância central da disciplina (`umami.dsc.rodrigor.com`),
sem cookies e sem dado pessoal:

| Item | Localização |
|---|---|
| Fragmento do rastreador | [`templates/fragments/analytics.html`](src/main/resources/templates/fragments/analytics.html) |
| Configuração por ambiente | [`dto/ConfiguracaoUmami.java`](src/main/java/br/ufpb/dsc/caladrius/dto/ConfiguracaoUmami.java) · [`config/GlobalModelAttributes.java`](src/main/java/br/ufpb/dsc/caladrius/config/GlobalModelAttributes.java) |
| Testes | [`dto/ConfiguracaoUmamiTest.java`](src/test/java/br/ufpb/dsc/caladrius/dto/ConfiguracaoUmamiTest.java) · [`web/AnalyticsUmamiWebTest.java`](src/test/java/br/ufpb/dsc/caladrius/web/AnalyticsUmamiWebTest.java) |

> 🔒 O rastreador usa `data-exclude-search` e é **deliberadamente omitido** em `/ativar` e
> `/verificar-email`, que recebem token pela URL — sem isso, um token válido de definição de senha
> seria enviado ao servidor de analytics.

---

## Sobre o projeto

O **CALADRIUS** organiza as solicitações de transporte de pacientes que precisam ir a
consultas na cidade metropolitana mais próxima. A plataforma permite ao gestor cadastrar
**usuários** (passageiros, motoristas e gerentes), **veículos** da frota, **cidades**
(origem e destinos) e planejar **viagens** — associando veículo, motorista e destino.

Os papéis do sistema (RBAC):

| Papel | Pode |
|---|---|
| **Passageiro** | Cadastrar-se e solicitar transporte (evolução futura) |
| **Motorista** | Visualizar suas viagens (evolução futura) |
| **Gerente** | Controle total: usuários, veículos, cidades e viagens |

> **Autenticação flexível:** o usuário pode entrar com **e-mail _ou_ telefone** (o sistema
> detecta o formato automaticamente). O telefone é obrigatório no cadastro; o e-mail é
> opcional e também serve para login e recuperação de conta.

> A integração com o WhatsApp (Evolution API) faz parte do escopo de longo prazo, mas
> **não está incluída** neste incremento — o foco atual é o CRUD e a autenticação.

Este projeto foi adaptado do boilerplate da disciplina (Spring Boot), mantendo toda a
arquitetura em camadas (Controller → Service → Repository), o padrão **HTMX + Thymeleaf**
e o pipeline de CI/CD.

---

## Tecnologias

| Camada | Tecnologia |
|--------|-----------|
| Backend | Java 21 + Spring Boot 3.4.5 |
| Templates | Thymeleaf + HTMX 2.0 |
| Frontend | Bootstrap 5.3 |
| Banco | PostgreSQL 16 |
| Migrações | Flyway |
| Segurança | Spring Security 6 (autenticação no banco, BCrypt) |
| Build | Maven 3.9 · CI/CD GitHub Actions |

---

## Como rodar (desenvolvimento)

Pré-requisitos: **Java 21**, **Maven 3.9+** e **Docker**.

```bash
# 1) Suba apenas o banco de desenvolvimento (PostgreSQL + Adminer)
docker compose -f docker/docker-compose.dev.yml up postgres adminer

# 2) Em outro terminal, rode a aplicação (perfil dev)
mvn spring-boot:run
```

Ou tudo de uma vez (banco + app + adminer):

```bash
docker compose -f docker/docker-compose.dev.yml up --build
```

### Acesso local

| O que | Endereço |
|-------|----------|
| Aplicação | http://localhost:8080 |
| Health check público | http://localhost:8080/ping |
| Adminer (banco) | http://localhost:8888 |

**Login do administrador (gerente)** — criado automaticamente na primeira execução:

| Campo | Valor |
|-------|-------|
| Telefone | `83999999999` |
| E-mail | `admin@caladrius.local` |
| Senha | `admin123` |

> Entre com o telefone **ou** o e-mail acima. Troque essas credenciais em produção.

---

## Estrutura

```
src/main/java/br/ufpb/dsc/caladrius/
├── config/        # SecurityConfig, DataInitializer (seed admin), handlers
├── controller/    # Controllers MVC + HTMX (Ping, Auth, Home, Veiculo, Cidade, Usuario, Viagem)
├── domain/        # Entidades JPA (Usuario, Veiculo, Cidade, Viagem) + enums
├── dto/           # Records de formulário (Bean Validation)
├── exception/     # Exceções de domínio
├── repository/    # Interfaces Spring Data JPA
├── security/      # UserDetailsService no banco (login por e-mail/telefone)
├── service/       # Lógica de negócio (@Transactional)
└── util/          # Validação de CPF, normalização de telefone/e-mail

src/main/resources/
├── db/migration/  # Flyway: V1 (boilerplate), V2 (schema CALADRIUS), V3 (limpeza + cidades)
└── templates/     # Thymeleaf (auth, inicio, veiculos, cidades, usuarios, viagens)
```

---

## Testes

```bash
mvn test          # requer Docker (Testcontainers sobe um PostgreSQL real)
```

Cobrem o carregamento do contexto (migrações + validação do schema + seed) e a lógica dos
services (`VeiculoService`, `UsuarioService`).

---

## CI/CD e Deploy

O `.github/workflows/deploy.yml` constrói a imagem Docker (`docker/Dockerfile`), publica no
GHCR e implanta no servidor da disciplina a cada `push` na `main`. A aplicação roda atrás de
um proxy (Caddy) e conecta ao **PostgreSQL compartilhado** da disciplina — por isso o pool de
conexões é limitado a 5 (`application-prod.yml`).

Documentação técnica complementar em [`docs/`](docs/) e na [CLAUDE.md](CLAUDE.md).
