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
> constituição, especificação de produto, plano técnico, specs por área (`acesso/`, `cadastros/`,
> `viagens/`, `whatsapp/`, `plataforma/`, `operacao/` — ver o índice em [`docs/sdd/README.md`](docs/sdd/README.md)), ADRs,
> cenários de teste e o roadmap. **Comece por lá** ao retomar — ver a seção
> "Estado atual e como retomar" no fim deste arquivo.

**Escopo já implementado:** autenticação; CRUD de Usuários/Veículos/Cidades; SYSADMIN + `/admin`
(configuração de sessão dinâmica, auditoria, convites); onboarding por convite/token +
`NotificacaoService` (in-app/e-mail/whatsapp-stub); **viagens rotineiras/imprevistas** (linhas
programadas, painel semanal, designação, conflito, ciclo de status, visão do motorista);
**endereço estruturado do passageiro** (municípios PB) + aba de análise; redesign do shell;
**solicitação de transporte do passageiro via sistema** (linhas disponíveis + minhas viagens, com
alocação automática e isolamento — SPEC-VIA-03); **integração WhatsApp** (SPEC-WPP-01: porta
`ProvedorWhatsapp` + Evolution API, webhook, painel `/whatsapp` com QR — código pronto; **falta a
infra**: Evolution na VPS + variáveis de ambiente no deploy);
**solicitação sob demanda + onboarding pelo WhatsApp** (SPEC-WPP-02: número novo se cadastra pelo bot;
pede destino+data+horário+condições; **gestor aprova/recusa** em `/gestao/solicitacoes` e o passageiro
é notificado; "Acesso à plataforma" define senha via token). Bot desacoplado (só serviços + porta).
**Observabilidade (SPEC-OPE-01):** agente OpenTelemetry exportando **traces/métricas/logs** via OTLP ao
**backend central da disciplina** (Grafana+Tempo+Prometheus+Loki, `service.name=dsc-eq14`) + **2 spans de
negócio** manuais (`RastreamentoService` → `solicitar-sob-demanda`/`aprovar-solicitacao`); **sem migration**; liga/desliga por
env (`JAVA_TOOL_OPTIONS`) — em produção.
**Feature toggle (SPEC-PLT-01):** `FeatureFlagService` sobre `configuracoes_sistema` (cache + default
seguro + auditoria); **kill switches** `feature.bot_whatsapp` e `feature.modo_manutencao`
(`ManutencaoFilter` → 503, libera SYSADMIN e `/ping`); **parâmetros de negócio** (`param.*`, lidos por
`VerificacaoService`/`ConviteService`); **entitlement** `municipios.pagamento_habilitado` (**V15**);
telas `/admin/features` e `/admin/municipios`.
**Central de logs (`/logs`):** item de 1º nível no menu (GERENTE/SYSADMIN) com a trilha inteira,
**etiqueta de área** por evento (`AreaSistema` — derivada da ação/entidade, **sem coluna nova**),
filtro por área e busca livre. `/historico` ficou só com o ciclo das **solicitações**. Painel WhatsApp
e solicitações passaram a ser auditados (antes não deixavam rastro).
**Multi-tenancy — fase 1 (SPEC-PLT-02 / ADR-21 e ADR-22):** plano de controle com `organizacoes` e
`vinculos` (**V16**), `ContextoTenant`/`ContextoTenantFilter` (organização da requisição, limpa em
`finally`) e a tela `/entrar/onde` (quem tem vínculo com mais de uma secretaria, ou mais de um papel,
escolhe por onde entra). **Aditiva e inerte em produção**: sem vínculo — que é o caso de todas as
contas hoje — nada muda. **Falta a fase 2**: dividir `usuarios` em identidade + membro, schema por
tenant (`search_path`) e provisionamento automático.
**Ainda fora do escopo:** alocação/assentos (capacidade) e prioridade automática,
escalas de motorista, perfil/CNH do motorista, integração com o WhatsApp dos motoristas,
**pagamento/organização** (especificado na SPEC-PLT-03, não implementado).

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
│                    #   Auditoria, Log (central /logs), Feature (/admin/features + municipios),
│                    #   Manutencao, Convite, Ativacao, Conta, Notificacao, Perfil, Analise, Ping,
│                    #   Contexto (/entrar/onde — escolha de secretaria/papel),
│                    #   Whatsapp (painel do gerente), WhatsappWebhook (POST /webhooks/whatsapp)
├── domain/          # Usuario, Veiculo, Cidade, Viagem, LinhaProgramada, SolicitacaoViagem, Endereco,
│   │                #   Organizacao, Vinculo (multi-tenancy — SPEC-PLT-02),
│   │                #   Municipio, ConfiguracaoSistema, LogAuditoria, Notificacao, TokenAtivacao,
│   │                #   ConversaBot, MensagemWhatsapp
│   └── enums/       # Papel(+SYSADMIN), StatusUsuario, Tipo/StatusVeiculo, TipoCidade, StatusViagem,
│                    #   TipoViagem, StatusSolicitacao, DiaSemana, CategoriaAuditoria,
│                    #   AreaSistema (etiqueta da central de logs), EtapaConversa, DirecaoMensagem,
│                    #   StatusOrganizacao, StatusVinculo
├── dto/             # Records de formulário (ViagemForm, LinhaProgramadaForm, DesignacaoForm,
│                    #   EnderecoForm, PainelSemana, ...)
├── multitenancia/   # ContextoTenant (organização da requisição) + ContextoTenantFilter (SPEC-PLT-02)
├── notificacao/     # CanalNotificacao (interface) + InApp/Email/Whatsapp + CanalTipo
├── observabilidade/ # RastreamentoService (spans de negócio, OTel API) + TelemetriaConfig (SPEC-OPE-01)
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

### Login social com Google (OAuth2/OIDC) — SPEC-ACE-02
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
- `V11` solicitacoes_viagem (solicitação de transporte do passageiro — SPEC-VIA-03)
- `V12` conversas_bot + mensagens_whatsapp (integração WhatsApp — SPEC-WPP-01)
- `V13` solicitação sob demanda (`solicitacoes_viagem.tipo`/`cidade_destino`, linha nullable) +
  contexto de cadastro no `conversas_bot` (SPEC-WPP-02)
- `V14` verificação de contato e recuperação de senha (`codigos_verificacao`, `tokens_ativacao.finalidade`,
  `usuarios.telefone_verificado_em`/`email_verificado_em`) — SPEC-ACE-03
- `V15` feature toggle: `municipios.pagamento_habilitado` (entitlement) — SPEC-PLT-01. As **flags** e os
  **parâmetros** NÃO têm schema: são linhas chave/valor em `configuracoes_sistema` (V5)
- `V16` **`organizacoes` + `vinculos`** — multi-tenancy fase 1 (SPEC-PLT-02 / ADR-21 e ADR-22).
  **Aditiva**: nenhuma tabela existente muda. Sem vínculo, a app opera no **tenant legado** (o
  comportamento de hoje). A divisão de `usuarios` e o schema por tenant são a **fase 2**
- **SPEC-OPE-01 (observabilidade/OpenTelemetry) — SEM migration** (infra/config + camada de código fina; não altera schema)

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
| **[docs/sdd/](docs/sdd/)** | **SDD — fonte da verdade**: constituição, produto, plano técnico (ADRs), specs **por área** em [`specs/`](docs/sdd/specs/), [roadmap](docs/sdd/03-tarefas-e-roadmap.md), [cenários de teste](docs/sdd/cenarios-de-teste.md) |
| **[docs/testes/](docs/testes/)** | **Suíte de testes documentada**: o que cada cenário verifica e por quê, cobertura por camada e como rodar |
| [docs/checklist.md](docs/checklist.md) | Requisitos cobrados na disciplina × estado atual |
| [README.md](README.md) | Visão geral, como rodar, acesso, estrutura |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Camadas, HTMX, Flyway |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md) | Migrations, nomenclatura, validação, Conventional Commits |
| [docs/SECURITY.md](docs/SECURITY.md) | SAST, OWASP, configuração do Spring Security |

## Estado atual e como retomar (ponto de restauração)
> **Atualizado em 2026-07-29.** Para retomar em um novo chat: **leia este arquivo + [`docs/sdd/`](docs/sdd/)**
> (em especial o [roadmap](docs/sdd/03-tarefas-e-roadmap.md), que rastreia o estado por capacidade e as
> dívidas técnicas DT-01..DT-18) e o [`docs/checklist.md`](docs/checklist.md). Este `CLAUDE.md` é
> carregado automaticamente em todo chat.

- **Último marco**: **multi-tenancy, fase 1 (SPEC-PLT-02 / ADR-21 e ADR-22)** — `organizacoes` +
  `vinculos` (**V16**), `OrganizacaoService` (slug único e normalizado), `VinculoService` (solicitar
  idempotente, aprovar, revogar, **isolamento por dono com 404**), `multitenancia.ContextoTenant` +
  `ContextoTenantFilter` e a tela `/entrar/onde`. **24 testes novos**; a decisão de arquitetura passou
  da Opção A (silo) para a **B2** — schema por tenant + plano de controle no `public`. **Fase 2 (o
  trabalho pesado) está aberta**: dividir `usuarios` em `identidades` + `membros`, ligar a
  multi-tenancy do Hibernate (`search_path`), trilha Flyway `tenant/` e provisionamento — com o
  **teste de isolamento entre dois schemas** como critério de saída.
- **Marco anterior**: **feature toggle (SPEC-PLT-01 / ADR-17)** — `FeatureFlagService` (fachada sobre
  `ConfiguracaoService`, com **cache**, **default seguro** e **auditoria**), catálogos `ChaveFeature` e
  `ParametroSistema`, `ManutencaoFilter` (503 + página pública, libera SYSADMIN e o contrato `/ping`),
  gate do bot no webhook, `MunicipioService` (entitlement) e telas `/admin/features` + `/admin/municipios`.
  **Migration V15** (só `municipios.pagamento_habilitado`). **Cobertura de testes com gate**: `jacoco:check`
  falha abaixo de **85%** — hoje **422 testes verdes**, **88,0% de linhas**. Cenários documentados em
  **[`docs/testes/`](docs/testes/)**. Antes: SPEC-OPE-01 (OpenTelemetry, em produção), SPEC-ACE-03 (V14),
  SPEC-WPP-02/10 (WhatsApp). Migrations até **V15**; teste de contexto Testcontainers cobre V1→V15.
- **Como rodar o build sem Java na máquina** (é o que valida antes do push):
  `docker run --rm -v "$PWD":/app -w /app -v caladrius-m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock --network host maven:3.9.9-eclipse-temurin-21 mvn -B verify`
- **Specs implementadas (✅)**: tudo em `acesso/`, `cadastros/`, `viagens/`, `whatsapp/` e `operacao/`,
  mais a **SPEC-PLT-01** (feature toggle). **SPEC-PLT-02** = proposta + protótipo (multi-ambiente,
  Opção A). **SPEC-PLT-03** = proposta (organização/planos/pagamento) — ver o status no topo de cada
  arquivo em `docs/sdd/specs/`.
- **Pontos de atenção / dívidas em aberto** (do roadmap):
  - **Observabilidade — ✅ em produção (SPEC-OPE-01)**: `dsc-eq14` recebendo traces/métricas/logs no Grafana
    (`otel.dsc.rodrigor.com`). Ativado pelo **portal da disciplina** (editor de `.env` que recria o
    container). Armadilhas da ativação (nome `JAVA_TOOL_OPTIONS` **singular**; **hífen** do `-javaagent`;
    diagnóstico via Dozzle no boot) documentadas na **SPEC-OPE-01 §11**.
  - **WhatsApp — infra pendente**: subir a **Evolution API na VPS da equipe** (SPEC-WPP-01 §8) e configurar
    as variáveis de ambiente do deploy (`EVOLUTION_URL`, `EVOLUTION_API_KEY`, `WHATSAPP_WEBHOOK_TOKEN`,
    `APP_URL_PUBLICA`). Sem elas, o canal opera como stub e o painel mostra "não configurada"
    (RN-WPP-02) — nada quebra. Em teste local: Evolution no Railway + túnel (cloudflared) p/ o webhook.
  - **Passageiro**: solicita via sistema ✅ (SPEC-VIA-03), via WhatsApp ✅ (SPEC-WPP-02: onboarding + sob demanda),
    e o gestor **aprova/recusa** ✅. **Falta**: **assentos/capacidade** e **prioridade automática**.
  - **Motorista**: `/minhas-viagens` (ver + status) funciona; **falta** perfil/CNH (`perfis_motorista`),
    visão de "Veículos" e **integração com o WhatsApp dos motoristas** (próxima etapa pós-SPEC-WPP-02).
  - **Home (`/`)**: ainda mostra os totais do sistema para **qualquer** papel (**DT-17**) — ajustar
    para esconder de não-gerentes e dar landing por papel.
  - **DT-03** (carga horária do motorista via `escalas_motorista`) ainda pendente.
  - **DT-12**: **sem lockout do login por senha** (o OTP tem trava, o `formLogin` não).
  - **DT-16**: horário de atendimento do WhatsApp é salvo mas **não aplicado** — agora dá para
    resolver como parâmetro da SPEC-PLT-01.
  - **Pagamento/organização (SPEC-PLT-03)**: **proposta escrita, com decisões em aberto** (planos/preços,
    como o passageiro descobre a organização dele, destino da `configuracoes_sistema`). A regra que
    sustenta a spec: o papel `GERENTE` **só** vem da confirmação servidor-a-servidor do pagamento.

## Próximos Passos Sugeridos
1. **Infra da SPEC-WPP-01/11**: Evolution API na VPS (docker compose + TLS) e variáveis no `.env` do
   servidor (`EVOLUTION_URL`, `EVOLUTION_API_KEY`, `WHATSAPP_WEBHOOK_TOKEN`, `APP_URL_PUBLICA` — ver
   `.env.example`; o `docker-compose.prod.yml` já as repassa).
2. **Decidir a SPEC-PLT-03** (planos, resolução de organização, faseamento) e implementar a **fase 1**
   (V16: `Organizacao`/`Assinatura`/`Pagamento` + checkout + webhook).
3. **WhatsApp dos motoristas**: avisar o motorista da viagem designada pelo chat (reusa a porta).
4. **Assentos/capacidade** (`assentos_viagem`) + **alocação por prioridade** (Incremento B).
5. **Home por papel** (DT-17) e **lockout do login** (DT-12).
