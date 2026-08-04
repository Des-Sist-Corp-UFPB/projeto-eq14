# SPEC-OPE-02 — Healthcheck de banco e analytics de uso (Umami)

| | |
|---|---|
| **Área** | `OBS` (observabilidade/operação) |
| **Papéis** | SYSADMIN (variáveis no deploy); professor/avaliador (leitura do código e dos endpoints); equipe (painel do Umami). **Sem papel de usuário final** — é capacidade transversal. |
| **Status geral** | ✅ **Implementada (código, 2026-07-31)** — `BancoHealthIndicator` + `/ping` com o campo `database`; fragmento `fragments/analytics.html` com o rastreador do Umami. **Sem migration.** **Pendente**: preencher `UMAMI_*` no `.env` do servidor e recriar o container (§8). |
| **Constituição** | Art. XI (segurança — nenhum segredo no repositório; nada que exponha token em URL a terceiros), Art. XII (o gate do CI é a compilação), **Art. XIII (`/ping` e Actuator são contrato — podem ser enriquecidos, nunca quebrados)**, Art. XIV (reusar a infra central da disciplina em vez de subir infra própria) |
| **Relacionada** | [SPEC-OPE-01](SPEC-OPE-01-observabilidade-opentelemetry.md) (mesma área; a telemetria diz *como o sistema se comporta*, esta spec diz *se ele está vivo* e *como é usado*), [SPEC-ACE-02](../acesso/SPEC-ACE-02-login-social-google.md)/[SPEC-WPP-01](../whatsapp/SPEC-WPP-01-integracao-whatsapp.md) (padrão de **ativação condicional por variável de ambiente**), [SPEC-ACE-03](../acesso/SPEC-ACE-03-verificacao-de-contato-e-recuperacao-de-senha.md) (é dela que vêm as URLs com token que o §6.2 protege) |
| **Código** | `health/BancoHealthIndicator`; `controller/PingController`; `dto/ConfiguracaoUmami`; `config/GlobalModelAttributes`; `templates/fragments/analytics.html` + inclusão em `layout.html` e nas telas públicas; `application.yml` (`show-components`, `caladrius.health.banco.timeout-segundos`); `.env.example`; `docker/docker-compose.prod.yml`. **Testes**: `BancoHealthIndicatorTest`, `ConfiguracaoUmamiTest`, `PaginasPublicasTest`, `AnalyticsUmamiWebTest`. **Nenhuma migration.** |

---

## 1. A lacuna que esta spec cobre

A avaliação da disciplina passou a cobrar dois itens que o projeto **tinha pela metade**:

| Item cobrado | Estado antes desta spec |
|---|---|
| **HC** — healthcheck que **consulta o banco**, *lido do código* | `/ping` devolvia um JSON **fixo**, sem tocar no banco. O `/actuator/health` do Spring verificava o `DataSource`, mas (a) com `show-details: when-authorized` um probe anônimo via só `{"status":"UP"}`, e (b) a consulta acontece **dentro do framework** — não há nada no nosso repositório que comprove a verificação. |
| **Uma** — analytics de uso com **Umami** | Nada. O site `eq14` já estava criado no painel central (`umami.dsc.rodrigor.com`), mas nenhuma página carregava o rastreador. |

O detalhe que define o desenho do primeiro item é **"lido do código"**: o avaliador abre o
repositório e procura o trecho que consulta o banco. Uma linha de configuração que revela o
indicador embutido do Spring **não atende** — a evidência precisa estar em código nosso, legível,
e é isso que o `BancoHealthIndicator` entrega.

---

## 2. Decisão — indicador próprio × `DataSourceHealthIndicator` do Spring

**Decisão: os dois convivem.**

O Spring Boot registra sozinho o componente `db` sempre que existe um `DataSource`. Ele continua
lá. O `BancoHealthIndicator` é **somado** a ele, como componente `banco`, por três razões:

1. **Evidência legível.** A consulta (`SELECT 1`), o timeout e o tratamento de falha estão numa
   classe de ~40 linhas do projeto, com Javadoc explicando o porquê.
2. **Timeout sob nosso controle.** O indicador embutido usa o `JdbcTemplate` da aplicação, sem
   teto próprio; aqui o teto é explícito e configurável (§4.2).
3. **Reuso pelo `/ping`.** O `PingController` precisa de um `boolean`, não de um `Health`. O método
   `acessivel()` dá isso sem que o controller conheça o Actuator.

O custo é uma consulta extra por chamada de health — desprezível (o healthcheck do container roda
a cada 30 s).

---

## 3. Decisão — `/ping` nunca devolve 503

O `/ping` é o **contrato público da disciplina** (Art. XIII) e alimenta monitores de
disponibilidade. Se ele passasse a devolver `503` quando o banco cai, a aplicação inteira seria
marcada como **fora do ar** por causa de uma dependência — e o `/ping` deixaria de cumprir o papel
para o qual existe: dizer se o processo está vivo.

A separação adotada é a clássica **liveness × readiness**:

| Endpoint | Pergunta que responde | Com o banco fora |
|---|---|---|
| `GET /ping` | *o processo está de pé?* | **200**, com `"database": "down"` |
| `GET /actuator/health` | *o sistema está apto a servir?* | **503**, componente `banco` em `DOWN` |

Quem age sobre a falha é o healthcheck do container, que aponta para o `/actuator/health`.

---

## 4. Desenho — healthcheck de banco

### 4.1 `BancoHealthIndicator`

```java
@Component
public class BancoHealthIndicator implements HealthIndicator {
    static final String CONSULTA = "SELECT 1";
    public boolean acessivel() { … }   // executa a consulta; false em DataAccessException
    @Override public Health health() { … }
}
```

- **`SELECT 1`, e não uma contagem de tabela.** Contar linhas de `usuarios` acoplaria a saúde do
  sistema ao schema: qualquer migration que renomeasse algo derrubaria o healthcheck sem que
  houvesse problema algum de banco.
- **`JdbcTemplate` próprio**, construído a partir do `DataSource`. `queryTimeout` é estado do
  template: alterá-lo no bean compartilhado imporia o teto do healthcheck a **todas** as consultas
  da aplicação.
- **Um único ponto de acesso ao banco** (`acessivel()`), consumido tanto por `health()` quanto pelo
  `/ping` — duas verificações independentes poderiam divergir e reportar coisas diferentes.

### 4.2 Timeout

`caladrius.health.banco.timeout-segundos` (default **2**) vira `Statement.setQueryTimeout`.

Sem teto, um banco **pendurado** (não recusando conexão, apenas não respondendo) penduraria junto
o `/ping` e o healthcheck do container — o probe externo cairia em timeout, que é um resultado
**pior** que um `DOWN` rápido e honesto: não distingue "app morta" de "banco lento".

> ⚠️ **O que este timeout não cobre**: ele limita a **execução da consulta**, não a **aquisição da
> conexão** no pool (Hikari, `connectionTimeout` default 30 s). Ver **DT-27**.

### 4.3 `/ping`

```json
{"status":"ok","service":"eq14","database":"up","timestamp":"2026-07-31T12:00:00Z"}
```

Os três campos históricos são preservados (`PaginasPublicasTest` trava isso); `database` é o campo
novo, alimentado pelo indicador.

### 4.4 Visibilidade para um probe anônimo

`show-components: always` no `application.yml`. Assim `/actuator/health` devolve os **componentes**
(nome + status) para qualquer um, enquanto `show-details` continua `when-authorized` — os detalhes
(vendor do banco, espaço em disco) seguem restritos.

---

## 5. Desenho — analytics de uso (Umami)

### 5.1 O que é e por que Umami

Ferramenta de *web analytics* **open-source e self-hosted**, alternativa ao Google Analytics.
Registra pageviews, sessões, referrers, navegador e país, **sem cookies e sem dado pessoal** — o
que dispensa banner de consentimento e conversa com o item de LGPD do
[checklist](../../../checklist.md). A instância é **central da disciplina**
(`https://umami.dsc.rodrigor.com`); a equipe não sobe infra (Art. XIV).

### 5.2 Ativação condicional

Mesmo padrão de SPEC-ACE-02/10/14: `UMAMI_URL` + `UMAMI_WEBSITE_ID` ausentes ⇒ `ConfiguracaoUmami`
nasce desligada e **nenhum script é renderizado**. É isso que mantém desenvolvimento e a suíte de
testes fora da estatística de produção.

`ConfiguracaoUmami.de(...)` normaliza a URL base (presume `https://`, remove barra final). Isso
não é zelo gratuito: os painéis exibem o endereço **sem** esquema, é isso que se cola no `.env`, e
o resultado seria um `src` **relativo** — o navegador pediria o script à própria aplicação, ele
não carregaria e **não haveria erro visível**. Foi exatamente o que derrubou o boot na SPEC-WPP-01.

### 5.3 Onde o rastreador entra

O `layout.html` cobre a área autenticada, mas **8 telas não usam o layout** — inclusive todo o
funil de entrada. O fragmento é incluído em:

| Tela | Rastreada | Motivo |
|---|:---:|---|
| `layout.html` (toda a área logada) | ✅ | uma inclusão cobre todas as telas internas |
| `auth/login`, `auth/registro` | ✅ | funil de entrada — o tráfego anônimo que mais interessa |
| `auth/esqueci-senha`, `auth/redefinir-senha` | ✅ | recuperação de senha (sem token na URL) |
| `auth/verificar-telefone` | ✅ | fluxo OTP, sem segredo na URL |
| `manutencao` | ✅ | mede quantas pessoas bateram no sistema fora do ar |
| **`auth/ativar`** | ❌ | **`/ativar?token=…` — token de definição de senha na URL** (§6.2) |
| **`auth/verificar-email`** | ❌ | **`/verificar-email?token=…` — token de verificação na URL** (§6.2) |

---

## 6. Segurança

### 6.1 O que não é segredo

`UMAMI_WEBSITE_ID` **não é segredo** — vai no HTML de toda página, por natureza. O que tem senha é
o **painel** do Umami, e essa credencial não entra no repositório (Art. XI). O `.env.example`
registra isso explicitamente para ninguém tratar o id como segredo nem, pior, o contrário.

### 6.2 Vazamento de token pela URL — a brecha que o desenho fecha

O rastreador do Umami envia **a URL da página**. Duas telas do sistema recebem um **segredo pela
query string**:

- `GET /ativar?token=…` (`AtivacaoController`) — o token **define a senha** da conta. É o link que
  o bot do WhatsApp envia na opção "Acesso à plataforma".
- `GET /verificar-email?token=…` (`VerificacaoController`) — confirma o e-mail da conta.

Colar o script nessas páginas entregaria **credenciais válidas** ao servidor de analytics, onde
ficariam registradas em texto puro. A proteção é em **duas camadas independentes**:

1. **`data-exclude-search="true"`** no script — o Umami não envia a query string. Protege inclusive
   URLs com segredo que ainda não existem.
2. **Omissão deliberada do fragmento** nas duas telas, com comentário no HTML explicando por quê.
   É a camada que sobrevive ao caso em que a instância do Umami seja uma versão que **ignore** o
   atributo.

`AnalyticsUmamiWebTest` trava as duas camadas.

### 6.3 Restrição por domínio

`data-domains` limita a coleta ao domínio de produção. É necessário porque o Umami aceita eventos
**pelo website-id** e **não filtra a origem** por conta própria: sem isso, qualquer ambiente que
suba com as variáveis preenchidas (um clone, um túnel de teste, a SPEC-PLT-02) contaminaria a
estatística — ou, pior, permitiria a um terceiro que lesse o id poluir os dados da equipe.

### 6.4 CSP

O projeto **não define** `Content-Security-Policy` hoje (o layout já carrega Google Fonts de fora),
então nada bloqueia o script. Se um dia entrar CSP, `umami.dsc.rodrigor.com` precisa ser liberado
em `script-src` **e** `connect-src` (o rastreador faz `POST` para `/api/send`).

---

## 7. Regras de negócio

| ID | Regra |
|---|---|
| **RN-HC-01** | O healthcheck do banco é **código do projeto** e executa uma **consulta real** (`SELECT 1`) — não delega a verificação ao framework nem presume saúde a partir da existência do `DataSource`. |
| **RN-HC-02** | A consulta tem **timeout** (`caladrius.health.banco.timeout-segundos`, default 2 s). Banco pendurado vira `DOWN` rápido; nunca pendura o `/ping`. |
| **RN-HC-03** | **`/ping` responde 200 sempre**, mesmo com o banco fora, e reporta o estado no campo `database`. Quem devolve 503 é o `/actuator/health`. |
| **RN-HC-04** | O detalhe publicado no health **não** contém a mensagem do driver (host, base, usuário) — apenas a classe da exceção, no log da aplicação. |
| **RN-HC-05** | O componente do banco é visível a um probe **anônimo** (`show-components: always`), sem expor os detalhes restritos. |
| **RN-HC-06** | O contrato histórico do `/ping` (`status`, `service`, `timestamp`) **não regride**. |
| **RN-ANL-01** | Sem `UMAMI_URL` **e** `UMAMI_WEBSITE_ID`, nenhum script é renderizado e a aplicação sobe idêntica — dev e testes ficam fora da estatística. |
| **RN-ANL-02** | A coleta é **restrita por domínio** (`data-domains`) quando `UMAMI_DOMINIOS` está definida. |
| **RN-ANL-03** | O rastreador **nunca** é carregado em tela cujo endereço carregue segredo na query string, e **nunca** envia a query string (`data-exclude-search`). Vale para `/ativar` e `/verificar-email`; toda tela nova com token na URL entra nesta regra. |
| **RN-ANL-04** | O `data-website-id` **não** é tratado como segredo; a **senha do painel** do Umami nunca entra no repositório. |

---

## 8. Ativação em produção

1. No `.env` do servidor (`/home/ghactions/eq14/.env`), acrescentar:

   ```bash
   UMAMI_URL=https://umami.dsc.rodrigor.com
   UMAMI_WEBSITE_ID=18d3d5e0-9a19-426f-a74e-72f5c837fc74
   UMAMI_DOMINIOS=eq14.dsc.rodrigor.com
   ```

2. Recriar o container (portal da disciplina ou `docker compose up -d`) — o
   `docker-compose.prod.yml` já repassa as três variáveis.
3. Acessar `https://eq14.dsc.rodrigor.com` e conferir a sessão no painel do Umami (*Realtime*).

O healthcheck **não precisa de ativação**: vai junto com o deploy. Verificação:

```bash
curl -s https://eq14.dsc.rodrigor.com/ping                 # database: "up"
curl -s https://eq14.dsc.rodrigor.com/actuator/health      # components.banco.status: "UP"
```

---

## 9. Testes

| Teste | O que garante |
|---|---|
| `BancoHealthIndicatorTest` | Banco inacessível → `DOWN` sem propagar exceção; detalhes **sem** a mensagem do driver (RN-HC-04); o timeout configurado **chega ao JDBC** (RN-HC-02); a consulta independe do schema. |
| `PaginasPublicasTest` | Caminho feliz contra PostgreSQL real: `/ping` com `database=up` e contrato preservado (RN-HC-06); componente `banco` visível a anônimo (RN-HC-05); e, sem as variáveis, **nenhum** rastreador (RN-ANL-01). |
| `ConfiguracaoUmamiTest` | Desligado por padrão e com meia configuração; normalização da URL (sem esquema, barra final); `data-domains` omitido quando não configurado (RN-ANL-02). |
| `AnalyticsUmamiWebTest` | Com as variáveis: rastreador presente no login e no layout logado, com `data-exclude-search` e `data-domains`; **ausente** em `/ativar` e `/verificar-email` (RN-ANL-03). |

O caminho feliz do indicador é coberto por integração (banco real) e não por *mock* do JDBC:
simular a cadeia `Connection`/`Statement`/`ResultSet` testaria o `JdbcTemplate`, não a nossa regra.

---

## 10. Fora de escopo (registrado no roadmap)

- Navegação por **HTMX** não gera pageview (**DT-25**) — instrumentar `htmx:afterSwap` de forma
  genérica poluiria a métrica com o polling do painel WhatsApp.
- **Timeout de aquisição de conexão** do pool não coberto pelo `queryTimeout` (**DT-27**).
- `unhealthy` **não reinicia** o container (**DT-28**).
- Compose da SPEC-PLT-02 sem as variáveis do Umami (**DT-29**).
- Eventos customizados (`umami.track`) para medir conversão de funil (**DT-26**).
