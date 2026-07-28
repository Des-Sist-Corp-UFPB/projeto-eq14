# SPEC-15 — Multi-ambiente por secretaria (isolamento multi-tenant por instância)

| | |
|---|---|
| **Área** | `MT` (multi-tenant / multi-ambiente) |
| **Papéis** | Operador de infra / SYSADMIN da **plataforma** (provisiona e opera os ambientes). **Dentro** de cada ambiente, os papéis normais (GERENTE/MOTORISTA/PASSAGEIRO) seguem idênticos e isolados. Nenhum papel novo de usuário final. |
| **Status geral** | 🚧 **Proposta + protótipo (Opção A).** Decisão em §3, comparação **A/B/C** em §2, runbook operacional em [`docs/multi-ambiente.md`](../../multi-ambiente.md). **Sem código de aplicação, sem migration** — a entrega é de **infra/provisionamento**. |
| **Constituição** | Art. IX (português), Art. XI (segurança — isolamento e segredos **por ambiente**), **Art. XII (o gate do CI é a compilação — o protótipo é aditivo e não toca no build/`docker/Dockerfile`)**, Art. XIII (`/ping`/Actuator intactos, por ambiente), Art. XIV (ambiente compartilhado — a Opção A **respeita** por isolar; ver §5.1), Art. IV (cada ambiente roda o Flyway no **seu** banco) |
| **Relacionada** | [SPEC-01](SPEC-01-autenticacao.md) (RBAC + admin-bootstrap via `DataInitializer`, **por ambiente**), [SPEC-08](SPEC-08-login-social-google.md)/[SPEC-10](SPEC-10-integracao-whatsapp.md)/[SPEC-14](SPEC-14-observabilidade-opentelemetry.md) (mesmo padrão "ativa por variável de ambiente" — aqui os segredos são **por secretaria**) · **ADR-19** ([plano técnico §9](../02-plano-tecnico.md)) |
| **Código/Infra** | `docker/docker-compose.tenant.yml` (modelo de stack isolado), `docker/.env.tenant.example` (modelo de `.env` por ambiente), `scripts/novo-ambiente.sh` (provisionamento), [`docs/multi-ambiente.md`](../../multi-ambiente.md) (runbook). **Nenhuma alteração em código Java. Nenhuma migration.** |

---

## 1. A lacuna que esta spec cobre

O cenário do dono do produto: **várias secretarias de saúde assinam o serviço** — p.ex. Campina
Grande (PB) e Caruaru (PE) — e **uma não pode ver nem interferir nos dados da outra**. Hoje o
CALADRIUS é **single-tenant**: não existe nenhuma fronteira organizacional.

O que **de fato** isola dados hoje (e por que **não** basta):

| Mecanismo | Isola o quê | Serve como fronteira entre secretarias? |
|---|---|---|
| **RBAC** (Art. VII) | o que cada **papel** pode fazer | ❌ não — é ortogonal à organização |
| **Por dono** (SPEC-09) | um PASSAGEIRO só vê as **próprias** solicitações (`listarDoPassageiro(passageiroId)`) | ❌ não — não escala para "todos os dados de uma secretaria" |
| **GERENTE** | — | ❌ vê **tudo, globalmente** (`ViagemService.listar`, `listarDemandaPendente` — sem filtro de escopo) |

A entidade `Usuario` **não tem** FK de organização; `Cidade`/`Municipio` são **dados de referência
compartilhados** (origem/destino de viagem), **não** donos. Ou seja: se Campina e Caruaru rodassem
no **mesmo** deploy, o gerente de uma **veria a outra**. Esta spec fecha essa lacuna.

---

## 2. Opções avaliadas

Três padrões clássicos de multi-tenancy, do mais isolado ao mais compartilhado:

| Critério | **A — Instância por cliente (silo)** | **B — Schema por tenant** | **C — Linha a linha (coluna `organizacao`)** |
|---|---|---|---|
| **Como** | 1 stack por secretaria: app + **banco dedicado** + volume + subdomínio | 1 Postgres, **1 schema** por secretaria; resolve o schema no login | 1 banco/schema; **FK `organizacao`** em cada tabela; **filtra toda query** pelo tenant do usuário |
| **Isolamento de dados** | 🟢 **físico** (bancos/volumes separados) | 🟡 lógico (mesmo servidor) | 🔴 lógico (mesma tabela; separação só na query) |
| **Código de aplicação** | 🟢 **zero** (nenhuma mudança) | 🟡 resolver de schema + roteamento | 🔴 `TenantContext` + filtro em **100%** das consultas |
| **Risco de vazamento entre clientes** | 🟢 ~nulo (impossível cruzar bancos) | 🟡 baixo | 🔴 **alto** — 1 `WHERE` esquecido = vazamento |
| **Custo operacional/recursos** | 🔴 **N** apps + **N** bancos | 🟡 N schemas, 1 servidor | 🟢 1 stack para todos |
| **Relatório consolidado (multi-secretaria)** | 🔴 difícil (dados separados) | 🟡 possível | 🟢 natural |
| **Ligar/desligar um cliente** | 🟢 trivial (`up`/`down` do stack) | 🟡 médio | 🟡 médio |
| **Migrations** | 🟢 Flyway roda em cada banco (isolado) | 🟡 aplicar em N schemas | 🟢 1 vez |
| **Esforço até a 1ª venda** | 🟢 **baixo** (só infra; casa com o deploy atual) | 🟡 médio | 🔴 alto (refactor amplo) |

**Leitura:** A troca custo-de-recursos por **segurança e simplicidade**; C troca segurança por
**eficiência e visão consolidada**; B fica no meio. Para poucos clientes com **exigência forte de
separação** e **sem** necessidade de painel único, A domina.

---

## 3. Decisão — **Opção A (instância por cliente / silo)**

> **Cada secretaria roda um _stack_ próprio e isolado (app + banco dedicado + subdomínio + `.env`
> com segredos), _namespaced_ pelo Compose (`name: caladrius-<slug>`). Não há conceito de "tenant"
> no código — o isolamento é _físico_.** Registrada como **ADR-19**.

**Por quê A (e não C) agora:**
- **Segurança máxima com risco de lógica nulo** — o requisito do dono é "uma não tem nada a ver com
  a outra". Bancos separados tornam o vazamento **impossível por construção**; C o deixa a um `WHERE`
  de distância (Art. XI: segurança como requisito, não verniz).
- **Zero código, zero migration** — nenhuma entidade, nenhum filtro, nenhum risco de regressão. O
  gate do CI (compilação, Art. XII) nem é tocado: a entrega é **aditiva** (compose + script + docs).
- **Casa com o que já existe** — o deploy hoje já sobe **um stack inteiro** por `docker compose`. A
  Opção A é esse mesmo padrão, **parametrizado** e **repetido por cliente**.
- **Blast radius por cliente** — um incidente (queda, corrupção, upgrade ruim) fica **contido** em uma
  secretaria; as demais não sentem.

**Quando revisitar (gatilhos para C):** ver **§7**.

### Requisitos funcionais

| ID | Requisito | Estado |
|---|---|---|
| **FR-MT-01** | Cada secretaria = **stack isolado**: app + **banco dedicado** + volume + subdomínio, sem recurso compartilhado entre clientes. | 🚧 protótipo (`docker-compose.tenant.yml`) |
| **FR-MT-02** | Provisionar um novo ambiente **sem editar código** — por **modelo + script**. | 🚧 protótipo (`scripts/novo-ambiente.sh`) |
| **FR-MT-03** | Dados de uma secretaria **nunca** visíveis a outra (isolamento **físico** por banco). | 🚧 por desenho (bancos/volumes separados) |
| **FR-MT-04** | Cada ambiente tem **seu** admin-bootstrap (o `DataInitializer` roda no 1º boot do **seu** banco). | ✅ herdado (SPEC-01) |
| **FR-MT-05** | Segredos e integrações (WhatsApp, Google, OTel) são **por ambiente**. | 🚧 protótipo (`.env` por ambiente) |
| **FR-MT-06** | Atualizar a versão é **propagável a todos** os ambientes (a **mesma** imagem do CI serve todos). | 🚧 runbook (rollout, §5.3) |

### Regras de negócio / invariantes

- **RN-MT-01** — **Nomeação**: o Compose project `caladrius-<slug>` prefixa **containers, rede e
  volume**; dois ambientes nunca colidem em recurso Docker.
- **RN-MT-02** — **Porta única por ambiente** (`127.0.0.1:<APP_PORT>`, só no loopback); o **Caddy** do
  host faz o proxy `https://<subdomínio> → 127.0.0.1:<APP_PORT>`. A porta do **Postgres não é
  publicada** (fica interna ao stack).
- **RN-MT-03** — **Senha de banco única por ambiente**, **sorteada** no provisionamento, guardada só no
  `.env` do host (permissão `600`, **git-ignored**). Nunca versionada (Art. XI).
- **RN-MT-04** — **Backup por ambiente**: `pg_dump` do banco de cada secretaria é **independente**.
- **RN-MT-05** — `/ping` e `/actuator/health` continuam **por ambiente** (contrato Art. XIII intacto).
- **RN-MT-06** — **Sem tenant no código** → nenhuma consulta precisa filtrar por organização; o risco
  de vazamento fica em **infra** (fácil de auditar), não espalhado pela **lógica**.
- **RN-MT-07** — **Rescisão de um cliente** = derrubar o stack + arquivar o dump + apagar o volume;
  **não afeta** os demais ambientes.

---

## 4. Protótipo entregue (Opção A)

Quatro artefatos **aditivos** (nada é referenciado pelo CI atual; o deploy `eq14` da disciplina
continua igual):

| Artefato | Papel |
|---|---|
| `docker/docker-compose.tenant.yml` | **Modelo** de stack isolado (app + Postgres dedicado), parametrizado por `${TENANT}`, `${APP_PORT}`, `${POSTGRES_*}`, `${DOMINIO}`, `OTEL_*`, `EVOLUTION_*`, `GOOGLE_*`. `name: caladrius-${TENANT}` faz o _namespacing_ (RN-MT-01). |
| `docker/.env.tenant.example` | **Modelo** do `.env` por ambiente (versionado, **sem segredos**). |
| `scripts/novo-ambiente.sh` | Gera `ambientes/<slug>/.env` a partir do modelo, **sorteia a senha** do banco (RN-MT-03) e imprime os próximos passos (Caddy + subir). Não sobe nada; recusa sobrescrever. |
| [`docs/multi-ambiente.md`](../../multi-ambiente.md) | **Runbook** operacional: pré-requisitos do host, provisionar, Caddy, 1º acesso, **rollout** de versão, backups, encerramento. |

**A mesma imagem do CI serve todos** — a Opção A **não** reconstrói nada por cliente: reusa
`APP_IMAGE=ghcr.io/des-sist-corp-ufpb/projeto-eq14:latest` (a que o `deploy.yml` publica no GHCR).

---

## 5. Operação

### 5.1 "Preciso de um deploy novo para cada secretaria?" — **sim**

Na Opção A, **cada secretaria é um deploy independente**. Mas o custo se divide em:

- **Uma vez, por host** (setup): Docker + Caddy + (opcional) DNS wildcard. Já existe hoje.
- **Por secretaria** (repetível, ~minutos): `scripts/novo-ambiente.sh <slug> …` → `docker compose
  --env-file … -f docker/docker-compose.tenant.yml up -d` → 1 bloco no Caddyfile → DNS. É isso.

O que **não** se repete: **build de imagem** (a mesma para todas), **código** (nenhuma mudança) e
**migrations** (o Flyway roda sozinho no 1º boot do banco de cada ambiente).

> **Nota sobre a infra da disciplina (Art. XIV):** o servidor de aula usa um **Postgres compartilhado
> do professor**, então a Opção A "pura" (Postgres dedicado por ambiente) é um cenário de
> **produtização real** (VPS própria da equipe). Dentro da infra de aula, dá para **demonstrar** o
> padrão com **um banco por ambiente no mesmo servidor** (variante que tende à Opção B) — mas a
> decisão de produto é a Opção A.

### 5.2 Provisionar (resumo — detalhe no runbook)

```bash
scripts/novo-ambiente.sh campina-grande \
    --nome "Secretaria de Saúde de Campina Grande" \
    --dominio campina.caladrius.app --porta 8114
# depois, no servidor:
docker compose --env-file ambientes/campina-grande/.env -f docker/docker-compose.tenant.yml up -d
```

### 5.3 Atualizar todos os ambientes (rollout)

Como a imagem é única, atualizar = **repetir `pull` + `up -d`** por ambiente (o runbook traz um laço
pronto). Recomenda-se **canário**: atualizar 1 secretaria, validar, depois as demais.

---

## 6. Consequências / trade-offs

- **Prós**: isolamento físico; risco de vazamento por lógica **nulo**; simplicidade (zero código);
  _blast radius_ por cliente; on/off trivial por cliente.
- **Contras**: **custo linear** de recursos (N apps + N bancos); **N deploys a atualizar** (mitigado:
  mesma imagem + laço de rollout); **sem visão consolidada** (relatório multi-secretaria exigiria C);
  N pontos de backup (mitigado: cron por ambiente).
- **Faixa de conforto**: ótimo para **poucas dezenas** de secretarias. Acima disso, o custo por
  Postgres passa a doer → reavaliar **B/C** (§7).

---

## 7. Quando migrar para a Opção C (linha a linha)

Gatilhos que justificariam o custo do refactor:

1. **Painel único multi-secretaria** (um super-admin da plataforma vendo/administrando todas).
2. **Auto-serviço de cadastro** de secretarias (signup de tenant sem provisionar infra à mão).
3. **Muitos tenants pequenos** — o custo de um Postgres por cliente domina.
4. **Relatórios agregados** entre secretarias como requisito de produto.

**Esboço do que C exigiria** (para dimensionar): entidade `Organizacao`; **FK `organizacao`** em
`usuarios`, `viagens`, `veiculos`, `linhas_programadas`, `solicitacoes_viagem` (+ migration); um
`TenantContext` derivado do **usuário logado**; e — para não depender de lembrar o `WHERE` — um
**filtro central** (Hibernate `@Filter`/interceptor) aplicado a todas as consultas, com testes de
isolamento. É um incremento próprio (nova spec/ADR).

---

## 8. Impacto em specs/documentos existentes

- **[Roadmap §1](../03-tarefas-e-roadmap.md)** — nova capacidade **"Multi-ambiente por secretaria"** +
  **Incremento H**.
- **[Plano técnico §9](../02-plano-tecnico.md)** — **ADR-19** registrada.
- **[CLAUDE.md](../../../CLAUDE.md)** — atualizar o "Estado atual" **quando a Opção A for de fato
  operada** (por ora é proposta + protótipo; não representar como enviado).
- **[`docs/multi-ambiente.md`](../../multi-ambiente.md)** — runbook operacional (novo).
