# 04 — Feature toggle (SPEC-PLT-01)

Testes do mecanismo de **ligar/desligar funcionalidades em runtime**, sem redeploy
([SPEC-PLT-01](../sdd/specs/plataforma/SPEC-PLT-01-feature-toggle.md), ADR-17). Foram escritos **antes** da
implementação (TDD): os cenários são a tradução direta das regras `RN-FLG-*` e dos critérios
`CA-FLG-*` da spec.

| Peça | Teste | Tipo |
|---|---|---|
| `FeatureFlagService` (flags + parâmetros + cache) | `FeatureFlagServiceTest` | unitário |
| `MunicipioService` (entitlement de pagamento) | `MunicipioServiceTest` | unitário |
| Telas, manutenção, parâmetros e adesão | `FeatureToggleWebTest` | API |
| Bot on/off | `WhatsappWebhookControllerTest` | API |

> **A propriedade mais importante de um kill switch é não quebrar.** Metade dos cenários abaixo
> existe para provar exatamente isso: configuração ausente, torta ou fora do intervalo **nunca**
> derruba o sistema — cai no default do código (RN-FLG-02).

---

## `FeatureFlagServiceTest` — 13 cenários (unitário/TDD)

### Flags globais (`feature.*`)

| Cenário | Regra | Protege |
|---|---|---|
| chave ausente ⇒ default do código (bot ligado, manutenção desligada) | RN-FLG-02 | banco novo/limpo opera igual ao de sempre |
| lê `'true'`/`'false'` ignorando espaço e caixa | — | tolerância a valor digitado à mão no banco |
| valor inválido (`'talvez'`) ⇒ default, **sem lançar** | RN-FLG-02 | config torta não pode derrubar o filtro que roda em **toda** requisição |
| a 2ª leitura **não vai ao banco** | RN-FLG-01 | o modo de manutenção é consultado a cada request — sem cache seria uma consulta por request |
| salvar **invalida o cache**; a leitura seguinte já vê o novo valor | RN-FLG-08 | efeito imediato, sem restart |
| alterar gera **auditoria** com antes → depois | RN-FLG-07 | quem desligou o bot e quando |
| `estadoDasFlags` devolve todas as flags com o valor efetivo | — | alimenta `/admin/features` |

### Parâmetros de negócio (`param.*`)

| Cenário | Regra | Protege |
|---|---|---|
| ausente ⇒ default (10 min, 5 tentativas, 60 s) | RN-FLG-02 | os valores de fábrica são os que o código já usava |
| lê o valor configurado dentro do intervalo | — | |
| **fora do intervalo (999 min) ⇒ default** | RN-FLG-06 | uma configuração absurda não vira comportamento absurdo |
| não numérico (`'dez'`) ⇒ default | RN-FLG-02 | |
| `definir` grava, invalida o cache e audita | RN-FLG-07/08 | |
| **gravar fora do intervalo é recusado** (nada é salvo, nada é auditado) | RN-FLG-06 | a leitura é tolerante, a **escrita** é rígida — o erro aparece para quem está configurando |

---

## `MunicipioServiceTest` — 5 cenários (unitário/TDD)

O *entitlement* por município (`municipios.pagamento_habilitado`, migration **V15**).

| Cenário | Protege |
|---|---|
| listar sem termo devolve tudo; com termo, filtra por nome | a lista da PB tem 223 municípios |
| marcar adesão persiste **e audita** (RN-FLG-05/07) | decisão comercial rastreável |
| município inexistente ⇒ 404 de domínio, **sem salvar nem auditar** | |
| passageiro de município aderido é elegível; de município comum, não | **RN-FLG-05** — a avaliação é pelo município de **origem** (endereço, SPEC-CAD-04) |
| **sem endereço (ou sem município) ⇒ `false`** | **RN-FLG-02** — o default seguro aqui é "não cobra" |

---

## `FeatureToggleWebTest` — 10 cenários (API)

Sobem o contexto real e exercem os critérios de aceite de ponta a ponta.

| Cenário | Critério | Protege |
|---|---|---|
| `/admin/features` abre para SYSADMIN; **GERENTE recebe 403** | FR-FLG-01 | a flag é um gate **adicional**, não uma via de escalonamento (§10 da spec) |
| alternar a flag vale **na hora** e fica na auditoria | **CA-FLG-06** | efeito imediato + rastreabilidade |
| **em manutenção, o GERENTE recebe 503** (página de manutenção) e o **SYSADMIN continua entrando** | **CA-FLG-01** | é o SYSADMIN que religa o sistema — se ele também fosse barrado, o kill switch seria irreversível pela interface |
| **`/ping` e `/actuator/health` continuam 200** em manutenção | **CA-FLG-02** | contrato público da disciplina (Art. XIII): manutenção não pode parecer queda para o monitoramento |
| requisição **HTMX** em manutenção → 503 com `HX-Redirect` | — | senão a página de manutenção seria injetada dentro de um fragmento |
| salvar parâmetro grava e passa a valer | FR-FLG-02 | |
| valor **fora do intervalo** é recusado com mensagem e o anterior permanece | **CA-FLG-04** | |
| valor **não numérico** devolve erro sem quebrar | RN-FLG-02 | |
| flag **desconhecida** na URL vira 400 | — | a rota aceita só o catálogo `ChaveFeature` |
| marcar a adesão de um município persiste o entitlement | **CA-FLG-05** | |

> **Cuidado que este arquivo documenta:** flags são **estado global** compartilhado pelo contexto do
> Spring. Todos os cenários restauram os padrões em `@AfterEach` — sem isso, um teste deixaria o
> sistema "em manutenção" e derrubaria os seguintes com 503.

---

## Bot on/off — em `WhatsappWebhookControllerTest`

**CA-FLG-03**: com `feature.bot_whatsapp = false`, uma mensagem que chega ao webhook é
**registrada** (idempotência preservada), o bot **não** é acionado e a resposta é **200** — para a
Evolution não re-tentar. As notificações de **saída** (aprovação/recusa do gestor) seguem
funcionando: a flag pausa o **atendimento automático**, não o canal.

---

## O que ainda não é testado

- **Cache distribuído**: a invalidação é por instância (**DT-18**). Com réplicas, desligar uma flag
  em uma não afeta a outra. Hoje roda um container por ambiente — aceito e registrado.
- **Janela de atendimento do WhatsApp** (**DT-16**): o horário é persistido mas ainda não aplicado;
  quando virar parâmetro da SPEC-PLT-01, entra aqui.
