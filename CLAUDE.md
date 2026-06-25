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
> constituição, especificação de produto, plano técnico, specs por feature (SPEC-01..07), ADRs,
> cenários de teste e o roadmap. **Comece por lá** ao retomar — ver a seção
> "Estado atual e como retomar" no fim deste arquivo.

**Escopo já implementado:** autenticação; CRUD de Usuários/Veículos/Cidades; SYSADMIN + `/admin`
(configuração de sessão dinâmica, auditoria, convites); onboarding por convite/token +
`NotificacaoService` (in-app/e-mail/whatsapp-stub); **viagens rotineiras/imprevistas** (linhas
programadas, painel semanal, designação, conflito, ciclo de status, visão do motorista);
**endereço estruturado do passageiro** (municípios PB) + aba de análise; redesign do shell.
**Ainda fora do escopo:** solicitação de transporte (passageiro), alocação/assentos, escalas de
motorista, perfil/CNH do motorista, integração WhatsApp real (Evolution API).

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
│                    #   Linha, MotoristaViagem, Admin, Configuracao, Auditoria, Convite, Ativacao,
│                    #   Conta, Notificacao, Perfil, Analise, Whatsapp, Ping
├── domain/          # Usuario, Veiculo, Cidade, Viagem, LinhaProgramada, Endereco, Municipio,
│   │                #   ConfiguracaoSistema, LogAuditoria, Notificacao, TokenAtivacao
│   └── enums/       # Papel(+SYSADMIN), StatusUsuario, Tipo/StatusVeiculo, TipoCidade, StatusViagem,
│                    #   TipoViagem, DiaSemana, CategoriaAuditoria
├── dto/             # Records de formulário (ViagemForm, LinhaProgramadaForm, DesignacaoForm,
│                    #   EnderecoForm, PainelSemana, ...)
├── notificacao/     # CanalNotificacao (interface) + InApp/Email/Whatsapp (stub) + CanalTipo
├── exception/       # RecursoNaoEncontradoException, RegraNegocioException
├── repository/      # Interfaces Spring Data JPA
├── security/        # UsuarioAutenticado (UserDetails), CaladriusUserDetailsService
├── service/         # Lógica de negócio (@Transactional): Usuario/Veiculo/Cidade/Viagem,
│                    #   LinhaProgramada, Configuracao, Auditoria, Convite, Notificacao, Endereco
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
| **[docs/sdd/](docs/sdd/)** | **SDD — fonte da verdade**: constituição, produto, plano técnico (ADRs), specs (SPEC-01..07), [roadmap](docs/sdd/03-tarefas-e-roadmap.md), [cenários de teste](docs/sdd/cenarios-de-teste.md) |
| [README.md](README.md) | Visão geral, como rodar, acesso, estrutura |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Camadas, HTMX, Flyway |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md) | Migrations, nomenclatura, validação, Conventional Commits |
| [docs/SECURITY.md](docs/SECURITY.md) | SAST, OWASP, configuração do Spring Security |

## Estado atual e como retomar (ponto de restauração)
> **Atualizado em 2026-06-18.** Para retomar em um novo chat: **leia este arquivo + [`docs/sdd/`](docs/sdd/)**
> (em especial o [roadmap](docs/sdd/03-tarefas-e-roadmap.md), que rastreia o estado por capacidade e as
> dívidas técnicas DT-01..DT-11). Este `CLAUDE.md` é carregado automaticamente em todo chat.

- **Último marco**: commit `feat: gestão avançada, viagens rotineiras, endereços e redesign do shell`
  (na `main`, já no GitHub e implantado). Migrations até **V9**. **Testes verdes** (35: 34 unidade +
  1 de contexto Testcontainers que aplica V1→V9 e valida o schema). `mvn test` funciona localmente.
- **Specs implementadas (✅)**: SPEC-01..07 — ver o status no topo de cada arquivo em `docs/sdd/specs/`.
- **Pontos de atenção / dívidas em aberto** (do roadmap):
  - **Passageiro**: hoje só faz cadastro + perfil (endereço). **Falta a função-fim**: *solicitar
    transporte* e *ver suas viagens alocadas* (dependem de `solicitacoes_transporte`/`assentos_viagem`).
  - **Motorista**: `/minhas-viagens` (ver + status) funciona; **falta** perfil/CNH (`perfis_motorista`)
    e a visão de "Veículos".
  - **Home (`/`)**: ainda mostra os totais do sistema para **qualquer** papel — ajustar para esconder
    de não-gerentes e dar landing por papel.
  - **WhatsApp**: seção do gerente (`/whatsapp`) é placeholder; canal WhatsApp do `NotificacaoService`
    é stub (Evolution API em avaliação).
  - **DT-03** (carga horária do motorista via `escalas_motorista`) e **imprevista** com campos de
    origem improvisada/horário de retorno na UI ainda pendentes.

## Próximos Passos Sugeridos
1. **Solicitação de transporte (passageiro)** + alocação/assentos → completa a jornada do passageiro.
2. **Perfil/CNH do motorista** (`perfis_motorista`) e visão de veículos.
3. **Home por papel** (esconder totais de não-gerente; atalhos por papel).
4. **Escalas de motorista** (`escalas_motorista`) → carga horária (DT-03 v2).
5. **WhatsApp** real via Evolution API (quando as features forem definidas).
