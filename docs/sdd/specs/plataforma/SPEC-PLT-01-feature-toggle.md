# SPEC-PLT-01 — Feature Toggle (flags de funcionalidade)

| | |
|---|---|
| **Área** | `FLG` |
| **Papéis** | SYSADMIN (gerencia as flags globais e de manutenção); GERENTE (parâmetros de negócio e adesão de municípios); todos (sofrem o efeito) |
| **Status geral** | ✅ **Implementado (2026-07-29).** Migration **V15** só para o entitlement por município; flags globais e parâmetros **reusam** `configuracoes_sistema` (V5). Decisões D1–D6 em §4 (com o ajuste D7 em §4). |
| **Código** | `FeatureFlagService` (fachada sobre `ConfiguracaoService`, com cache, defaults e auditoria), catálogos `ChaveFeature` e `ParametroSistema`, `ManutencaoFilter` + `ManutencaoController` + `templates/manutencao.html`, gate no `WhatsappWebhookController`, `MunicipioService` (entitlement) e coluna `municipios.pagamento_habilitado` (V15); telas `/admin/features` e `/admin/municipios`. `VerificacaoService`/`ConviteService` passaram a ler os parâmetros do toggle. |
| **Constituição** | Artigos II (camadas), IV (migrations forward-only), VI (UUID/enums/VARCHAR), VII (RBAC), IX (português), X (HTMX), XI (segurança), XIII (`/ping` público), XIV (ambiente compartilhado) |
| **Relacionada** | Configuração do Sistema (`ConfiguracaoSistema`/sessão dinâmica — #18, ADR-10), [SPEC-WPP-01](../whatsapp/SPEC-WPP-01-integracao-whatsapp.md)/[SPEC-WPP-02](../whatsapp/SPEC-WPP-02-solicitacao-sob-demanda-e-onboarding-whatsapp.md) (bot), [SPEC-CAD-04](../cadastros/SPEC-CAD-04-endereco-do-passageiro.md) (`Municipio`), [SPEC-ACE-03](../acesso/SPEC-ACE-03-verificacao-de-contato-e-recuperacao-de-senha.md) (parâmetros de OTP) · **ADR-17** (proposta, §4) |

---

## 1. A lacuna que esta spec cobre

O `docs/checklist.md` marca **Feature toggle** como 🟡 **parcial**: já há **config dinâmica** no banco
(`ConfiguracaoSistema`/`ConfiguracaoService` + sessão dinâmica) e **beans condicionais por variável de
ambiente** (Google, WhatsApp `@ConditionalOnExpression`, flag da SPEC-ACE-03), mas **falta** um mecanismo
de *feature flags* de **runtime** — ligar/desligar funcionalidades **pela área administrativa, sem
redeploy**. Esta spec fecha isso, com escopo enxuto (reaproveitando o que existe), movendo o item para ✅.

---

## 2. Escopo

### 2.1 Inclui (o conjunto escolhido pelo dono do projeto)
1. **Bot do WhatsApp on/off** — *kill switch* de operação: pausar o atendimento automático sem
   derrubar a app nem exigir deploy.
2. **Modo de manutenção** — *kill switch* global: coloca o sistema fora do ar para os usuários comuns
   (página de manutenção), mantendo o **SYSADMIN** operando para religar.
3. **Pagamento apenas para municípios que aderiram** — *entitlement* por `Municipio`: um marcador de
   adesão que uma futura feature de pagamento consultará (o **pagamento em si está fora do escopo** —
   ver `docs/checklist.md`, item ⬜).
4. **Config toggles — parâmetros de negócio** — externalizar constantes hoje "chumbadas" no código
   (validade/tentativas/cooldown do OTP, validade do token de convite, limites de senha, etc.) para
   `configuracoes_sistema`, editáveis pelo gestor, **sempre com default seguro**.

### 2.2 Não inclui (fica para depois)
- Os demais exemplos discutidos (auto-cadastro on/off, canais de notificação individuais, A/B de
  mensagens, canary, telas beta por papel/município) — podem entrar como novas chaves reusando o mesmo
  `FeatureFlagService`, mas **não** nesta spec.
- A **integração de pagamento** (Mercado Pago) — é outra spec/incremento (⬜ no checklist); aqui só se
  cria o **entitlement** que ela vai consultar.
- Biblioteca externa de feature flags (**Togglz descartado** — peso desnecessário; ver ADR-17).

---

## 3. Tipos de toggle (mapeamento)

| Item | Tipo (taxonomia clássica) | Onde vive | Efeito |
|---|---|---|---|
| Bot on/off | *Ops / kill switch* | `configuracoes_sistema` (`feature.bot_whatsapp`) | Webhook responde 200 e **não aciona** o bot |
| Modo de manutenção | *Ops / kill switch* | `configuracoes_sistema` (`feature.modo_manutencao`) | Filtro barra não-SYSADMIN |
| Pagamento por município | *Permission / entitlement* | `municipios.pagamento_habilitado` (V15) | Gate por município de origem do passageiro |
| Parâmetros de negócio | *Config* | `configuracoes_sistema` (`param.*`) | Serviços leem com fallback ao default |

---

## 4. Decisões de modelo (propostas) — base da **ADR-17**

### D1 — Onde moram as flags globais e os parâmetros → **✅ Reusar `configuracoes_sistema`**
Não cria tabela nova: as flags booleanas (`feature.*`) e os parâmetros (`param.*`) são **linhas
chave/valor** na `configuracoes_sistema` (V5), mesma engine da configuração de sessão dinâmica e das
configs do WhatsApp (SPEC-WPP-02). Segue o ADR-10 (config dinâmica no banco).

### D2 — `FeatureFlagService` com **cache** e **default seguro**
Uma fachada sobre o `ConfiguracaoService` expõe `ativo(chave)` (boolean) e `parametro(chave, default)`
(tipado), com **cache em memória** invalidado ao salvar. **Toda** leitura tem um **default no código**:
chave ausente/valor inválido ⇒ usa o default (nunca quebra) — princípio *fail-safe* de um kill switch.

### D3 — Entitlement de pagamento → **✅ Coluna `municipios.pagamento_habilitado` (V15)**
Como o pagamento é a **única** feature por-município prevista, um `boolean NOT NULL DEFAULT false` em
`municipios` é o mais simples. Se surgirem mais features por município, migra-se para uma tabela
`municipio_features(municipio, feature)` (registrar DT). A futura feature de pagamento consulta a adesão
pelo **município de origem** do passageiro (endereço — SPEC-CAD-04).

### D4 — Parâmetros de negócio → **✅ Config com fallback ao default constante**
Os serviços passam a ler os parâmetros do `FeatureFlagService` **em vez de** constantes fixas, mantendo
as constantes atuais como **default**. Valor fora do intervalo válido ⇒ default (RN-FLG-06).

### D5 — Modo de manutenção → **✅ Filtro que libera o SYSADMIN**
Um `ManutencaoFilter` (após a autorização) devolve a **página de manutenção** (HTTP **503**) para
qualquer requisição quando `feature.modo_manutencao = true`, **exceto**: papel **SYSADMIN**, `/login`,
`/logout`, `/ping` (contrato — Art. XIII), `/actuator/health` e estáticos. Assim o SYSADMIN entra e
**desliga** o modo.

### D7 — Adesão de município → **SYSADMIN** (ajuste feito na implementação)
A FR-FLG-03 previa a tela para **GERENTE/SYSADMIN**. Ela ficou em `/admin/municipios`, ou seja,
**só SYSADMIN**: adesão ao pagamento é decisão **comercial da plataforma**, não operação diária da
secretaria — e assim nenhuma exceção precisou ser aberta no `SecurityConfig` (`/admin/**` continua
exclusivo do SYSADMIN). Reversível se a operação pedir.

### D6 — Bot on/off → **✅ Gate no webhook (200 + no-op)**
Com `feature.bot_whatsapp = false`, o `WhatsappWebhookController` **registra a mensagem** (idempotência,
RN-WPP-03) mas **não** chama o bot e responde **200** (a Evolution não re-tenta). Opcional (decisão de
UX a confirmar): enviar **uma** mensagem de cortesia ("atendimento pausado, tente mais tarde"). O canal
de **notificação de saída** (aprovação/recusa do gestor) **não** é afetado por esta flag.

---

## 5. Modelagem

### 5.1 Migration V15 (proposta) — só o entitlement
```
ALTER TABLE municipios
    ADD COLUMN pagamento_habilitado BOOLEAN NOT NULL DEFAULT false;
```
Aditiva (Art. IV), sem extensões (Art. XIV). Nada mais precisa de schema: flags e parâmetros são linhas
em `configuracoes_sistema`.

### 5.2 Chaves (constantes em `ChaveFeature`)
| Chave | Tipo | Default | Consumidor |
|---|---|---|---|
| `feature.bot_whatsapp` | boolean | `true` | `WhatsappWebhookController` (D6) |
| `feature.modo_manutencao` | boolean | `false` | `ManutencaoFilter` (D5) |
| `param.otp.validade_minutos` | int | `10` | `VerificacaoService` |
| `param.otp.max_tentativas` | int | `5` | `VerificacaoService` |
| `param.otp.cooldown_segundos` | int | `60` | `VerificacaoService` |
| `param.convite.validade_dias` | int | `7` | `ConviteService` |
| `param.senha.min` / `param.senha.max` | int | `6` / `72` | reset/registro |

> Precedente: **nome de exibição**, **modelo de mensagem** e **horário de atendimento** do WhatsApp já
> são config (SPEC-WPP-02) — mesmo padrão, agora generalizado.

---

## 6. Regras de negócio

| Regra | Descrição |
|---|---|
| **RN-FLG-01** | Flags globais são booleanos em `configuracoes_sistema` (chave `feature.*`), lidos pelo `FeatureFlagService` com **cache**. |
| **RN-FLG-02** | **Fail-safe:** toda flag/parâmetro tem **default no código**; valor ausente ou inválido ⇒ usa o default (o sistema **nunca** quebra por config faltante ou torta). |
| **RN-FLG-03** | **Modo de manutenção** ON ⇒ requisições de não-SYSADMIN recebem a **página de manutenção (503)**, exceto `/login`, `/logout`, `/ping`, `/actuator/health` e estáticos; o **SYSADMIN** opera normalmente. |
| **RN-FLG-04** | **Bot** OFF ⇒ o webhook registra a mensagem (idempotência) e responde **200 sem acionar o bot**; a notificação de **saída** (aprovação/recusa) segue funcionando. |
| **RN-FLG-05** | **Entitlement de pagamento** é por `Municipio` (`pagamento_habilitado`, default `false`); só municípios marcados entram no (futuro) fluxo de pagamento, avaliado pelo **município de origem** do passageiro. |
| **RN-FLG-06** | **Parâmetros de negócio** são lidos da config com **validação de intervalo** (ex.: OTP validade 1–60 min, tentativas 1–10); fora do intervalo ⇒ default (RN-FLG-02). |
| **RN-FLG-07** | Toda **alteração de flag/parâmetro/adesão** é **auditada** (`AuditoriaService`, categoria SEGURANCA/OPERACAO): quem, quando, valor antigo → novo. |
| **RN-FLG-08** | Salvar uma flag **invalida o cache** imediatamente (efeito na hora, sem restart). |

---

## 7. Requisitos funcionais

- **FR-FLG-01** — `/admin/features` (SYSADMIN) lista as flags globais com o estado atual e permite
  **ligar/desligar** (HTMX), auditando a mudança.
- **FR-FLG-02** — `/admin/configuracoes` (existente) ganha uma seção de **parâmetros de negócio**
  editáveis (com validação de intervalo).
- **FR-FLG-03** — Uma tela (GERENTE/SYSADMIN) permite **marcar municípios** como aderidos ao pagamento
  (`pagamento_habilitado`).
- **FR-FLG-04** — Um `ManutencaoFilter` aplica a RN-FLG-03; a página de manutenção é pública e estática.
- **FR-FLG-05** — O `WhatsappWebhookController` consulta `feature.bot_whatsapp` antes de acionar o bot
  (RN-FLG-04).
- **FR-FLG-06** — Os serviços (`VerificacaoService`, `ConviteService`, …) leem os parâmetros via
  `FeatureFlagService` em vez das constantes fixas, preservando os defaults atuais.

---

## 8. Critérios de aceite (Dado / Quando / Então)

- **CA-FLG-01 — Manutenção barra usuário comum, libera SYSADMIN**
  *Dado* `feature.modo_manutencao = true`, *Quando* um GERENTE acessa `/viagens`, *Então* recebe a
  página de manutenção (503); *Quando* o SYSADMIN acessa `/admin/features`, *Então* entra normalmente.
- **CA-FLG-02 — `/ping` imune à manutenção**
  *Dado* o modo de manutenção ligado, *Quando* qualquer cliente faz `GET /ping`, *Então* recebe **200**
  (contrato — Art. XIII).
- **CA-FLG-03 — Bot desligado não responde, mas não perde a mensagem**
  *Dado* `feature.bot_whatsapp = false`, *Quando* chega uma mensagem no webhook, *Então* ela é
  registrada, o bot **não** é acionado e o webhook responde **200**.
- **CA-FLG-04 — Default seguro**
  *Dado* uma chave de parâmetro **ausente** ou com valor inválido, *Quando* o serviço a lê, *Então* usa
  o **default do código** (sem erro).
- **CA-FLG-05 — Entitlement por município**
  *Dado* o município A com `pagamento_habilitado = true` e o B com `false`, *Então* só passageiros de A
  são elegíveis ao (futuro) fluxo de pagamento.
- **CA-FLG-06 — Efeito imediato + auditoria**
  *Quando* o SYSADMIN liga/desliga uma flag, *Então* o efeito vale na próxima requisição (cache
  invalidado) e há registro de auditoria da mudança.

---

## 9. RBAC e rotas (proposta)

| Método | Rota | Acesso | Ação |
|---|---|---|---|
| GET/POST | `/admin/features` | SYSADMIN | Ver/alternar flags globais (bot, manutenção) |
| GET/POST | `/admin/configuracoes` | SYSADMIN | Parâmetros de negócio (seção nova) |
| GET/POST | `/admin/municipios` (ou aba) | GERENTE/SYSADMIN | Marcar adesão ao pagamento |

Padrão HTMX (Art. X). A **página de manutenção** é pública.

---

## 10. Segurança (Art. XI)

- **Fail-safe** (RN-FLG-02): kill switch nunca deixa o sistema num estado quebrado por config.
- **Modo de manutenção não vaza dados**: barra **antes** de renderizar telas protegidas; mantém só o
  mínimo público (`/login`, `/ping`, saúde, estáticos).
- **Auditoria** de toda mudança de flag (RN-FLG-07) — rastreável no painel de auditoria existente.
- Flags **não** substituem RBAC: são um gate **adicional**, não uma via de escalonamento.

---

## 11. Testes ✅ (entregues — detalhamento em [`docs/testes/04-feature-toggle.md`](../../../testes/04-feature-toggle.md))

Escritos **antes** da implementação (TDD):

- **`FeatureFlagServiceTest`** (13 cenários, unit): `ativo`/`parametro` com cache; default seguro para
  chave ausente/valor inválido/fora do intervalo; invalidação do cache ao salvar; auditoria da
  mudança; recusa de escrita fora do intervalo.
- **`MunicipioServiceTest`** (5 cenários, unit): adesão persistida e auditada; elegibilidade pelo
  município de origem; sem endereço ⇒ `false` (default seguro).
- **`FeatureToggleWebTest`** (10 cenários, MockMvc + Testcontainers): RBAC das telas; manutenção
  barrando o GERENTE (503 + página) e liberando o SYSADMIN; `/ping` e `/actuator/health` imunes;
  variante HTMX (`HX-Redirect`); parâmetros válidos/inválidos; adesão de município.
- **Bot off** (`WhatsappWebhookControllerTest`): flag off ⇒ registra a mensagem, **não** aciona o bot,
  responde 200.
- **Contexto** (Testcontainers): schema **V1→V15** válido com `ddl-auto: validate`.

**Resultado:** 346 testes verdes na suíte; cobertura global **87,3% de linhas** (gate de 85% no
`mvn verify`).

---

## 12. Impacto e rastreabilidade

- **`docs/checklist.md`**: item "Feature toggle" 🟡 → ✅ quando implementado.
- **Plano técnico**: registrar **ADR-17**.
- **SPEC-WPP-02**: a flag `feature.bot_whatsapp` e o horário de atendimento (hoje persistido mas não
  aplicado) convergem — a janela pode passar a silenciar o bot.
- **SPEC-ACE-03**: os parâmetros do OTP passam a ser config (mantendo os defaults 10 min / 5 / 60 s).

| Requisito | Artefato (a criar/editar) |
|---|---|
| FR-FLG-01/02/06 | `FeatureFlagService`, `ChaveFeature`, `ConfiguracaoService`, telas `/admin` |
| FR-FLG-03, RN-FLG-05 | `municipios.pagamento_habilitado` (V15), `MunicipioRepository`, tela de adesão |
| FR-FLG-04, RN-FLG-03 | `ManutencaoFilter`, `SecurityConfig`, página de manutenção |
| FR-FLG-05, RN-FLG-04 | `WhatsappWebhookController`, `BotAtendimentoService` |
| RN-FLG-07 | `AuditoriaService` |

---

## 13. Próximos passos

1. Aprovar **ADR-17** e confirmar a **D6** (bot off: silencioso × mensagem de cortesia).
2. `FeatureFlagService` + `ChaveFeature` (sobre `ConfiguracaoService`, com cache).
3. Modo de manutenção (`ManutencaoFilter` + página) e `/admin/features`.
4. Gate do bot no webhook.
5. Migration **V15** + adesão de municípios ao pagamento.
6. Externalizar os parâmetros de negócio (com defaults) + seção no `/admin/configuracoes`.
7. Testes (§11) + gate `docker build`; atualizar `checklist.md` (🟡 → ✅) e o roadmap.
