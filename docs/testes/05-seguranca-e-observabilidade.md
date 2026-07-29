# 05 — Segurança e observabilidade

Dois temas que não são "features" para o usuário final, mas cuja quebra é cara: **quem entra no
sistema** e **o que conseguimos enxergar** quando algo dá errado em produção.

---

## Segurança — `SegurancaUnitTest` (11 cenários)

### `CaladriusUserDetailsService` — o login flexível (SPEC-01)

O mesmo campo aceita **e-mail ou telefone**; o serviço detecta o formato.

| Cenário | Protege |
|---|---|
| identificador com `@` é tratado como e-mail (com `trim`) | quem digita com espaço não fica de fora |
| identificador sem `@` vira telefone **normalizado para dígitos** | `(83) 99999-0000` e `83999990000` são a mesma pessoa |
| sem correspondência ⇒ **mensagem genérica** (`"Credenciais inválidas"`) | **não revela** se o problema foi o identificador ou a senha — impede enumerar quem tem conta |
| identificador **nulo** não quebra | robustez do caminho de erro |

### `UsuarioAutenticado` — o principal único

Um único tipo de principal serve ao `formLogin` **e** ao login Google/OIDC (SPEC-08) — decisão que
evita dois caminhos de autorização divergentes.

| Cenário | Protege |
|---|---|
| papéis viram authorities `ROLE_*` | é disso que o `hasRole(...)` do `SecurityConfig` depende |
| **só conta `ATIVO` está habilitada** (`SUSPENSO` e `PENDENTE` não) | conta suspensa não loga, mesmo com a senha certa |
| `username` é o telefone; sem telefone (login social) recai no e-mail e, por fim, no id | conta do Google nasce sem telefone — não pode ficar sem identificador |
| expõe os dados de exibição e **`getName()` não vaza o hash** | o `getName()` vai para logs e auditoria |

### `AuditoriaSecurityListener` — a trilha de acesso (#19)

| Cenário | Protege |
|---|---|
| login bem-sucedido registra `LOGIN_SUCESSO` com id e nome | |
| falha registra `LOGIN_FALHA` **sem id**, guardando o identificador tentado | base para detectar força bruta (e para a futura **DT-12**, o lockout do login por senha) |
| **falha ao auditar NÃO impede a autenticação** | auditoria com problema não pode trancar todo mundo fora; o erro é só logado |

### Segurança coberta em outros arquivos

| Tema | Onde |
|---|---|
| RBAC de cada rota, CSRF, 403/redirect ao login | [02 — Web/API](02-web-api.md) (`ControleAcessoTest` e o RBAC de cada controller) |
| **Anti-enumeração** no "esqueci a senha" | [01 — Serviços](01-servicos.md) (`RecuperacaoSenhaServiceTest`) |
| **Lockout de OTP** e hash do código | [01 — Serviços](01-servicos.md) (`VerificacaoServiceTest`) |
| **E-mail não verificado não adota conta** (OIDC) | [01 — Serviços](01-servicos.md) (`IdentidadeOauthServiceTest`) |
| Token do webhook, idempotência, grupos/`fromMe` | [03 — WhatsApp](03-whatsapp-e-bot.md) |
| Manutenção não vaza tela protegida; flag não escala privilégio | [04 — Feature toggle](04-feature-toggle.md) |

**Lacuna conhecida:** `CaladriusOidcUserService` (17,6% de linhas) — cobri-lo exige montar um
`OidcUserRequest` com `ClientRegistration` completo. A **decisão de negócio** que ele delega (resolver
a identidade em 3 passos) já está coberta em `IdentidadeOauthServiceTest`; o que falta é a casca de
adaptação ao Spring Security.

---

## Observabilidade — SPEC-14 (7 cenários)

Dois níveis: a instrumentação em si e o seu efeito nos fluxos reais de negócio.

### `RastreamentoServiceTest` — 3 cenários (unitário)

| Cenário | Protege |
|---|---|
| caminho feliz: cria o span com nome e atributo, status de sucesso, e devolve o resultado | o span **não** pode alterar o retorno da operação |
| erro: **repropaga a exceção**, marca o span `ERROR` e grava o evento `exception` | telemetria não engole erro — e o erro aparece no Grafana |
| **sem agente** (OpenTelemetry no-op): executa a ação e não quebra | **RN-OBS-06** — em dev/testes o agente não está anexado; a app roda igual |

### `SolicitacaoViagemTelemetriaTest` — 4 cenários (cenário real)

Verificam que os **2 spans de negócio** saem dos fluxos de verdade (não de um teste sintético):

| Cenário | Protege |
|---|---|
| `solicitar-sob-demanda` emite span com destino e tipo | atributos de domínio no trace — é o que torna o trace útil |
| solicitação **duplicada** deixa o span em `ERROR` | a regra violada aparece na observabilidade |
| `aprovar-solicitacao` emite span com destino e sucesso | |
| aprovar uma solicitação **não-pendente** deixa o span em `ERROR` | |

> **Por que testar telemetria?** Porque ela só é consultada quando algo deu errado — e é justamente
> aí que ninguém quer descobrir que o span nunca foi emitido. O custo é baixo: o SDK de teste do
> OpenTelemetry coleta os spans em memória.
