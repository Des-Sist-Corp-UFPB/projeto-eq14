# CALADRIUS — Agendamento de Transporte Municipal de Saúde

Projeto da disciplina **Desenvolvimento de Sistemas Corporativos** — equipe **eq14**.
**Professor**: Rodrigo Rebouças · **UFPB — Campus IV**

---

## 📦 Primeira Entrega — guia de avaliação

Esta seção aponta **onde no código** estão os itens solicitados para a avaliação:
**(1) logs de auditoria**, **(2) conexão com serviços externos** e **(3) cobertura de testes**.

### 1. Logs de auditoria

| Item | Localização |
|---|---|
| Serviço que grava os logs (categorias `SEGURANCA` / `OPERACAO` / `SISTEMA`) | [`service/AuditoriaService.java`](src/main/java/br/ufpb/dsc/caladrius/service/AuditoriaService.java) |
| Entidade + tabela | [`domain/LogAuditoria.java`](src/main/java/br/ufpb/dsc/caladrius/domain/LogAuditoria.java) · migration [`V6__criar_log_auditoria.sql`](src/main/resources/db/migration/V6__criar_log_auditoria.sql) |
| Auditoria **automática** de login/logout | [`config/AuditoriaSecurityListener.java`](src/main/java/br/ufpb/dsc/caladrius/config/AuditoriaSecurityListener.java) |
| Controller das telas | [`controller/AuditoriaController.java`](src/main/java/br/ufpb/dsc/caladrius/controller/AuditoriaController.java) |
| **Tela da trilha completa (SYSADMIN)** | rota **`GET /admin/auditoria`** |
| Tela do histórico de operação (GERENTE) | rota `GET /historico` |
| RBAC das rotas | [`config/SecurityConfig.java`](src/main/java/br/ufpb/dsc/caladrius/config/SecurityConfig.java) (`/admin/** → SYSADMIN`) |
| Testes | [`service/AuditoriaServiceTest.java`](src/test/java/br/ufpb/dsc/caladrius/service/AuditoriaServiceTest.java) (unit) · [`web/PaginasAutenticadasTest.java`](src/test/java/br/ufpb/dsc/caladrius/web/PaginasAutenticadasTest.java) (rota com sysadmin → 200) |

> ⚠️ **Para visualizar a trilha de auditoria do sistema (`/admin/auditoria`) é preciso o papel
> `SYSADMIN`.** O professor pode **criar a própria conta normalmente** (cadastro ou login com
> Google), mas o papel **SYSADMIN não é auto-concedido** — por segurança, **somente o dono do
> projeto (eq14) pode atribuí-lo** a uma conta existente. Para avaliar essa parte, **crie a conta
> e me informe o telefone/e-mail dela** que eu concedo o papel. (Sem SYSADMIN, um GERENTE ainda
> enxerga o `/historico` de operação.)

### 2. Conexão com serviços externos — Login social com Google (OAuth2 / OIDC)

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
some. *(O canal WhatsApp do `NotificacaoService` é um stub — a Evolution API é escopo futuro.)*

### 3. Cobertura de testes

- **Suíte:** `src/test/java/...` — JUnit 5 + Mockito (unitários de regra de negócio) e
  Testcontainers (integração HTTP com PostgreSQL real).
- **Rodar + gerar o relatório de cobertura (JaCoCo):**
  ```bash
  mvn verify          # requer Docker (Testcontainers)
  # relatório HTML em: target/site/jacoco/index.html
  ```
- **Estado atual:** a **camada de negócio (`service`) está em ~96%** de cobertura de linha (todos
  os serviços ≥ 86%). A cobertura **global** é de **~72%** de linha — o restante é infraestrutura
  (controllers, wiring de config/segurança, canais de notificação e o `DevSeed` de demonstração,
  que não roda no perfil de teste). **A meta de 85% global está em elevação** com testes de
  integração das demais rotas.

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
