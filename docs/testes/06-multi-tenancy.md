# 06 — Multi-tenancy: organizações, vínculos e contexto

Testes da **fase 1** da [SPEC-PLT-02](../sdd/specs/plataforma/SPEC-PLT-02-multi-ambiente-por-secretaria.md)
(ADR-21/ADR-22): o *plano de controle* do modelo multi-tenant — quem são as secretarias, quem
pertence a cada uma e por qual contexto a pessoa entra.

| Peça | Teste | Tipo |
|---|---|---|
| `OrganizacaoService` | `OrganizacaoServiceTest` | unitário |
| `VinculoService` | `VinculoServiceTest` | unitário |
| `ContextoTenant` | `ContextoTenantTest` | unitário |
| `ContextoTenantFilter` + `ContextoController` | `SelecaoContextoWebTest` | API (MockMvc + Testcontainers) |

> **O que atravessa todos:** esta fase é **aditiva**. Nenhuma tabela existente mudou e nenhuma conta
> tem vínculo hoje — logo, o comportamento de produção precisa ficar **idêntico**. Vários cenários
> abaixo existem só para provar isso.

---

## `OrganizacaoServiceTest` — 5 cenários

O cuidado é com o **slug**: ele identifica o tenant, vira subdomínio e, na fase 2, nome de schema.

| Cenário | Protege |
|---|---|
| slug normalizado (acento, maiúscula e espaço viram kebab-case) | "Campina Grande" e "campina grande" não podem virar dois tenants |
| slug derivado do nome quando não informado | o cadastro não obriga o gestor a inventar um identificador |
| nasce em `RASCUNHO` e **sem** schema de dados | RN-PAG-01: criar organização não concede acesso; o schema só vem no provisionamento |
| **slug duplicado é recusado** | colisão de slug = colisão de tenant |
| nome em branco é recusado | evita organização com identificador vazio |

## `VinculoServiceTest` — 7 cenários

| Cenário | Protege |
|---|---|
| **o vínculo nasce `PENDENTE`** | **RN-MT-08** — escolher uma secretaria na tela não é entrar nela; quem ativa é o gestor |
| solicitar duas vezes **não duplica** | dois cliques não viram duas solicitações na fila do gestor |
| aprovar ativa, grava quem/quando e **audita** | rastro na central de logs (`/logs`) |
| revogar mantém o registro | **RN-MT-16** — histórico não se apaga |
| aprovar vínculo inexistente ⇒ 404 | |
| `ativosDe` traz só os `ATIVO` da própria pessoa | é o que alimenta a tela de escolha |
| **isolamento por dono**: vínculo de outra pessoa ⇒ 404 | mesmo padrão da SPEC-VIA-03; **404 e não 403**, para não confirmar que existe |

## `ContextoTenantTest` — 4 cenários

Classe pequena, dois invariantes de segurança:

| Cenário | Protege |
|---|---|
| sem nada definido, roda no **tenant legado** | é o estado de 100% das requisições hoje |
| `definir`/`limpar` | ciclo de vida por requisição |
| `definir(null)` equivale a limpar | nunca deixa contexto meio definido |
| **o contexto não atravessa threads** | com requisições concorrentes sobre o pool, vazar aqui é vazar dado de outra secretaria |

## `SelecaoContextoWebTest` — 8 cenários (API)

| Cenário | Protege |
|---|---|
| **sem vínculo, entra direto** | a **regressão** que mais importa: é a situação de todas as contas em produção |
| um único vínculo é assumido em silêncio | perguntar sem haver escolha é fricção |
| dois vínculos ⇒ qualquer tela leva a `/entrar/onde` | o contexto precisa existir antes de operar |
| a tela lista **só** os vínculos de quem está logado | **FR-MT-09** — o sistema nunca enumera a carteira de secretarias |
| escolher libera o sistema e **vale para a sessão** | não pergunta de novo a cada clique |
| **vínculo de outra pessoa ⇒ 404** | **CA-MT-05**, anti-enumeração |
| vínculo inexistente ⇒ 404 | mesma resposta do caso acima — sem oráculo |
| anônimo é mandado ao login | a tela é autenticada |

---

## Ainda **não** coberto (fase 2)

O teste que falta é o mais importante da spec: **isolamento com dois schemas** (CA-MT-01) e o
**reset do `search_path`** ao devolver a conexão ao pool (CA-MT-03). Eles só fazem sentido quando a
multi-tenancy do Hibernate entrar — e são **critério de saída** da fase 2, conforme a
[SPEC-PLT-02 §11](../sdd/specs/plataforma/SPEC-PLT-02-multi-ambiente-por-secretaria.md).
