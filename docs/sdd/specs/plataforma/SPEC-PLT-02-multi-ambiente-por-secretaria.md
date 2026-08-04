# SPEC-PLT-02 — Multi-tenancy por secretaria (schema por tenant + identidade global)

| | |
|---|---|
| **Área** | `MT` (multi-tenant / multi-ambiente) |
| **Papéis** | SYSADMIN da **plataforma** (provisiona e opera os tenants; vê organizações, **não** vê dados de viagem). **Dentro** de cada secretaria os papéis seguem idênticos (GERENTE/MOTORISTA/PASSAGEIRO) e isolados. Nenhum papel novo de usuário final. |
| **Status geral** | 🟡 **Fase 1 implementada (2026-08-04); fases 2 e 3 em aberto.** Substitui a decisão anterior (Opção A / silo, ADR-19) pela **Opção B2**: schema por tenant + plano de controle no `public`. Decisão em §3, comparação em §2, fluxos de cadastro em §5, faseamento e **estado de cada fase** em §11. **ADR-21** (isolamento) e **ADR-22** (identidade global) registradas. |
| **Constituição** | Art. II (camadas), **Art. IV (migrations forward-only — agora em duas trilhas, §7.3)**, **Art. V (o schema é o contrato; o Hibernate só valida — ver a armadilha do `validate` em §7.4)**, Art. VI (UUID/enums VARCHAR), Art. VII (RBAC), Art. IX (português), **Art. XI (segurança — o isolamento fica ABAIXO do ORM, não na lógica)**, Art. XII (o gate do CI é a compilação), Art. XIII (`/ping` intacto), **Art. XIV (ambiente compartilhado — ver §9, o que fazer sem privilégio de `CREATE`)** |
| **Relacionada** | [SPEC-ACE-01](../acesso/SPEC-ACE-01-autenticacao.md) (login e auto-cadastro), [SPEC-ACE-02](../acesso/SPEC-ACE-02-login-social-google.md) (OIDC), [SPEC-ACE-03](../acesso/SPEC-ACE-03-verificacao-de-contato-e-recuperacao-de-senha.md) (OTP — reusado no *step-up*), [SPEC-PLT-01](SPEC-PLT-01-feature-toggle.md) (configuração: o que é da plataforma × da secretaria), **[SPEC-PLT-03](SPEC-PLT-03-organizacao-planos-e-pagamento.md) (venda self-service — é ela que dispara o provisionamento)**, [SPEC-WPP-01](../whatsapp/SPEC-WPP-01-integracao-whatsapp.md) (roteamento do webhook por instância), [SPEC-CAD-04](../cadastros/SPEC-CAD-04-endereco-do-passageiro.md) (município → secretaria) · **ADR-19** (silo, agora *tier* dedicado), **ADR-21/ADR-22** ([plano técnico §9](../../02-plano-tecnico.md)) |
| **Código/Infra** | A criar: `TenantContext`, `ResolvedorTenant`, `ProvedorConexaoMultiTenant`, `ProvisionamentoTenantService`, trilha Flyway `db/migration/tenant/`. Já existente (tier dedicado): `docker/docker-compose.tenant.yml`, `scripts/novo-ambiente.sh`, [`docs/multi-ambiente.md`](../../../multi-ambiente.md). |

---

## 1. A lacuna que esta spec cobre

Várias secretarias de saúde assinam o serviço — Campina Grande (PB), Caruaru (PE) — e **uma não pode
ver nem interferir nos dados da outra**. Hoje o CALADRIUS é **single-tenant**: não existe fronteira
organizacional nenhuma.

O que **de fato** isola dados hoje (e por que não basta):

| Mecanismo | Isola o quê | Serve de fronteira entre secretarias? |
|---|---|---|
| **RBAC** (Art. VII) | o que cada **papel** pode fazer | ❌ é ortogonal à organização |
| **Por dono** (SPEC-VIA-03) | um PASSAGEIRO só vê as **próprias** solicitações | ❌ não escala para "todos os dados de uma secretaria" |
| **GERENTE** | — | ❌ vê **tudo, globalmente** (`ViagemService.listar` não tem escopo) |

A entidade `Usuario` não tem vínculo organizacional; `Cidade`/`Municipio` são **dados de referência
compartilhados**, não donos. Se Campina e Caruaru rodassem no mesmo deploy, o gerente de uma veria a
outra.

**O que mudou desde a versão anterior desta spec:** a [SPEC-PLT-03](SPEC-PLT-03-organizacao-planos-e-pagamento.md)
introduziu **venda self-service** — quem paga espera acessar em segundos, às duas da manhã, sem
operador humano no meio. Isso invalida a Opção A como padrão (§2) e **exige provisionamento
automático**.

---

## 2. Opções reavaliadas

| Critério | **A — instância por cliente (silo)** | **B — schema por tenant** | **C — linha a linha (coluna `organizacao`)** |
|---|---|---|---|
| Isolamento | 🟢 físico (bancos separados) | 🟢 **por conexão** (`search_path`) | 🔴 lógico (mesma tabela) |
| Onde mora a garantia | infra | **conexão — abaixo do ORM** | **na lógica da consulta** |
| SQL nativo é isolado? | 🟢 sim | 🟢 **sim** | 🔴 **não** — `@TenantId` não filtra query nativa |
| Provisionamento automático | 🔴 exige *control plane* com acesso ao Docker | 🟢 `CREATE SCHEMA` + Flyway, segundos | 🟢 `INSERT` |
| Exportar/apagar um cliente | 🟢 dump do banco | 🟢 `pg_dump -n` / `DROP SCHEMA` | 🔴 `DELETE` em ~8 tabelas sem esquecer nenhuma |
| Custo de recursos | 🔴 N apps + N bancos | 🟡 1 servidor, N schemas | 🟢 1 de tudo |
| Migrations | 🟢 por banco | 🟡 **N schemas por deploy** | 🟢 uma vez |
| Relatório agregado entre clientes | 🔴 difícil | 🟡 possível (`UNION`/laço) | 🟢 natural |
| Faixa de conforto | poucas dezenas | **dezenas a poucas centenas** | milhares |

**Por que B e não C** (revendo a ADR-20, que propunha C): as duas isolam, mas em C a garantia mora na
**lógica** — e a própria SPEC-PLT-03 §D6 admite com um ⚠️ que `@TenantId` **não filtra SQL nativo**,
transferindo o risco para revisão de código. Em B, o vizinho não está no `search_path`: nem o ORM, nem
SQL nativo, nem um `EXPLAIN` mal colado o alcançam. Somado a isso, "me devolva / apague meus dados" —
que é contrato, não capricho — é um comando em B e um projeto em C (Art. XI).

**Por que B e não A:** A não provisiona sozinha. Automatizá-la exigiria um *control plane* com acesso
ao Docker da VPS — mais superfície de ataque e mais coisa para quebrar do que um `CREATE SCHEMA`. E na
infra da disciplina (Art. XIV: Postgres compartilhado, **pool limitado a 5 conexões**) a Opção A é
indemonstrável, enquanto B roda com 2–3 schemas no mesmo banco `eq14`.

---

## 3. Decisão — **Opção B2 (schema por tenant + plano de controle no `public`)**

> **Os dados operacionais de cada secretaria vivem em um schema próprio (`tenant_<slug>`); o que é da
> plataforma — identidades, vínculos, organizações, assinaturas, pagamentos e dados de referência —
> vive no `public`. O tenant da requisição é resolvido a partir do vínculo escolhido no login e
> aplicado no `search_path` da conexão.** Registrada como **ADR-21**.

O "2" distingue esta variante da **B1 pura** (tudo, inclusive usuários, dentro do schema do tenant),
que foi **descartada**: ela obrigaria o login a saber o tenant **antes** de autenticar — ou seja, um
seletor de secretaria na tela de login, que **enumera a carteira de clientes** para qualquer visitante
e ainda impede a mesma pessoa de existir em duas secretarias (§4).

```
public/  (plano de controle)           tenant_campina/            tenant_caruaru/
  identidades     credencial+contato     membros  (perfil+papel)    membros
  vinculos        identidade×org×papel   viagens                    viagens
  organizacoes                           veiculos                   veiculos
  assinaturas / pagamentos               linhas_programadas         ...
  municipios / cidades  (referência)     solicitacoes_viagem
  configuracoes_plataforma               enderecos
  log_plataforma                         notificacoes
                                         conversas_bot / mensagens_whatsapp
                                         log_auditoria
                                         configuracoes_secretaria
```

### 3.1 Requisitos funcionais

| ID | Requisito | Estado |
|---|---|---|
| **FR-MT-01** | Cada secretaria = **stack isolado** (app + banco + subdomínio), sem recurso compartilhado. | ✅ protótipo — **agora restrito ao tier dedicado** (§10) |
| **FR-MT-02** | Provisionar um novo ambiente **sem editar código**. | ✅ protótipo (`scripts/novo-ambiente.sh`) |
| **FR-MT-03** | Dados de uma secretaria **nunca** visíveis a outra. | ⬜ passa a valer por **schema** (§7) |
| **FR-MT-04** | Cada ambiente tem seu **admin-bootstrap**. | 🟡 vira "o gestor que assinou é o 1º membro" (SPEC-PLT-03) |
| **FR-MT-05** | Segredos e integrações **por ambiente**. | 🟡 no tier dedicado, `.env`; em B2, `configuracoes_secretaria` + instância WhatsApp própria |
| **FR-MT-06** | Atualizar a versão é **propagável a todos**. | ✅ imagem única |
| **FR-MT-07** | **Provisionar um tenant é automático**: criar schema, migrar e liberar acesso **sem intervenção humana**, disparado pela confirmação de pagamento (RN-PAG-01). | ⬜ novo |
| **FR-MT-08** | Uma pessoa pode ter **vínculos em mais de uma secretaria** e/ou **mais de um papel**, com **uma** credencial. | ⬜ novo |
| **FR-MT-09** | A **tela de login não expõe** a lista de secretarias; a descoberta acontece por convite, subdomínio ou município (§6). | ⬜ novo |
| **FR-MT-10** | A estratégia de isolamento é **substituível por configuração** (`schema`, `dedicado`, `legado`) sem alterar a lógica de negócio. | ⬜ novo — é o que sustenta o §9 |
| **FR-MT-11** | Exportar (`pg_dump -n`) e **encerrar** (`DROP SCHEMA` após arquivar) um cliente são operações de **um comando**, sem tocar nos demais. | ⬜ novo |

### 3.2 Regras de negócio / invariantes

Os invariantes **RN-MT-01..07** da versão anterior continuam válidos **no tier dedicado** (§10):
nomeação do Compose, porta só no loopback, senha sorteada por ambiente, backup independente, `/ping`
por ambiente, ausência de tenant no código e rescisão isolada. Os novos, do modelo B2:

- **RN-MT-08 — O tenant vem do vínculo autenticado, nunca do cliente.** Nem cabeçalho, nem parâmetro,
  nem subdomínio sozinho definem o schema: ele vem do **vínculo escolhido**, que pertence à identidade
  autenticada. Subdomínio é *dica de UX*, sujeita a conferência.
- **RN-MT-09 — Toda conexão devolvida ao pool volta ao `search_path` neutro.** Com pool de 5 conexões
  (Art. XIV), uma conexão devolvida ainda apontando para `tenant_a` e reaproveitada por `tenant_b`
  **é o vazamento**. O reset é obrigatório e testado.
- **RN-MT-10 — Requisição sem tenant resolvido não toca dado operacional.** Falha fechada (erro),
  nunca "cai no `public`" — seria justamente esse o padrão inseguro.
- **RN-MT-11 — O `public` guarda o mínimo de dado pessoal**: credencial e contato (e-mail/telefone).
  Nome, CPF, endereço, condições de saúde e histórico ficam **dentro** do schema da secretaria.
- **RN-MT-12 — Dados de referência não são multi-tenant**: `municipios` e `cidades` seguem
  compartilhados (IBGE/domínio), somente leitura para o tenant.
- **RN-MT-13 — O SYSADMIN da plataforma não entra em schema de tenant** para ver dado operacional;
  enxerga apenas o plano de controle (organizações, assinaturas, pagamentos, saúde do provisionamento).
- **RN-MT-14 — Toda migration vale para todos os tenants.** Não existe schema com forma própria; um
  tenant fora da versão corrente é **incidente**, não configuração.
- **RN-MT-15 — Mensagem recebida pelo WhatsApp é roteada pela instância** que a recebeu → organização.
  Sem instância mapeada, a mensagem é registrada e **não** processada pelo bot (RN-WPP-02 segue
  valendo: nada quebra).
- **RN-MT-16 — Encerrar contrato não apaga dado sem dump arquivado** (espelha a RN-PAG-04:
  inadimplência nunca apaga).

---

## 4. Identidade global e vínculos (ADR-22)

O modelo que faz os três fluxos de cadastro funcionarem — e o que evita o seletor de secretaria na
tela de login.

```
public.identidades       quem é a pessoa: e-mail, telefone, senha (BCrypt), verificações
   1 ──── N
public.vinculos          identidade × organizacao × papel × status (PENDENTE/ATIVO/REVOGADO)
   │
   └────  tenant_<slug>.membros    o perfil DENTRO da secretaria: nome, CPF, endereço, preferências
```

**Por que separar identidade de membro:** se `usuarios` continuasse global e inteiro, o isolamento do
dado pessoal voltaria a depender de `WHERE` — exatamente o que a Opção B evita. Dividindo, o `public`
fica com o mínimo para **autenticar** e para responder "onde você pode entrar", enquanto as FKs
operacionais (`viagens.motorista`, `solicitacoes.passageiro`) apontam para `membros` **dentro do
mesmo schema**.

| Peça de hoje | Muda? |
|---|---|
| `CaladriusUserDetailsService` (e-mail ou telefone + BCrypt) | 🟡 passa a consultar `public.identidades`, **fora** de qualquer tenant |
| Tela de login | ❌ **não muda** |
| `UsuarioAutenticado` | ✅ carrega `identidadeId`, `organizacaoId`, `membroId` e as *authorities* **do vínculo escolhido** |
| Papéis (`Usuario.papeis`, `@ElementCollection` → `papeis_usuario`) | ✅ a tabela **já é N papéis por pessoa**: ganha a coluna `organizacao` (e `status`) e vira `vinculos`. O refactor não é criar multi-papel — é fazer o papel valer **por secretaria** e a sessão carregar só o do vínculo escolhido |
| Login Google/OIDC (SPEC-ACE-02) | 🟡 resolve/cria a **identidade**; sem vínculo, cai em `/conta/secretaria` |
| `PerfilIncompletoFilter` | ➕ ganha um irmão, `VinculoPendenteFilter` — **mesmo padrão já em produção** |
| Unicidade de telefone/e-mail | 🟡 **global, na identidade** — resolve a limitação aceita no D7 da SPEC-PLT-03 |

---

## 5. Fluxos de cadastro

### 5.1 Passageiro (usuário comum)

Três portas de entrada, todas terminando em **vínculo**:

| Porta | Como a secretaria é resolvida | Exposição |
|---|---|---|
| **Convite** do gestor/admin | o token carrega a organização | zero |
| **WhatsApp** (SPEC-WPP-02) | a **instância** que recebeu a mensagem (RN-MT-15) | zero |
| **Sistema** (senha ou Google) | §6 — subdomínio, município ou busca | mínima |

No caminho pelo sistema a conta **nasce sem vínculo**, e um filtro a prende em `/conta/secretaria` até
resolver; depois o vínculo fica `PENDENTE` até o gestor aprovar (a tela `/gestao/solicitacoes` já
existe e serve de modelo).

> ⚠️ **Correção ao desenho original.** "Travar a autenticação até escolher a secretaria" é
> **impossível no caminho do Google**: quem autentica é o Google, e só depois o controle volta para
> nós. O que dá para fazer — e é equivalente em efeito — é **autenticar e não liberar nada** enquanto
> não houver vínculo. Quem escolhe a secretaria *antes* de clicar pode ter a escolha carregada no
> parâmetro `state` do OAuth, pulando a etapa.

### 5.2 Gestor

```
1. identidade   Google ou e-mail+senha  →  public.identidades, verificada por OTP (SPEC-ACE-03)
                                           ← "autenticado", ainda SEM papel
2. secretaria   escolher uma existente (→ vínculo PENDENTE, gestor atual aprova)
                ou CRIAR: nome, CNPJ, municípios atendidos, slug
                                        →  organizacoes(status = RASCUNHO)
3. plano        checkout (SPEC-PLT-03)  →  AGUARDANDO_PAGAMENTO
4. webhook      confirmação servidor-a-servidor (RN-PAG-01)
                → CREATE SCHEMA tenant_<slug> + Flyway            (FR-MT-07)
                → vinculo(identidade, org, GERENTE, ATIVO) + membro no schema
                → organizacao ATIVA + notificação
```

É isto que responde ao "liberar o botão de autenticação antes de escolher o plano": a **identidade**
existe e está verificada antes do pagamento; o **papel** só nasce no passo 4. Em nenhum momento um
formulário concede `GERENTE`.

### 5.3 Motorista

- **Só por convite** do gestor da secretaria — não há auto-cadastro de motorista (RN-MT-08: o vínculo
  vem de quem já está dentro).
- Quem é motorista **e** passageiro tem **dois vínculos** e uma credencial. Após o login,
  `/entrar/onde` lista **apenas os vínculos daquela identidade** — nenhuma outra secretaria aparece.
- **Step-up ao entrar no contexto MOTORISTA**: OTP por e-mail ou telefone reusando
  `VerificacaoService`/`codigos_verificacao` (SPEC-ACE-03), com validade configurável por
  `param.step_up.*` (SPEC-PLT-01).

> **Recomendação de produto:** não peça OTP a cada login — motorista entra várias vezes por dia. Peça
> na **troca para o contexto de motorista** e guarde a validade por N minutos. Lembre que o canal
> WhatsApp ainda opera como *stub* em produção: hoje o OTP sairia só por e-mail.

---

## 6. Descoberta da secretaria sem enumeração

Secretaria municipal é entidade pública — a existência dela não é segredo. O que precisa de proteção é
**enumerar a carteira de clientes** e **confirmar que fulano pertence a X**. Por isso não existe, em
lugar nenhum, uma lista completa:

| Mecanismo | Quando | Exposição |
|---|---|---|
| Convite (o token carrega a organização) | gestor/admin convidando | zero |
| Subdomínio (`campina.caladrius.app`) | canal oficial, QR no posto | zero |
| **Município do endereço** (SPEC-CAD-04) | cadastro público espontâneo | mínima: "sua cidade é atendida por X" |
| Busca com **≥ 3 caracteres**, sem listagem, com *rate limit* | fallback | baixa |

Reforços: o vínculo nasce `PENDENTE` e o gestor aprova; e as respostas de erro seguem o padrão
**anti-enumeração** já adotado na recuperação de senha (SPEC-ACE-03) — nunca revelar se uma conta ou
um vínculo existe.

---

## 7. Mecânica técnica

### 7.1 Resolução do tenant
`TenantContext` (ThreadLocal) preenchido por filtro a partir do principal; `ResolvedorTenant`
(`CurrentTenantIdentifierResolver`) o entrega ao Hibernate. Sem tenant ⇒ erro (RN-MT-10).

### 7.2 Conexão
`ProvedorConexaoMultiTenant` (`MultiTenantConnectionProvider`) faz `SET search_path TO tenant_x, public`
ao obter a conexão e **`RESET search_path` ao devolvê-la** (RN-MT-09). Um único pool serve todos os
tenants — o que mantém o limite de 5 conexões do Art. XIV.

### 7.3 Flyway em duas trilhas (Art. IV)
`db/migration/plataforma/` roda no `public`; `db/migration/tenant/` roda em **cada** schema, no boot e
na criação do tenant. As migrations existentes **V1..V15 não são tocadas** (já aplicadas no banco
compartilhado; o Flyway compara checksum).

> **Decisão pendente (D-MT-A):** como nasce a trilha `tenant/`. **(a) Baseline novo** —
> `V1__baseline_tenant.sql` gerado por `pg_dump -s` do estado atual, com o deploy de hoje virando
> "tenant legado" servido pelo `public`; ou **(b) migrar o legado**, movendo as tabelas operacionais
> para `tenant_legado`. **Recomendação: (a)**, por não mexer no banco compartilhado — acompanhada de
> um teste que sobe os dois e compara os schemas, senão viram duas verdades.

### 7.4 A armadilha do `ddl-auto: validate` (Art. V)
Produção sobe com `validate`: se a entidade não bater com o schema, **a app não sobe**. Com
multi-tenancy por schema, o Hibernate valida contra o schema da conexão padrão — é preciso definir
contra **qual** schema validar (um *template* na versão corrente). Item de saída da fase 2: erra-se
aqui e o deploy inteiro cai.

### 7.5 O resto do sistema
| Peça | O que muda |
|---|---|
| **Webhook WhatsApp** (SPEC-WPP-01) | instância Evolution **por organização**; `instância → org` define o tenant (RN-MT-15). Hoje a instância é única — precisa entrar na SPEC-WPP-01 |
| **Central de logs `/logs`** | `log_auditoria` fica no tenant; a plataforma ganha `log_plataforma` (fecha a **DT-21**) |
| **Feature toggle** (SPEC-PLT-01) | `configuracoes_sistema` racha em `configuracoes_plataforma` (modo de manutenção, kill switches) × `configuracoes_secretaria` (nome de exibição do WhatsApp, parâmetros de OTP). Responde o ❓ da SPEC-PLT-03 §5.2 |
| **Observabilidade** (SPEC-OPE-01) | `tenant` como atributo de span/log — **sem PII**, atento à cardinalidade |
| **`/ping`** | continua público e **sem tenant** (Art. XIII intacto) |

---

## 8. Migrations propostas

| Trilha | Migration | Conteúdo |
|---|---|---|
| `plataforma/` | **V16** | `identidades`, `vinculos`, `organizacoes`, `assinaturas`, `pagamentos` (as três últimas vêm da SPEC-PLT-03 §5.1) |
| `tenant/` | **V1 (baseline)** | schema operacional completo: `membros`, `viagens`, `veiculos`, `linhas_programadas`, `solicitacoes_viagem`, `enderecos`, `notificacoes`, `conversas_bot`, `mensagens_whatsapp`, `log_auditoria`, `configuracoes_secretaria` |

**A V17 da SPEC-PLT-03 (coluna `organizacao` em ~8 tabelas + backfill) deixa de existir** — era o que a
Opção C exigia. É a maior economia desta decisão.

### 8.1 O que acontece com cada tabela existente

Levantamento contra o schema atual (V1→V15). Só **uma** tabela muda de forma; o resto muda de
**lugar** ou fica onde está.

| Tabela (entidade) | Destino | O que muda |
|---|---|---|
| **`usuarios`** (`Usuario`) | **racha em duas** | `public.identidades` fica com credencial e contato (`email`, `telefone`, `hash_senha`, `telefone_verificado_em`, `email_verificado_em`, `perfil_incompleto`); `tenant_x.membros` fica com o perfil na secretaria (`nome_completo`, `cpf`, `status`, `removido_em`). **Unicidade**: telefone/e-mail passam a ser globais (na identidade); **CPF passa a ser único por secretaria** — a mesma pessoa pode ser membro de duas |
| **`papeis_usuario`** (`Usuario.papeis`) | `public.vinculos` | já é N papéis por pessoa; ganha `organizacao` e `status` (PENDENTE/ATIVO/REVOGADO). `concedido_por` passa a referenciar a identidade |
| `viagens` (`Viagem`) | `tenant_x` | forma **idêntica**; `motorista` e `criado_por` passam a referenciar `membros` (mesmo schema) |
| `solicitacoes_viagem` (`SolicitacaoViagem`) | `tenant_x` | idem; `passageiro` → `membros` |
| `veiculos` (`Veiculo`) | `tenant_x` | sem alteração |
| `linhas_programadas` + `linha_dias` (`LinhaProgramada`) | `tenant_x` | sem alteração |
| `enderecos` (`Endereco`) | `tenant_x` | `usuario` (UNIQUE) → `membro`. O `municipio` continua apontando para o `public` (referência) |
| `notificacoes` (`Notificacao`) | `tenant_x` | `usuario` → `membros` |
| `conversas_bot`, `mensagens_whatsapp` (`ConversaBot`, `MensagemWhatsapp`) | `tenant_x` | `usuario` → `membros`; o roteamento passa a ser pela instância (RN-MT-15) |
| `log_auditoria` (`LogAuditoria`) | `tenant_x` | sem alteração (já guarda `usuario_id`/`usuario_nome` **sem FK**); a plataforma ganha `log_plataforma` — fecha a **DT-21** |
| `tokens_ativacao` (`TokenAtivacao`) | `public` | segue a identidade; o **convite** ganha `organizacao` (é para entrar numa secretaria específica) |
| `codigos_verificacao` (`CodigoVerificacao`) | `public` | OTP é da identidade — e é o mesmo mecanismo do *step-up* (§5.3) |
| `identidades_oauth` (`IdentidadeOauth`) | `public` | `usuario` → `identidades` |
| `municipios`, `cidades` (`Municipio`, `Cidade`) | `public` | **sem alteração** — referência compartilhada (RN-MT-12) |
| `configuracoes_sistema` (`ConfiguracaoSistema`) | **racha em duas** | `configuracoes_plataforma` (`public`: modo de manutenção, kill switches) × `configuracoes_secretaria` (`tenant_x`: nome de exibição do WhatsApp, parâmetros de OTP) — responde o ❓ da SPEC-PLT-03 §5.2 e afeta o `FeatureFlagService` |
| `assentos_viagem`, `escalas_motorista`, `perfis_gerente/motorista/passageiro`, `solicitacoes_transporte` | **não migram** | tabelas **dormentes** (sem entidade nem uso — **DT-14**). O baseline do tenant é a chance de não carregá-las adiante |

> **Leitura de risco:** as duas linhas em negrito são o trabalho de verdade. `usuarios` é o coração do
> login e das FKs; `configuracoes_sistema` é o coração da SPEC-PLT-01. Todo o resto é recriar a mesma
> tabela em outro schema — mecânico, e coberto pelo teste de isolamento (CA-MT-01).

---

## 9. Sem privilégio de `CREATE` no banco (Art. XIV)

Criar tabela exige `CREATE` no **schema**; criar schema exige `CREATE` no **banco** — ter um não
implica ter o outro. Verificação:

```sql
SELECT has_database_privilege(current_user, current_database(), 'CREATE');
```

O privilégio só é necessário para **provisionar**: operar exige apenas `USAGE` no schema e
`SET search_path`, que não exige privilégio nenhum. Se a resposta for `false`, a decisão **não cai** —
degrada, nesta ordem:

1. **Schemas pré-criados pelo DBA** (ou `GRANT CREATE`): a aplicação nunca emite DDL. Perde-se o
   "pagou, entrou em segundos"; vira ordem de provisionamento — o mesmo modelo do tier dedicado.
2. **Produto real na VPS da equipe**, onde a equipe é dona do banco; o deploy da disciplina segue como
   **tenant legado** único.
3. **Opção C como plano B**, que não exige privilégio novo.

É por isso que a **FR-MT-10** existe: com a estratégia atrás de uma abstração (`schema` / `dedicado` /
`legado`), trocar de modo é configuração — mesmo padrão dos beans condicionais do WhatsApp, do Google
e do OTel.

---

## 10. O *tier* dedicado (ex-Opção A) continua vivo

A ADR-19 **não é descartada**: vira um **plano comercial**, para o cliente que exija isolamento físico
por contrato.

| | Padrão (self-service) | Dedicado (enterprise) |
|---|---|---|
| Isolamento | schema `tenant_<slug>` | banco + stack próprios |
| Provisionamento | automático, no webhook | assistido, com SLA |
| Contratação | checkout na plataforma central | **o mesmo checkout**, que gera ordem de provisionamento |
| Artefatos | esta spec | `docker-compose.tenant.yml`, `scripts/novo-ambiente.sh`, [runbook](../../../multi-ambiente.md) |

O plano de controle é **sempre central**: onde os dados moram é um atributo da organização, não um
produto diferente.

---

## 11. Faseamento

| Fase | Entrega | Critério de saída | Estado |
|---|---|---|---|
| **1** | Plano de controle: `organizacoes` + `vinculos` (**V16**), `ContextoTenant`, escolha de contexto em `/entrar/onde`. **Um** tenant (legado) | login e RBAC intactos; nada muda para quem não tem vínculo | ✅ **2026-08-04** |
| **2** | `usuarios` → `identidades` + `membros`; `MultiTenantConnectionProvider` (`search_path`); trilha Flyway `tenant/`; provisionamento automático | **teste de isolamento com dois schemas** (CA-MT-01/02) e teste de reset do pool (CA-MT-03) | ⬜ |
| **3** | Descoberta da secretaria (§6), *step-up* do motorista, instância WhatsApp por organização | CA-MT-04..06 | ⬜ |

⚠️ **A fase 1 sozinha mantém o estado de hoje** — todos enxergam todos, porque nenhuma tabela
operacional foi para um schema de tenant ainda. Aceitável para demonstração acadêmica **se
declarado**; antes de qualquer cliente real, a fase 2 é obrigatória.

**O que a fase 1 entregou, concretamente:** migration **V16** (`organizacoes`, `vinculos`), entidades
`Organizacao`/`Vinculo` + enums de status, `OrganizacaoService` (slug normalizado e único),
`VinculoService` (solicitar idempotente, aprovar, revogar, isolamento por dono),
`multitenancia.ContextoTenant` + `ContextoTenantFilter` (contexto por requisição, limpo em
`finally`) e a tela `/entrar/onde`. **24 testes** cobrindo, entre outros, CA-MT-05 e a regressão
"quem não tem vínculo entra como sempre".

**O que a fase 1 deliberadamente NÃO fez:** não dividiu `usuarios`, não criou schema de tenant, não
mexeu em `papeis_usuario` (que segue sendo a fonte dos papéis) e **não** ligou a multi-tenancy do
Hibernate. Enquanto o `search_path` não muda, o `ContextoTenant` é infraestrutura pronta e inerte —
por isso a fase 1 é aditiva e não tem como quebrar produção.

---

## 12. Critérios de aceite (Dado / Quando / Então)

- **CA-MT-01 — Isolamento entre secretarias**
  *Dado* viagens, veículos e solicitações nos tenants A e B, *Quando* o gestor de A lista qualquer uma
  delas, *Então* vê **somente** as de A — inclusive por consulta SQL nativa.
- **CA-MT-02 — Sem tenant, sem dado**
  *Dado* uma requisição autenticada sem vínculo escolhido, *Quando* ela toca dado operacional, *Então*
  falha explicitamente (RN-MT-10) — nunca lê o `public`.
- **CA-MT-03 — Conexão devolvida ao pool não vaza**
  *Dado* uma requisição do tenant A seguida de uma do tenant B **na mesma conexão** do pool, *Então* a
  segunda enxerga apenas B (RN-MT-09).
- **CA-MT-04 — A tela de login não enumera**
  *Dado* um visitante anônimo, *Quando* percorre login, cadastro e recuperação de senha, *Então* em
  nenhum ponto obtém a lista de secretarias (FR-MT-09).
- **CA-MT-05 — Múltiplos vínculos**
  *Dado* uma pessoa com vínculo de passageiro em A e de motorista em B, *Quando* faz login, *Então*
  escolhe entre os dois e a sessão carrega **apenas** as *authorities* do escolhido.
- **CA-MT-06 — Step-up do motorista**
  *Dado* o vínculo de motorista, *Quando* a pessoa entra nesse contexto sem verificação válida,
  *Então* é exigido OTP antes de qualquer tela de motorista.
- **CA-MT-07 — Provisionamento pelo pagamento**
  *Dado* o webhook de pagamento aprovado (RN-PAG-01), *Então* o schema é criado, migrado na versão
  corrente e o gestor recebe acesso — sem intervenção humana.
- **CA-MT-08 — Encerramento isolado**
  *Dado* o encerramento de um cliente, *Quando* o schema é arquivado e removido, *Então* os demais
  tenants seguem operando (RN-MT-16).

---

## 13. Consequências / trade-offs

- **Prós**: isolamento abaixo do ORM (SQL nativo incluído); export/delete por cliente em um comando;
  provisionamento automático; uma imagem e um pool para todos; caminho de saída para o tier dedicado
  sem mudar o modelo de dados.
- **Contras**: migrations **× N schemas** (boot mais lento; uma migration ruim atinge todos — mitigar
  com canário por tenant); vizinho barulhento (CPU e conexões compartilhadas); relatório agregado
  exige laço ou `UNION`; o catálogo do Postgres incha na casa dos milhares de schemas.
- **Quando revisitar (gatilhos para C)**: relatório agregado entre secretarias como requisito de
  produto, ou **muitos milhares** de tenants pequenos.

---

## 14. Impacto em specs/documentos existentes

- **[SPEC-PLT-03](SPEC-PLT-03-organizacao-planos-e-pagamento.md)** — §3 e D6 mudam (isolamento por
  schema, não por `@TenantId`); **a V17 deixa de existir**; o D7 (unicidade global) é resolvido pelo
  modelo de identidade; o D8 (organização do passageiro) passa a apontar para o §6 desta spec.
- **[SPEC-WPP-01](../whatsapp/SPEC-WPP-01-integracao-whatsapp.md)** — instância Evolution **por
  organização** e roteamento do webhook (RN-MT-15).
- **[SPEC-PLT-01](SPEC-PLT-01-feature-toggle.md)** — separação plataforma × secretaria da configuração.
- **[Plano técnico §9](../../02-plano-tecnico.md)** — **ADR-21** (isolamento por schema) e **ADR-22**
  (identidade global + vínculos); a ADR-19 passa a valer para o tier dedicado; a parte de isolamento
  da ADR-20 é substituída.
- **[Roadmap](../../03-tarefas-e-roadmap.md)** — capacidade "Multi-tenancy" repactuada em três fases;
  a **DT-21** (logs por organização) passa a ser resolvida por esta spec.
- **[CLAUDE.md](../../../../CLAUDE.md)** — atualizar o "Estado atual" **quando a fase 1 for de fato
  implantada**; até lá, isto é proposta.
