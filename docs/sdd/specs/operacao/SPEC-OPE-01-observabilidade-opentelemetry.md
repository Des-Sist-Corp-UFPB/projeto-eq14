# SPEC-OPE-01 — Observabilidade com OpenTelemetry (traces, métricas e logs no stack central da disciplina)

| | |
|---|---|
| **Área** | `OBS` (observabilidade) |
| **Papéis** | SYSADMIN (variáveis de ambiente/segredos no deploy); equipe/professor (leitura do painel Grafana). **Sem papel de usuário final** — é capacidade transversal de operação. |
| **Status geral** | ✅ **Em produção (2026-07-24)** — telemetria de **`dsc-eq14`** fluindo no backend central (confirmado no Grafana: `dsc-eq14` no dropdown *Job* do *JVM Overview*). Instrumentação = **agente Java (automática)** + **camada manual** (`RastreamentoService`) em **2 fluxos de negócio**; 199 testes verdes, `docker build` verde, RN-OBS-01 conferido na imagem. **Sem migration.** Ativação e **troubleshooting** (armadilhas reais da 1ª ativação) no **§11**. |
| **Constituição** | Art. I (versões fixas — agente **pinado `v2.30.0`**; deps OTel pinadas pelo BOM do Spring Boot), Art. XI (segurança — token só por env, TLS, nada de segredo no repo), **Art. XII (o gate do CI é a compilação — o download do agente no build de prod não pode quebrar `docker build`)**, Art. XIII (`/ping` e Actuator intactos), Art. XIV (reusa o **backend central**, **não sobe infra própria** no servidor compartilhado) |
| **Relacionada** | [SPEC-ACE-02](../acesso/SPEC-ACE-02-login-social-google.md) (padrão de **ativação condicional por variável de ambiente** — o app sobe igual sem config), [SPEC-WPP-01](../whatsapp/SPEC-WPP-01-integracao-whatsapp.md) (mesma filosofia "sem config, sem quebra"; infra externa via env/segredos), backlog **"Observabilidade"** do [roadmap §4](../../03-tarefas-e-roadmap.md) · **ADR-18** ([plano técnico §9](../../02-plano-tecnico.md)) |
| **Código/Infra** | `pom.xml` (**+`opentelemetry-api`** compile, **+`opentelemetry-sdk-testing`** test — versões geridas pelo `opentelemetry-bom` do Spring Boot, 1.43.0); `observabilidade/RastreamentoService` + `observabilidade/TelemetriaConfig`; `service/SolicitacaoViagemService` (2 spans de negócio + logs estruturados); `docker/Dockerfile` (baixa e embute o agente pinado); `docker/docker-compose.prod.yml` (`OTEL_*` + `JAVA_TOOL_OPTIONS`, todos com default vazio); `.env.example`; `docker/Dockerfile.dev` + `docker/docker-compose.dev.yml` (agente + `grafana/otel-lgtm`). **Testes**: `RastreamentoServiceTest`, `SolicitacaoViagemTelemetriaTest`. **Nenhuma migration.** |

---

> ### 🔁 Playbook reutilizável (outros serviços/projetos)
> Esta spec é do CALADRIUS, mas o **padrão** vale para **qualquer serviço JVM/Spring Boot** na infra da
> disciplina (e, com pequenas adaptações, fora dela). Para reusar em outro serviço, troque só os pontos
> abaixo — o **runbook genérico de ativação + troubleshooting está no [§11](#11-runbook-de-ativação-em-produção--troubleshooting-lições-da-1ª-ativação)**:
>
> | Ponto de substituição | Neste projeto (exemplo) | No seu serviço |
> |---|---|---|
> | `OTEL_SERVICE_NAME` | `dsc-eq14` | `dsc-eqNN` — o **seu** identificador |
> | Caminho do agente na imagem | `/app/opentelemetry-javaagent.jar` | onde o **seu** Dockerfile copia o `.jar` |
> | Span(s) manuais de negócio | `solicitar-sob-demanda`, `aprovar-solicitacao` (`SolicitacaoViagemService`) | `nome-da-sua-operação` no **seu** service |
> | Atributos de domínio | `solicitacao.tipo`, `solicitacao.cidade_destino` | atributos do **seu** domínio (**sem PII**) |
> | Endpoint / token | `https://otel.dsc.rodrigor.com` / `<TOKEN_DA_TURMA>` | o endpoint e token do seu backend |
>
> **O que NÃO muda** (o "esqueleto" reutilizável): baixar o **agente pinado** no Dockerfile e embuti-lo
> **inerte**; ligar por **`JAVA_TOOL_OPTIONS=-javaagent:<caminho>`**; `OTEL_*` no **ambiente do container**;
> o helper **`RastreamentoService`** (independente de domínio — copiável como está); e o **diagnóstico**
> (log de boot do container + filtro por `service.name` no painel). As classes de exemplo
> (`RastreamentoService`/`TelemetriaConfig`) não referenciam nada do CALADRIUS — servem de base direta.

---

## 1. A lacuna que esta spec cobre

Hoje a observabilidade do CALADRIUS se resume a dois pontos **locais ao container**: o
health-check público `/ping` (Art. XIII) e o Actuator (`health,info,metrics` — `application.yml`).
Isso só ajuda quem consegue **acessar o servidor**. O problema que o dono da infra (professor)
levantou em sala é justamente o contrário:

> *"Um problema pode gerar **indisponibilidade** e você não consegue acessar para ver o que houve."*

A ideia do **agregador central** é reunir os sinais de todas as equipes num só lugar (Grafana), de
fora do servidor, para diagnosticar mesmo quando a aplicação está fora do ar. Esta spec entrega isso:
**exportar os três sinais de telemetria** — **traces**, **métricas** e **logs** — via
**OpenTelemetry (OTLP)** para o **backend central da disciplina** (Grafana + Tempo + Prometheus +
Loki), **sem subir infra própria** e **sem migration**.

Dois princípios regem o desenho (espelham SPEC-ACE-02/10):

- **Instrumentação de baixo atrito** — um **agente Java** (instrumentação por bytecode) captura
  automaticamente HTTP (Spring MVC), JDBC (Hikari/PostgreSQL), a JVM e os **logs** (Logback), **sem
  uma linha de código**. Sobre essa base, uma **camada manual mínima** (`RastreamentoService`)
  nomeia operações do **nosso domínio** e as enriquece com atributos de negócio — o que o agente,
  sozinho, não conhece (é também o entregável avaliado de "spans manuais").
- **Ligado por configuração, desligado por padrão** — sem as variáveis `OTEL_*`/`JAVA_TOOL_OPTIONS`,
  o app sobe **idêntico ao de hoje** (agente dormente; a camada manual vira no-op). A telemetria é
  ativada no `.env` do servidor (RN-OBS-01), como o login Google (SPEC-ACE-02) e o WhatsApp (SPEC-WPP-01).

### Requisitos funcionais

| ID | Requisito | Estado |
|---|---|---|
| **FR-OBS-01** | Exportar **traces** das requisições (HTTP + JDBC) para o Tempo via OTLP. | ✅ **em produção** (`dsc-eq14` no Grafana) |
| **FR-OBS-02** | Exportar **métricas** (JVM, HTTP, pool de conexões) para o Prometheus via OTLP. | ✅ **em produção** |
| **FR-OBS-03** | Exportar **logs** da aplicação para o **Loki** via OTLP (agregador central), correlacionados ao trace. | ✅ **em produção** |
| **FR-OBS-04** | Separar a telemetria da equipe por `service.name = dsc-eq14` (filtro no Grafana). | 🟢 ✅ (`dsc-eq14` prod / `dsc-eq14-dev` dev) |
| **FR-OBS-05** | Não exigir migration; ligar/desligar por variável de ambiente. | ✅ (por desenho) |
| **FR-OBS-06** | **Spans de negócio** manuais (nome de operação + atributos de domínio), cobertos por teste. | ✅ 2 fluxos: `solicitar-sob-demanda` + `aprovar-solicitacao` (ver §3.1/§8) |

---

## 2. Decisão de instrumentação — base da **ADR-18**

**Decisão:** **agente OpenTelemetry Java standalone** para os três sinais automáticos, **complementado
por uma camada manual mínima** (`RastreamentoService`) para spans de negócio, exportando **OTLP** para
o **backend central da disciplina** (logs no Loki pela mesma via OTLP). Ativação **condicional** por
variável de ambiente. Registrada como **ADR-18** no plano técnico.

### 2.1 Como instrumentar — agente (auto) + camada manual mínima

| Aspecto | **Agente OTel Java (auto)** — base | **Camada manual (`RastreamentoService`, OTel API)** — complemento |
|---|---|---|
| **O que cobre** | HTTP (Spring MVC), JDBC, JVM, **logs (Logback)**, cliente HTTP — "de graça". | Operações de **negócio** nomeadas (ex.: `solicitar-sob-demanda`) + atributos de domínio (destino, tipo). |
| **Mudança de código** | **Nenhuma** — anexado por `-javaagent`. | Mínima e **aditiva**: só a **API** no `pom.xml` (SDK vem do agente) + 1 fluxo fiado. |
| **Testabilidade** | Não testável em unidade (bytecode; exige o agente na JVM). | **Testável** com `opentelemetry-sdk-testing` (spans em memória) — ver §8. |
| **Desligar sem config** | Trivial — sem `JAVA_TOOL_OPTIONS`, a JVM nem carrega o agente. | `GlobalOpenTelemetry.get()` vira **no-op**: a ação roda, o span é descartado (RN-OBS-06). |

**Por quê os dois:** o agente entrega os três sinais **operacionais** (ver o que acontece em prod)
sem uma linha de código e sai de cena quando não configurado — o padrão "sem config, sem quebra" das
SPEC-ACE-02/10. A **camada manual** existe por dois motivos: (1) é o **entregável avaliado** ("≥2 spans
manuais + atributo de negócio + log estruturado"); (2) só ela dá um alvo **testável em unidade** e um
**cenário real** ("solicitou → gerou telemetria"). Métricas/spans de negócio adicionais ficam para
depois (§3.2), via Micrometer/OTel SDK.

### 2.2 Onde mandar — backend central da disciplina × self-host

| Aspecto | **Backend central (Grafana+Tempo+Prometheus+Loki da disciplina)** — escolhido | **Self-host (subir `otel-lgtm`/collector no servidor da equipe)** |
|---|---|---|
| **Infra da equipe** | **Zero** — o professor provê e mantém. | Container(es) pesado(s) na VPS **compartilhada** (RAM, portas, TLS). |
| **Art. XIV (ambiente compartilhado)** | Respeitado — nada de recursos extras no servidor. | Tensiona: `grafana/otel-lgtm` é pesado; portas 3000/4317/4318 podem colidir; expor Grafana é risco. |
| **Segredo** | Um **token da turma** no header `Authorization` (por env). | Gerir auth/HTTPS do próprio Grafana. |
| **Separação entre equipes** | Por `OTEL_SERVICE_NAME` (`dsc-eq14`). | N/A (isolado). |

**Por quê o central:** é o mesmo princípio do Postgres/pgAdmin **compartilhados** — reusar a infra da
disciplina em vez de duplicá-la (Art. XIV). O `grafana/otel-lgtm` **continua existindo, mas só no
`docker-compose.dev.yml`** (roda na máquina do desenvolvedor, isolado, **sem token**, sem poluir o
backend compartilhado com ruído de dev — ver §4.1).

### 2.3 Logs — OTLP na mesma via × appender Loki dedicado

**Decisão: logs pela mesma via OTLP** (não um `loki-logback-appender` nem push direto ao Loki).
O mesmo endpoint OTLP (`https://otel.dsc.rodrigor.com`) roteia **traces→Tempo**,
**métricas→Prometheus** e **logs→Loki**. Vantagens: **correlação log↔trace** (cada log carrega
`trace_id`/`span_id` que o agente injeta no MDC), **reuso** do mesmo agente/endpoint/token, e **zero
dependência nova** de runtime além da API. Confirmado no guia do professor
([`docs/opentelemetry-logs.md`](../../../opentelemetry-logs.md)): o agente Java intercepta o Logback e
envia ao OTLP **sem código**.

---

## 3. Escopo

### 3.1 Inclui (implementado)

**Aplicação (Java):**
- **Deps** (`pom.xml`, aditivas e **versionless** — geridas pelo `opentelemetry-bom` do Spring Boot,
  1.43.0): `io.opentelemetry:opentelemetry-api` (compile) e `io.opentelemetry:opentelemetry-sdk-testing`
  (test). O **SDK/exportador OTLP não entram no jar** — vêm do **agente** em runtime.
- **`observabilidade/RastreamentoService`**: fina camada sobre a API OTel. `rastrear(nome, atributos,
  acao)` abre um span, anexa atributos, e em falha grava a exceção + marca `ERROR` sem engolir o erro.
- **`observabilidade/TelemetriaConfig`**: bean `OpenTelemetry` = `GlobalOpenTelemetry.get()`
  (`@ConditionalOnMissingBean`) — com agente, é a instância dele; sem agente, no-op.
- **2 fluxos de negócio fiados** (`SolicitacaoViagemService`), cada um com **log estruturado** (vai ao
  Loki correlacionado ao trace) e atributos **sem PII** (RN-OBS-08):
  - `solicitarSobDemanda` → span **`solicitar-sob-demanda`** (`solicitacao.tipo`, `solicitacao.cidade_destino`);
  - `aprovar` → span **`aprovar-solicitacao`** (`solicitacao.id`, `solicitacao.cidade_destino`).

**Dev** (`docker/Dockerfile.dev` + `docker/docker-compose.dev.yml`):
- `Dockerfile.dev` baixa o `opentelemetry-javaagent.jar` **pinado** (`ARG OTEL_AGENT_VERSION=2.30.0`) e
  o injeta no `mvn spring-boot:run` (`-javaagent:/app/opentelemetry-javaagent.jar`).
- `docker-compose.dev.yml` sobe o serviço **`otel-lgtm`** (`grafana/otel-lgtm`: Grafana `:3000`,
  OTLP gRPC `:4317`, OTLP HTTP `:4318`) e aponta o app via `OTEL_*`
  (`OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-lgtm:4318`, `http/protobuf`, exporters `otlp` para
  traces/metrics/logs, `OTEL_SERVICE_NAME=dsc-eq14-dev`, `deployment.environment=dev`).

**Prod** (`docker/Dockerfile`, `docker/docker-compose.prod.yml`, `.env.example`):
- **`docker/Dockerfile`:** baixa o agente **no estágio de build** (`maven:3.9.9-eclipse-temurin-21` tem
  `curl`), com **versão pinada** (`ARG OTEL_AGENT_VERSION=2.30.0`, `curl -fsSL`), e **copia** o `.jar`
  para o runtime com `chown mercado:mercado`. **O `ENTRYPOINT` não muda**: a ativação é **opcional**
  via `JAVA_TOOL_OPTIONS`.
- **`docker/docker-compose.prod.yml`:** repassa `OTEL_SERVICE_NAME`, `OTEL_EXPORTER_OTLP_ENDPOINT`,
  `OTEL_EXPORTER_OTLP_PROTOCOL`, `OTEL_EXPORTER_OTLP_HEADERS`, `OTEL_RESOURCE_ATTRIBUTES`,
  `OTEL_LOGS_EXPORTER` e `JAVA_TOOL_OPTIONS` — **todos com default vazio/seguro** (comportamento atual
  preservado — RN-OBS-01), lidos do `.env`.
- **`.env.example`:** documenta as chaves com **placeholder** (o token real é segredo — RN-OBS-02).

**Comum:** três sinais por OTLP (traces→Tempo, métricas→Prometheus, logs→Loki); separação por
`OTEL_SERVICE_NAME`; ambiente por `OTEL_RESOURCE_ATTRIBUTES=deployment.environment=prod|dev`.

### 3.2 Não inclui (futuro)
- **Mais fluxos manuais** além dos 2 fiados (ex.: `ViagemService.designar`, onboarding do bot) — a base
  (`RastreamentoService`) já permite adicionar em 1 linha.
- **Métricas de negócio** custom via Micrometer (ex.: solicitações/dia, taxa de aprovação).
- **Alertas** (Grafana Alerting/Alertmanager) e **dashboards custom** no backend central — são do
  professor (não controlamos aquela infra).
- **Self-host** de qualquer parte do stack no servidor da disciplina (Art. XIV — ver §2.2).

---

## 4. Arquitetura

### 4.1 Visão geral (dev × prod)

O **mesmo agente** e o **mesmo protocolo** (OTLP/http-protobuf) nos dois ambientes; muda só **o
destino** (e, em prod, o header com o token). No dev, o backend é local e efêmero; em prod, é o
central da disciplina.

```mermaid
flowchart LR
    subgraph APP["Aplicação Spring Boot (CALADRIUS)"]
        JVM["JVM + OpenTelemetry Java agent<br/>(-javaagent via JAVA_TOOL_OPTIONS)<br/>auto: HTTP · JDBC · JVM · Logback"]
        MAN["RastreamentoService (OTel API)<br/>spans de negócio (solicitar-sob-demanda)"]
        MAN --> JVM
    end

    subgraph DEV["DEV — na máquina do desenvolvedor (efêmero)"]
        LGTM["grafana/otel-lgtm<br/>OTLP :4318 · Grafana :3000<br/>(Tempo + Prometheus + Loki embutidos)"]
    end

    subgraph PROD["PROD — backend CENTRAL da disciplina"]
        GW["Ingestão OTLP<br/>https://otel.dsc.rodrigor.com<br/>(Authorization: Bearer token da turma)"]
        TEMPO["Tempo (traces)"]
        PROM["Prometheus (métricas)"]
        LOKI["Loki (logs)"]
        GRAF["Grafana<br/>otel.dsc.rodrigor.com<br/>Explore → filtra service.name=dsc-eq14"]
        GW --> TEMPO
        GW --> PROM
        GW --> LOKI
        TEMPO --> GRAF
        PROM --> GRAF
        LOKI --> GRAF
    end

    JVM -->|"OTLP http/protobuf (dev)"| LGTM
    JVM -->|"OTLP http/protobuf + token (prod)"| GW

    classDef prod fill:#dbeafe,stroke:#1d4ed8,color:#000;
    classDef dev fill:#dcfce7,stroke:#15803d,color:#000;
    class GW,TEMPO,PROM,LOKI,GRAF prod;
    class LGTM dev;
```

### 4.2 Como o agente é ativado (sem tocar no `ENTRYPOINT`)

O `ENTRYPOINT` de prod (`java -XX:... -jar app.jar`) **não muda**. A JVM lê automaticamente a
variável **`JAVA_TOOL_OPTIONS`** e antepõe suas opções. Assim:

- `JAVA_TOOL_OPTIONS` **vazia** (default) → a JVM sobe **sem** o agente → **idêntico ao de hoje**
  (conferido na imagem: o `ENTRYPOINT` não referencia o agente e nenhuma `OTEL_*` é embutida).
- `JAVA_TOOL_OPTIONS=-javaagent:/app/opentelemetry-javaagent.jar` (definida no `.env` do servidor) →
  o agente carrega e passa a exportar, lendo as demais `OTEL_*` do ambiente. O agente também popula o
  `GlobalOpenTelemetry`, então os **spans manuais** entram no **mesmo trace**.

O `.jar` do agente **está presente na imagem** desde o build, mas fica **inerte** até o
`JAVA_TOOL_OPTIONS` referenciá-lo. É o liga/desliga sem rebuild (mesma ergonomia do `GOOGLE_CLIENT_ID`
da SPEC-ACE-02).

---

## 5. Configuração (a "modelagem" desta spec)

Esta spec **não tem schema nem migration** — a sua "modelagem" é o **conjunto de variáveis de
ambiente**. O agente OTel lê os padrões `OTEL_*` diretamente do ambiente.

| Variável | Uso | Valor DEV | Valor PROD | Segredo? |
|---|---|---|---|---|
| `OTEL_SERVICE_NAME` | Identidade/separação no Grafana | `dsc-eq14-dev` | **`dsc-eq14`** | não |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Base OTLP (o agente anexa `/v1/traces\|metrics\|logs`) | `http://otel-lgtm:4318` | `https://otel.dsc.rodrigor.com` | não |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | Transporte OTLP | `http/protobuf` | `http/protobuf` | não |
| `OTEL_EXPORTER_OTLP_HEADERS` | Autenticação na ingestão | *(vazio)* | `Authorization=Bearer <token da turma>` | **SIM** |
| `OTEL_RESOURCE_ATTRIBUTES` | Atributos de recurso (ambiente) | `deployment.environment=dev` | `deployment.environment=prod` | não |
| `OTEL_LOGS_EXPORTER` | Habilita export de **logs** (→ Loki) | `otlp` | `otlp` *(explícito — garante Loki)* | não |
| `OTEL_TRACES_EXPORTER` / `OTEL_METRICS_EXPORTER` | Traces/métricas | `otlp` | `otlp` *(default do agente v2.x)* | não |
| `JAVA_TOOL_OPTIONS` | **Liga o agente** na JVM | *(injetado no `CMD` do dev)* | `-javaagent:/app/opentelemetry-javaagent.jar` | não |

> **Detalhe do endpoint:** usa-se a **base** (sem `/v1/...`); o agente monta o caminho por sinal.
> **`http/protobuf`** casa com a porta 4318/HTTPS (gRPC seria 4317).
>
> **Detalhe do header:** no `.env`, **não** envolver o valor em aspas — o Compose lê literal e as
> aspas virariam parte do header (→ 401). Formato: `OTEL_EXPORTER_OTLP_HEADERS=Authorization=Bearer eyJ...`.

---

## 6. Regras de negócio / operacionais

| Regra | Descrição |
|---|---|
| **RN-OBS-01** | **Sem configuração, sem quebra**: ausentes as variáveis (em especial `JAVA_TOOL_OPTIONS`), o app sobe **idêntico ao atual** — a JVM nem carrega o agente e a camada manual vira no-op. **Conferido na imagem** (§4.2/§8). Espelha a RN-WPP-02 (SPEC-WPP-01) e o padrão da SPEC-ACE-02. |
| **RN-OBS-02** | **O token da turma é SEGREDO**: vive **só** no `.env` do servidor (`/home/ghactions/eq14/.env`), **nunca** no repositório (que é público). O `.env.example` leva apenas *placeholder*. Erro **401** na ingestão = token ausente/errado no header (Art. XI). |
| **RN-OBS-03** | **O gate do CI não pode quebrar** (Art. XII): o agente é baixado com **versão pinada** (Art. I) e o `docker build -f docker/Dockerfile .` segue **verde** (validado). O download usa `curl -fsSL` (falha o build em erro HTTP em vez de gerar um `.jar` corrompido). |
| **RN-OBS-04** | **Separação por `OTEL_SERVICE_NAME`**: exatamente **`dsc-eq14`** em prod (convenção do professor — é o filtro no Grafana → Explore); `dsc-eq14-dev` no dev (destino local, não polui o central). Ambientes distinguidos por `deployment.environment`. |
| **RN-OBS-05** | **Não sobe infra no servidor compartilhado** (Art. XIV): usa o backend central. O `grafana/otel-lgtm` roda **só no dev**, na máquina do desenvolvedor, e some com `docker compose down`. |
| **RN-OBS-06** | **Telemetria nunca derruba o app**: o agente é resiliente (endpoint fora → registra o erro e segue); a camada manual, sem agente, opera como **no-op** e **repropaga** exceções de negócio sem alterá-las (coberto por teste). Espelha RN-WPP-01. |
| **RN-OBS-07** | **Retenção é limitada** (backend central): **traces 72 h**, **logs 48 h**, **métricas 7 dias**. É diagnóstico operacional, não histórico de longo prazo. |
| **RN-OBS-08** | **Nada de segredo/PII na telemetria**: atributos e logs de negócio não carregam senha, token nem dado sensível (o span `solicitar-sob-demanda` leva só `tipo` e `cidade_destino`). Em prod, root `WARN`/app `INFO` já limita o volume. |

---

## 7. Deploy e operação

- **Onde as variáveis vivem:** no `.env` do servidor, gerido pelas **mesmas mãos** que já definem
  `DATABASE_PASSWORD`/`GOOGLE_CLIENT_ID` (SYSADMIN/deploy). `.env` está no `.gitignore`.
- **Sequência de ativação (prod) — o que foi feito:**
  1. Dockerfile embute o agente; `push` → CI builda/publica a imagem (com o agente). Sem `.env`, o
     runtime é **idêntico** (agente inerte — RN-OBS-01), então o push é **seguro**.
  2. Ligar a telemetria pelo **portal da disciplina** (editor de `.env` → **Salvar e aplicar**, que
     **recria o container**) — **não** por SSH nem pelo compose do repo. Passo a passo no **§11**.
  3. Validar no **Dozzle** (boot do container) e no Grafana (§11.3).
- **Ponto a confirmar — RESOLVIDO (2026-07-24):** o servidor **não** usa o `docker-compose.prod.yml`
  do repositório; o container é gerido pelo **portal da disciplina**. As `OTEL_*`/`JAVA_TOOL_OPTIONS`
  no compose do repo valem para o **dev** e como documentação; em **prod**, quem vale é o `.env` do
  portal (§11.1).

---

## 8. Verificação

Diferente das specs de infra "pura", aqui **há testes automatizados** (a camada manual é código de
domínio), somados à validação operacional do agente/infra.

**Testes automatizados** (`mvn test` — 199 verdes, incl. contexto Testcontainers V1→V13):
- **`RastreamentoServiceTest`** (unidade, a *ferramenta*, sem agente/rede — usa `opentelemetry-sdk-testing`
  com `InMemorySpanExporter`): (a) caminho feliz cria span com nome+atributo e devolve o resultado;
  (b) exceção → span `ERROR` + evento `exception`, repropagada; (c) **sem agente** (`OpenTelemetry.noop()`)
  executa a ação e **não quebra** (RN-OBS-06).
- **`SolicitacaoViagemTelemetriaTest`** (*cenário real*, 4 casos): dirige os métodos de negócio de
  verdade (repos mockados) com um `RastreamentoService` real ligado ao SDK de teste, verificando os
  **dois** spans: **`solicitar-sob-demanda`** (nasce com `cidade_destino`/`tipo`; duplicata → `ERROR`)
  e **`aprovar-solicitacao`** (nasce com `cidade_destino`; aprovar não-pendente → `ERROR`) — FR-OBS-06.

**Validação de build/infra:**
- **Gate do CI (Art. XII):** `docker build -f docker/Dockerfile .` **verde** — confirma que o download
  do agente pinado funciona e a imagem monta (validado).
- **RN-OBS-01 (regressão):** conferido na imagem que o `ENTRYPOINT` **não** referencia o agente e que
  **nenhuma** `OTEL_*`/`JAVA_TOOL_OPTIONS` é embutida — sem `.env`, o app sobe idêntico.

**Validação operacional (no deploy, pós-`.env`):** no **Grafana → Explore**, filtrando
`service.name = dsc-eq14`:
- **Tempo** mostra **traces** de requisições, com o span de negócio **`solicitar-sob-demanda`**
  aninhado no trace HTTP quando um passageiro solicita sob demanda;
- **Prometheus** mostra **métricas** JVM/HTTP/pool;
- **Loki** mostra os **logs** da aplicação (`{service_name="dsc-eq14"}`), e um log traz
  `trace_id`/`span_id` clicável até o trace (correlação log↔trace — FR-OBS-03).
- **Diagnóstico rápido:** **401** na ingestão → token (RN-OBS-02); **sem dados** → conferir endpoint
  (base **sem** `/v1`), protocolo `http/protobuf` e o `OTEL_SERVICE_NAME`.
- **Dev:** o mesmo, apontando para o `otel-lgtm` local em `http://localhost:3000`.

---

## 9. Impacto em specs/documentos existentes

- **[Roadmap §1](../../03-tarefas-e-roadmap.md)** — nova capacidade **"Observabilidade (OpenTelemetry)"**
  (✅ em produção — `dsc-eq14` no Grafana) + **Incremento F**.
- **[Roadmap §4](../../03-tarefas-e-roadmap.md)** — o item transversal **"Observabilidade"** ("avaliar
  métricas/health além de `/ping`") passa a ser **endereçado por esta spec**.
- **[Plano técnico §9](../../02-plano-tecnico.md)** — **ADR-18** registrada (agente auto + camada manual +
  backend central + logs OTLP).
- **[CLAUDE.md](../../../../CLAUDE.md)** — marco e novas env registrados no "Estado atual"; novo pacote
  `observabilidade/`.
- **Sem migration** — esta spec **quebra de propósito** o padrão das últimas (V8→V13): é infra/config +
  uma camada de código fina, não altera o schema (Art. IV/V permanecem intactos).

---

## 10. Próximos passos

1. ✅ **Feito (2026-07-24):** ativado em produção pelo **portal da disciplina** — `dsc-eq14` recebendo
   no Grafana. Runbook e troubleshooting em **§11**.
2. **(Futuro)** mais **spans de negócio** (ex.: `ViagemService.designar`, onboarding do bot) reusando o
   `RastreamentoService`; **métricas de negócio** via Micrometer/OTel SDK; correlação da telemetria com
   a auditoria (`log_auditoria`) e com o WhatsApp (SPEC-WPP-01).

> **Nota sobre o agente pinado:** a versão fixada (`v2.30.0`, embute o SDK OTel 1.64.0) segue o Art. I.
> Atualizações do agente são uma mudança **consciente** (bump do `ARG OTEL_AGENT_VERSION` no
> Dockerfile), nunca um `latest` que possa quebrar o build de prod sem aviso. A **API** OTel usada pela
> app (1.43.0, do BOM do Spring Boot) é ≤ à do agente — compatível.

---

## 11. Runbook de ativação em produção + troubleshooting (lições da 1ª ativação)

Registro da **primeira ativação real** (2026-07-24) para **reuso em outros projetos JVM**. Duas
armadilhas consumiram a maior parte do tempo — estão na tabela de troubleshooting (§11.4).

### 11.1 Como a infra da disciplina realmente ativa (importante)

O CI **não sincroniza** o `docker-compose.prod.yml` do repositório com o servidor: o container de cada
equipe é gerido por um **portal da disciplina**, que expõe um **editor de variáveis de ambiente
(`.env`)**. Ao **"Salvar e aplicar"**, o portal grava o `.env` **e recria o container**
automaticamente (é o mesmo mecanismo por onde já chegam as credenciais do banco). Ou seja: **a
telemetria é ligada por esse portal — não por SSH nem editando o compose do repo.**

> Consequência para o repo: as `OTEL_*`/`JAVA_TOOL_OPTIONS` que estão no `docker/docker-compose.prod.yml`
> servem de **documentação/rede de segurança** e valem para o **dev**; em **prod**, quem vale é o `.env`
> do portal.

### 11.2 Passo a passo (portal da equipe)

1. Portal da equipe → card **"Variáveis de ambiente"** → **Editar variáveis**.
2. Acrescente ao `.env` (mantendo as chaves de banco/Google), copiando de [`.env.example`](../../../../.env.example):
   ```
   OTEL_SERVICE_NAME=dsc-eqNN
   OTEL_EXPORTER_OTLP_ENDPOINT=https://otel.dsc.rodrigor.com
   OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
   OTEL_EXPORTER_OTLP_HEADERS=Authorization=Bearer <TOKEN_DA_TURMA>
   OTEL_TRACES_EXPORTER=otlp
   OTEL_METRICS_EXPORTER=otlp
   OTEL_LOGS_EXPORTER=otlp
   JAVA_TOOL_OPTIONS=-javaagent:/app/opentelemetry-javaagent.jar
   ```
3. **Salvar e aplicar** (recria o container).
4. Validar (§11.3).

### 11.3 Como validar sem SSH (Dozzle + Grafana)

A infra tem **Dozzle** (visualizador de logs, *read-only* — não edita nada). No **boot** do container
`eqNN` devem aparecer, nesta ordem:
- `Picked up JAVA_TOOL_OPTIONS: -javaagent:/app/opentelemetry-javaagent.jar` — a variável chegou **e** o valor está correto (**com** o hífen);
- `[otel.javaagent ...] opentelemetry-javaagent - version: 2.30.0` — o agente subiu;
- `Started CaladriusApplication ...` — sem erro de JVM.

No **Grafana** (`otel.dsc.rodrigor.com`): em **Dashboards → JVM Overview** o dropdown **Job** passa a
listar `dsc-eqNN`; em **Explore → Tempo/Loki** filtre `service.name=dsc-eqNN` (**traces e logs vivem no
Explore**; as métricas ficam nos Dashboards).

### 11.4 Troubleshooting — as armadilhas reais

| Sintoma (log/Grafana) | Causa | Correção |
|---|---|---|
| `dsc-eqNN` não aparece; app sobe normal; **sem** `Picked up JAVA_TOOL_OPTIONS` no boot | A variável não chega ao container **ou** o **nome está errado**. O correto é **`JAVA_TOOL_OPTIONS`** — **`TOOL` no singular**; o typo `JAVA_TOOLS_OPTIONS` faz a JVM **ignorar silenciosamente** (app sobe, mas sem agente). | Corrigir o nome; garantir que o `.env` chega ao container (o portal já faz ao "aplicar"; em Compose próprio, `env_file: [.env]` ou listar em `environment:`). |
| `Unrecognized option: javaagent:...` → `Could not create the Java Virtual Machine` → **container em crash-loop** (app fora do ar) | O valor está **sem o hífen** inicial. `-javaagent` é opção da JVM e **exige** o `-`. | Valor exato: **`-javaagent:/app/opentelemetry-javaagent.jar`** — o `-` vem logo após o `=`. |
| Agente sobe no boot, mas **sem dados** no Grafana; log com `401`/`Unauthorized` | Token ausente/errado, ou **aspas** no valor do header. | `OTEL_EXPORTER_OTLP_HEADERS=Authorization=Bearer <token>` — **sem aspas** (o `.env` lê literal). |
| Nada em **Dashboards**, mas não olhou em **Explore** | Dashboards mostram **métricas** por Job; **traces/logs** vivem no **Explore**. | Use **Explore → Tempo** (traces) e **Loki** (logs) filtrando `service.name`. |

### 11.5 Lição para reuso (qualquer projeto JVM)

O par que liga a telemetria é **`JAVA_TOOL_OPTIONS=-javaagent:<caminho-do-agente-na-imagem>`** — os dois
pontos que custam tempo são o **nome** (`TOOL`, singular) e o **hífen** do valor. O resto é `OTEL_*` no
ambiente. Em qualquer infra que injete o `.env` no ambiente do container (portal, Portainer, Compose com
`env_file`/`environment`), basta essas chaves **chegarem ao container** e o `.jar` do agente existir na
imagem — **sem tocar no código** da aplicação. Diagnóstico é sempre o mesmo: **log de boot do container**
(`Picked up ...` + `opentelemetry-javaagent - version`) e o **dropdown/filtro por `service.name`** no painel.
