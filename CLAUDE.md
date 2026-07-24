# Memória do Projeto — CALADRIUS (eq14)

## Identidade do Projeto
- **Nome**: CALADRIUS — Agendamento de Transporte Municipal de Saúde
- **Equipe**: eq14
- **Disciplina**: Desenvolvimento de Sistemas Corporativos
- **Professor**: Rodrigo Rebouças — UFPB Campus IV
- **Origem**: adaptado do boilerplate "Sistema Mercado" do professor (mesma stack e arquitetura).

## Domínio (resumo)
Pacientes solicitam transporte para consultas na cidade metropolitana mais próxima; o gestor
organiza **viagens**, **veículos**, **motoristas** e **cidades**. Sistema baseado em papéis
(RBAC): **PASSAGEIRO**, **MOTORISTA**, **GERENTE**, **SYSADMIN** (papel isolado de administração).

> **A fonte da verdade do projeto é o SDD em [`docs/sdd/`](docs/sdd/)** (Spec-Driven Development):
> constituição, especificação de produto, plano técnico, specs por feature (SPEC-01..10), ADRs,
> cenários de teste e o roadmap. **Comece por lá** ao retomar — ver a seção
> "Estado atual e como retomar" no fim deste arquivo.

**Escopo já implementado:** autenticação; CRUD de Usuários/Veículos/Cidades; SYSADMIN + `/admin`
(configuração de sessão dinâmica, auditoria, convites); onboarding por convite/token +
`NotificacaoService` (in-app/e-mail/whatsapp-stub); **viagens rotineiras/imprevistas** (linhas
programadas, painel semanal, designação, conflito, ciclo de status, visão do motorista);
**endereço estruturado do passageiro** (municípios PB) + aba de análise; redesign do shell;
**solicitação de transporte do passageiro via sistema** (linhas disponíveis + minhas viagens, com
alocação automática e isolamento — SPEC-09); **integração WhatsApp** (SPEC-10: porta
`ProvedorWhatsapp` + Evolution API, webhook, painel `/whatsapp` com QR — código pronto; **falta a
infra**: Evolution na VPS + variáveis de ambiente no deploy);
**solicitação sob demanda + onboarding pelo WhatsApp** (SPEC-11: número novo se cadastra pelo bot;
pede destino+data+horário+condições; **gestor aprova/recusa** em `/gestao/solicitacoes` e o passageiro
é notificado; "Acesso à plataforma" define senha via token). Bot desacoplado (só serviços + porta).
**Observabilidade (SPEC-14):** agente OpenTelemetry exportando **traces/métricas/logs** via OTLP ao
**backend central da disciplina** (Grafana+Tempo+Prometheus+Loki, `service.name=dsc-eq14`) + **2 spans de
negócio** manuais (`RastreamentoService` → `solicitar-sob-demanda`/`aprovar-solicitacao`); **sem migration**; liga/desliga por
env (`JAVA_TOOL_OPTIONS`) — código/infra dev+prod prontos, falta só o `.env` do servidor (token) + redeploy.
**Ainda fora do escopo:** alocação/assentos (capacidade) e prioridade automática,
escalas de motorista, perfil/CNH do motorista, integração com o WhatsApp dos motoristas.

## Stack Técnica
| Camada | Tecnologia | Versão |
|--------|-----------|--------|
| Linguagem | Java | 21 |
| Framework | Spring Boot | 3.4.5 |
| Templates | Thymeleaf + HTMX | 3.x + 2.0 |
| Frontend | Bootstrap | 5.3 |
| Banco | PostgreSQL | 16 |
| Migrations | Flyway | 11.x |
| Segurança | Spring Security | 6.x (autenticação no banco, BCrypt) |
| Testes | JUnit 5 + Mockito + Testcontainers | - |

## Estrutura de Pacotes
```
br.ufpb.dsc.caladrius
├── config/          # Security, GlobalModelAttributes, DataInitializer, SessaoConfig (sessão dinâmica),
│                    #   AuditoriaSecurityListener (login/logout), GlobalExceptionHandler
├── controller/      # Auth, Home, Veiculo, Cidade, Usuario, Viagem (+semana/designar/status),
│                    #   Linha, MotoristaViagem, Solicitacao (passageiro), Admin, Configuracao,
│                    #   Auditoria, Convite, Ativacao, Conta, Notificacao, Perfil, Analise, Ping,
│                    #   Whatsapp (painel do gerente), WhatsappWebhook (POST /webhooks/whatsapp)
├── domain/          # Usuario, Veiculo, Cidade, Viagem, LinhaProgramada, SolicitacaoViagem, Endereco,
│   │                #   Municipio, ConfiguracaoSistema, LogAuditoria, Notificacao, TokenAtivacao,
│   │                #   ConversaBot, MensagemWhatsapp
│   └── enums/       # Papel(+SYSADMIN), StatusUsuario, Tipo/StatusVeiculo, TipoCidade, StatusViagem,
│                    #   TipoViagem, StatusSolicitacao, DiaSemana, CategoriaAuditoria,
│                    #   EtapaConversa, DirecaoMensagem
├── dto/             # Records de formulário (ViagemForm, LinhaProgramadaForm, DesignacaoForm,
│                    #   EnderecoForm, PainelSemana, ...)
├── notificacao/     # CanalNotificacao (interface) + InApp/Email/Whatsapp + CanalTipo
├── observabilidade/ # RastreamentoService (spans de negócio, OTel API) + TelemetriaConfig (SPEC-14)
├── whatsapp/        # Porta ProvedorWhatsapp + EvolutionApiProvedor (adaptador), records da porta,
│   └── bot/         #   ProcessadorMensagemRecebida; bot/ = BotAtendimentoService + MensagensBot
├── exception/       # RecursoNaoEncontradoException, RegraNegocioException
├── repository/      # Interfaces Spring Data JPA
├── security/        # UsuarioAutenticado (UserDetails), CaladriusUserDetailsService
├── service/         # Lógica de negócio (@Transactional): Usuario/Veiculo/Cidade/Viagem,
│                    #   LinhaProgramada, SolicitacaoViagem, Configuracao, Auditoria, Convite,
│                    #   Notificacao, Endereco, Whatsapp (fachada: envio + log + estado da conexão)
└── util/            # Documentos (CPF, normalização de telefone, detecção de e-mail)
```

## Configuração e Acesso
- **Porta interna**: 8080 · **Perfil padrão**: `dev` · **Health check público**: `GET /ping` (200 JSON).
- **Banco dev**: `caladrius_dev` (docker-compose.dev.yml). **Banco prod**: `eq14` (compartilhado).
- **Pool de conexões em prod limitado a 5** (`application-prod.yml`) — o banco é compartilhado entre equipes.
- **Login (admin/gerente)**: telefone `83999999999` **ou** e-mail `admin@caladrius.local`, senha `admin123`
  (criado pelo `DataInitializer` na primeira execução).

## Comandos Essenciais
```bash
# Stack de dev completa (postgres + app + adminer) — atalho de 1 comando, de dentro de docker/:
cd docker && docker compose up --build -d     # usa docker/compose.yaml (inclui o dev)
#   app: http://localhost:8080 · adminer: http://localhost:8888
#   ATENÇÃO: se o volume do postgres estiver "stale" (erro de senha), recrie: docker compose down -v

# Só o banco de dev + app local fora do container
docker compose -f docker/docker-compose.dev.yml up postgres adminer
mvn spring-boot:run

# Build/testes
mvn clean package -DskipTests        # build (igual ao do Dockerfile/CI)
mvn test                             # testes (requer Docker p/ Testcontainers)
mvn verify -Psecurity                # SAST: SpotBugs + FindSecBugs + OWASP Dependency-Check

# Imagem de produção (mesmo build do CI)
docker build -f docker/Dockerfile -t caladrius:latest .
```

## Decisões Arquiteturais

### Autenticação no banco com login por e-mail OU telefone
`CaladriusUserDetailsService` detecta o formato (contém "@" → e-mail; senão → telefone, normalizado
para dígitos) e carrega o usuário do PostgreSQL. Substitui o `InMemoryUserDetailsManager` do
boilerplate. Conforme o redesenho v3 da equipe. Senhas com BCrypt.

### Login social com Google (OAuth2/OIDC) — SPEC-08
`oauth2Login` nativo do Spring Security coexiste com o `formLogin` (mantém o modelo stateful/sessão).
O `CaladriusOidcUserService` resolve a identidade em 3 passos (vínculo `identidades_oauth` →
e-mail verificado → auto-provisão de PASSAGEIRO) e devolve um `UsuarioAutenticado` — que agora também
implementa `OidcUser`, mantendo **um único tipo de principal** para ambos os fluxos. O
`ClientRegistrationRepository` é criado por um bean **condicional** a `GOOGLE_CLIENT_ID`
(`OAuth2ClientConfig`); sem a variável, o app sobe só com senha e o botão "Continuar com Google" some.
Conta criada por Google nasce com `perfil_incompleto = true` (não tem telefone, que passou a ser
nullable) e é levada a `/conta/completar` por um filtro até informar o telefone.

### Enums como VARCHAR (não enum nativo do PostgreSQL)
As colunas de enum são `VARCHAR` mapeadas com `@Enumerated(EnumType.STRING)` (valores = nomes das
enums Java, em MAIÚSCULAS, com `CHECK` nas tabelas centrais). É a opção mais robusta com o Hibernate
`ddl-auto: validate` — evita o atrito de mapear enums nativas do Postgres.

### UUID com `gen_random_uuid()` nativo
PKs são UUID. A função `gen_random_uuid()` é nativa no PostgreSQL 13+ — **não exige a extensão
pgcrypto** (evita problema de permissão no banco compartilhado da disciplina). O Hibernate gera o
UUID via `GenerationType.UUID`.

### Soft-delete
`usuarios` e `veiculos` usam `removido_em` (nunca DELETE físico); as consultas filtram
`removido_em IS NULL`. Unicidade (telefone/e-mail/CPF/placa) via índices únicos **parciais** (só
entre ativos).

### Migrations Flyway — V1 intocada
`V1__criar_tabela_produto.sql` (do boilerplate) **NÃO** é editada (já aplicada no banco compartilhado;
o Flyway compara checksum). Toda alteração futura = **nova** migration (forward-only). Estado atual:
- `V1` produto (boilerplate) · `V2` schema CALADRIUS · `V3` drop produto + seed cidades
- `V4` papel SYSADMIN · `V5` configuracoes_sistema · `V6` log_auditoria
- `V7` tokens_ativacao + notificacoes · `V8` municipios (seed PB) + enderecos (drop JSONB)
- `V9` linhas_programadas + linha_dias + evolução de viagens (tipo, FK linha, origem, horario_retorno)
- `V10` identidades_oauth (login social Google) + `usuarios.perfil_incompleto` + telefone nullable
- `V11` solicitacoes_viagem (solicitação de transporte do passageiro — SPEC-09)
- `V12` conversas_bot + mensagens_whatsapp (integração WhatsApp — SPEC-10)
- `V13` solicitação sob demanda (`solicitacoes_viagem.tipo`/`cidade_destino`, linha nullable) +
  contexto de cadastro no `conversas_bot` (SPEC-11)
- **SPEC-14 (observabilidade/OpenTelemetry) — SEM migration** (infra/config + camada de código fina; não altera schema)

> **Política em banco compartilhado:** ver [`docs/sdd/02-plano-tecnico.md` §2.5](docs/sdd/02-plano-tecnico.md).
> Migrations aditivas, sem extensões/superusuário; backup próprio (`pg_dump`) antes de alterações sensíveis.

## Convenções de Código
- Nomes/identificadores em **português** (domínio, métodos, colunas, comentários).
- Endpoints REST em português (`/veiculos`, `/viagens`...).
- Records Java para DTOs; `@Transactional(readOnly = true)` em consultas.
- Padrão HTMX: controller devolve página completa em requisição normal e **fragmento** quando há
  header `HX-Request`. Templates por módulo em `templates/{modulo}/` com `fragments/{tabela,linha,form}.html`.
- Commits no padrão Conventional Commits: `feat:`, `fix:`, `docs:`, `refactor:`.

## CI/CD (não quebrar!)
- `.github/workflows/deploy.yml` faz **build da imagem** (`docker/Dockerfile` → `mvn clean package
  -DskipTests`), publica no GHCR e implanta. **O gate é a COMPILAÇÃO** — valide com
  `docker build -f docker/Dockerfile .` antes de dar push.
- Em prod a app roda com `ddl-auto: validate`: **toda entidade JPA precisa bater com o schema do
  Flyway**, senão a app não sobe.

## Documentação Técnica
| Documento | Conteúdo |
|-----------|----------|
| **[docs/sdd/](docs/sdd/)** | **SDD — fonte da verdade**: constituição, produto, plano técnico (ADRs), specs (SPEC-01..11), [roadmap](docs/sdd/03-tarefas-e-roadmap.md), [cenários de teste](docs/sdd/cenarios-de-teste.md) |
| [README.md](README.md) | Visão geral, como rodar, acesso, estrutura |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Camadas, HTMX, Flyway |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md) | Migrations, nomenclatura, validação, Conventional Commits |
| [docs/SECURITY.md](docs/SECURITY.md) | SAST, OWASP, configuração do Spring Security |

## Estado atual e como retomar (ponto de restauração)
> **Atualizado em 2026-07-24.** Para retomar em um novo chat: **leia este arquivo + [`docs/sdd/`](docs/sdd/)**
> (em especial o [roadmap](docs/sdd/03-tarefas-e-roadmap.md), que rastreia o estado por capacidade e as
> dívidas técnicas DT-01..DT-11). Este `CLAUDE.md` é carregado automaticamente em todo chat.

- **Último marco**: **observabilidade com OpenTelemetry (SPEC-14 / ADR-17)** — agente Java (auto:
  HTTP/JDBC/JVM/**logs**) + **camada manual** (`RastreamentoService` + `TelemetriaConfig`) com **2 spans
  de negócio** (`solicitar-sob-demanda` + `aprovar-solicitacao`, atributos de domínio + logs estruturados) exportando **OTLP** ao
  **backend central** da disciplina (Grafana+Tempo+Prometheus+**Loki**, `service.name=dsc-eq14`).
  **Sem migration.** `pom.xml` ganhou só a **API** OTel (o SDK vem do agente). Agente **pinado v2.30.0**
  no `Dockerfile` — embutido mas **inerte** sem `JAVA_TOOL_OPTIONS` (RN-OBS-01, conferido na imagem);
  `grafana/otel-lgtm` local no dev. **Em produção**: ativado pelo portal da disciplina — `dsc-eq14`
  recebendo no Grafana (runbook + armadilhas da ativação na SPEC-14 §11). Antes: SPEC-11 (sob demanda + onboarding WhatsApp, V13) e SPEC-10
  (Evolution/webhook/painel `/whatsapp`). Migrations até **V13**. **Testes verdes (199)**, incl. o de
  contexto Testcontainers (V1→V13) e os de telemetria; `mvn test` e `docker build -f docker/Dockerfile .`
  verdes localmente.
- **Specs implementadas (✅)**: SPEC-01..11 — ver o status no topo de cada arquivo em `docs/sdd/specs/`.
- **Pontos de atenção / dívidas em aberto** (do roadmap):
  - **Observabilidade — ✅ em produção (SPEC-14)**: `dsc-eq14` recebendo traces/métricas/logs no Grafana
    (`otel.dsc.rodrigor.com`). Ativado pelo **portal da disciplina** (editor de `.env` que recria o
    container). Armadilhas da ativação (nome `JAVA_TOOL_OPTIONS` **singular**; **hífen** do `-javaagent`;
    diagnóstico via Dozzle no boot) documentadas na **SPEC-14 §11**.
  - **WhatsApp — infra pendente**: subir a **Evolution API na VPS da equipe** (SPEC-10 §8) e configurar
    as variáveis de ambiente do deploy (`EVOLUTION_URL`, `EVOLUTION_API_KEY`, `WHATSAPP_WEBHOOK_TOKEN`,
    `APP_URL_PUBLICA`). Sem elas, o canal opera como stub e o painel mostra "não configurada"
    (RN-WPP-02) — nada quebra. Em teste local: Evolution no Railway + túnel (cloudflared) p/ o webhook.
  - **Passageiro**: solicita via sistema ✅ (SPEC-09), via WhatsApp ✅ (SPEC-11: onboarding + sob demanda),
    e o gestor **aprova/recusa** ✅. **Falta**: **assentos/capacidade** e **prioridade automática**.
  - **Motorista**: `/minhas-viagens` (ver + status) funciona; **falta** perfil/CNH (`perfis_motorista`),
    visão de "Veículos" e **integração com o WhatsApp dos motoristas** (próxima etapa pós-SPEC-11).
  - **Home (`/`)**: ainda mostra os totais do sistema para **qualquer** papel — ajustar para esconder
    de não-gerentes e dar landing por papel.
  - **DT-03** (carga horária do motorista via `escalas_motorista`) ainda pendente.

## Próximos Passos Sugeridos
1. **Infra da SPEC-10/11**: Evolution API na VPS (docker compose + TLS) e variáveis no deploy.
2. **WhatsApp dos motoristas**: avisar o motorista da viagem designada pelo chat (reusa a porta).
3. **Assentos/capacidade** (`assentos_viagem`) + **alocação por prioridade** (Incremento B).
4. **Perfil/CNH do motorista** (`perfis_motorista`) e visão de veículos.
5. **Home por papel** (esconder totais de não-gerente; atalhos por papel).
