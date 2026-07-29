# SPEC-16 — Organização, planos e pagamento (self-service do gestor)

| | |
|---|---|
| **Área** | `PAG` (pagamento) + `ORG` (organização) |
| **Papéis** | **Visitante** (assina um plano e vira GERENTE da sua secretaria); GERENTE (gere a assinatura da organização); SYSADMIN da plataforma (vê organizações, assinaturas e pagamentos). Nenhum papel novo. |
| **Status geral** | 🚧 **Proposta — a discutir/aprovar.** Decisões D1–D9 em §4; pontos em aberto marcados com ❓. **Duas migrations** propostas (V16 = organização/assinatura/pagamento; V17 = tenant nas tabelas operacionais). Ver o **faseamento** em §9 — as duas fases são specs de implementação distintas. |
| **Constituição** | Art. II (camadas), IV (migrations forward-only), VI (UUID/enums VARCHAR), VII (RBAC), IX (português), X (HTMX), **XI (segurança — o pagamento é o *gate* de autorização, nunca o clique do usuário)**, XII (o gate do CI é a compilação), XIV (banco compartilhado) |
| **Relacionada** | [SPEC-01](SPEC-01-autenticacao.md) (auth/cadastro/convite), [SPEC-08](SPEC-08-login-social-google.md) (OIDC), [SPEC-12](SPEC-12-verificacao-de-contato-e-recuperacao-de-senha.md) (token/OTP), [SPEC-13](SPEC-13-feature-toggle.md) (**entitlement `municipios.pagamento_habilitado`** — RN-FLG-05), **[SPEC-15](SPEC-15-multi-ambiente-por-secretaria.md) §7 (gatilhos da Opção C)** · **ADR-20** (proposta) |

---

## 1. A ideia do dono do produto (registro fiel)

> "Durante a tela de cadastro, a pessoa escolhe entre **Gestor** ou **Usuário**. Se escolher Gestor,
> há mais uma etapa: **pagamento**. Ela escolhe o plano, gera o pagamento e só então o cadastro como
> gestor é efetivado. Nesse caso seria melhor criar a entidade **Organização** para cada
> gestor/secretaria — o que implicaria escolher a **Opção C** da SPEC-15."

**Avaliação: a ideia é válida e coerente.** É o padrão clássico de SaaS B2B *self-service*
(cadastro → plano → cobrança → provisionamento). Ela ainda resolve dois itens em aberto do
`docs/checklist.md` de uma vez (integração de pagamento / Mercado Pago) e dá **motivo real** para a
multi-tenancy da SPEC-15. Três ajustes de modelagem, porém, são necessários — §2.

---

## 2. Três correções à ideia original

### 2.1 A organização é **por secretaria**, não por gestor
Uma secretaria tem (e vai ter) **mais de um gestor** — férias, troca de gestão, dois turnos. Logo:

```
Organizacao 1 ──── N Usuario        (e não 1 Organizacao por gestor)
Organizacao 1 ──── 1 Assinatura     (plano vigente)
Assinatura  1 ──── N Pagamento      (histórico de cobranças)
```

O plano e a cobrança pertencem à **organização**; o gestor que assinou é apenas o primeiro usuário
dela (e fica registrado como tal). Gestores seguintes entram por **convite** — a engine já existe
(`ConviteService`, ADR-11), e não pagam de novo.

### 2.2 Escolher "Gestor" na tela **não** pode conceder o papel
Hoje o `/registrar` só cria **PASSAGEIRO**; GERENTE nasce de um convite do SYSADMIN. Colocar um
seletor de papel no cadastro, sozinho, seria **escalonamento de privilégio por autoatendimento**:
qualquer pessoa marcaria "Gestor". O que fecha esse buraco é exatamente o pagamento — desde que a
regra seja:

> **RN-PAG-01 (a regra mais importante desta spec):** o papel `GERENTE` é concedido **apenas** pela
> confirmação **servidor-a-servidor** do pagamento (webhook + reconsulta na API do provedor). Nunca
> pela escolha do usuário, nunca pelo *redirect* de retorno do checkout (`back_url`), nunca por um
> campo do formulário. Até lá, a conta é `PENDENTE` **sem** papel de gestão.

O *redirect* de sucesso é **informação de UX**, não prova de pagamento — ele é controlado pelo
navegador e pode ser forjado digitando a URL.

### 2.3 Não é a **tela de login** que muda — é o que a **sessão carrega**
O impacto no login é **pequeno** (detalhado em §6). O caro não é autenticar: é fazer **toda consulta
do sistema** respeitar a fronteira da organização (§5.2).

---

## 3. Isto implica a Opção C da SPEC-15? — **Sim, e a própria SPEC-15 já previa**

A [SPEC-15 §7](SPEC-15-multi-ambiente-por-secretaria.md) lista os gatilhos para abandonar a Opção A
(um stack por cliente) e ir para a **Opção C** (linha a linha, com FK `organizacao`). Dois deles são
exatamente o que esta proposta cria:

| Gatilho previsto na SPEC-15 §7 | Esta proposta o dispara? |
|---|---|
| **Auto-serviço de cadastro** de secretarias | ✅ **Sim** — é o coração da ideia |
| **Painel único multi-secretaria** (admin da plataforma) | ✅ Sim — alguém precisa ver assinaturas/pagamentos de todas |
| Muitos tenants pequenos | 🟡 Provável (é o objetivo comercial) |
| Relatórios agregados | 🟡 Depois |

**Por que a Opção A deixa de servir:** ela provisiona um *stack* (app + banco + subdomínio + `.env`)
**à mão**, em minutos de trabalho humano. Quem paga com cartão às 2h da manhã espera acesso em
segundos. Automatizar a Opção A exigiria um *control plane* com acesso ao Docker/VPS — mais
infraestrutura, mais superfície de ataque e mais coisa para quebrar do que a Opção C.

> **Decisão proposta (ADR-20):** adotar a **Opção C** — `organizacao` como *tenant* nas tabelas
> operacionais — **substituindo** a ADR-19 (Opção A) como estratégia de multi-tenancy quando a venda
> por autoatendimento entrar em operação. A ADR-19 **não é descartada**: a Opção A continua sendo a
> resposta certa para um cliente que exija **isolamento físico** contratual (banco dedicado) — os
> dois modelos podem coexistir (a mesma imagem serve os dois).

---

## 4. Decisões propostas

### D1 — Entidades novas: `Organizacao`, `Assinatura`, `Plano`, `Pagamento`
`Plano` como **enum** (`ESSENCIAL`, `PLENO`, `AVANCADO`) com preço e limites no código — planos mudam
por decisão comercial, não por CRUD; vira tabela se/quando houver plano sob medida. ❓ **Confirmar os
planos, preços e o que cada um limita** (nº de veículos? de viagens/mês? de gestores?).

### D2 — Ciclo de vida da organização
`RASCUNHO → AGUARDANDO_PAGAMENTO → ATIVA → (INADIMPLENTE) → SUSPENSA/CANCELADA`.
Só `ATIVA` (ou `INADIMPLENTE` dentro do período de tolerância) permite operar.

### D3 — O papel vem do webhook (RN-PAG-01)
Confirmado o pagamento: organização → `ATIVA`, usuário → `ATIVO` **+ papel `GERENTE`**, e um
**token de ativação** (reuso do `ConviteService`/ADR-11) é enviado para a pessoa **definir a senha**.
Assim o fluxo de cadastro **não precisa** guardar senha de uma conta que talvez nunca seja paga.

### D4 — Provedor: **Mercado Pago, Checkout Pro** (redirect)
Redirect tira o cartão do nosso domínio (**zero escopo PCI**) e é o caminho com sandbox mais simples
para a disciplina. Atrás de uma **porta** `ProvedorPagamento` + adaptador `MercadoPagoProvedor` —
exatamente o padrão que já deu certo no WhatsApp (ADR-14): o domínio não conhece o provedor, e o bean
é **condicional** às variáveis de ambiente (sem credencial, a app sobe e a assinatura fica desligada).

### D5 — Confiança no webhook: assinatura + **reconsulta**
1. valida o header `x-signature` (HMAC do Mercado Pago);
2. **reconsulta o pagamento por id na API** — a fonte da verdade é a resposta da API, nunca o corpo
   do POST;
3. **idempotência** por `provedor_pagamento_id` (a mesma notificação chega várias vezes);
4. `external_reference` = id da organização, para amarrar pagamento ↔ tenant.

### D6 — Tenant no banco: coluna `organizacao` + `@TenantId` do Hibernate 6
Em vez de escrever `WHERE organizacao = ?` em cada consulta (o risco que a SPEC-15 §2 aponta como
"um `WHERE` esquecido = vazamento"), usa-se a **multi-tenancy por discriminador nativa do
Hibernate 6**: o campo anotado com `@TenantId` + um `CurrentTenantIdentifierResolver` que lê a
organização do usuário logado. O Hibernate passa a **filtrar toda consulta JPQL/Criteria e a
preencher a coluna em todo insert**, automaticamente.
⚠️ **Consultas SQL nativas não são filtradas** — auditar as existentes e proibir novas sem filtro
explícito (vira item de revisão de código).

### D7 — Unicidade e login: telefone/e-mail continuam **globais**
Mantendo a unicidade global, o usuário é encontrado no login como hoje e **a tela de login não muda**
(sem seletor de secretaria). Custo: a mesma pessoa não pode ser gestora de duas secretarias com o
mesmo telefone. É aceitável agora e reversível depois (unicidade composta + resolução por subdomínio).

### D8 — De onde vem a organização de um **passageiro**
Passageiro não escolhe secretaria numa lista (erraria). Ordem de resolução:
1. **link/convite** da secretaria (carrega o id da organização);
2. **subdomínio**, quando houver (`campina.caladrius.app`);
3. **município do endereço** (SPEC-07) → organização que atende aquele município;
4. sem resolver: cadastro fica pendente de vínculo, e o gestor aprova.
❓ **Confirmar a ordem** — muda a UX do cadastro público.

### D9 — Dados de referência **não** são multi-tenant
`municipios` e `cidades` seguem compartilhados (são referência do IBGE/domínio). O *entitlement* da
SPEC-13 (`municipios.pagamento_habilitado`) permanece válido e passa a conviver com a assinatura:
uma coisa é **o município aderir**, outra é **a secretaria ter plano ativo**.

---

## 5. Modelagem

### 5.1 Migration V16 — organização, assinatura e pagamento (fase 1)

```sql
CREATE TABLE organizacoes (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome         VARCHAR(160) NOT NULL,          -- "Secretaria de Saúde de Campina Grande"
    documento    VARCHAR(18),                    -- CNPJ (opcional no rascunho)
    slug         VARCHAR(60) NOT NULL UNIQUE,    -- subdomínio/identificador legível
    status       VARCHAR(30) NOT NULL DEFAULT 'RASCUNHO'
                 CHECK (status IN ('RASCUNHO','AGUARDANDO_PAGAMENTO','ATIVA','INADIMPLENTE','SUSPENSA','CANCELADA')),
    criado_em    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE assinaturas (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizacao   UUID NOT NULL REFERENCES organizacoes (id),
    plano         VARCHAR(30) NOT NULL CHECK (plano IN ('ESSENCIAL','PLENO','AVANCADO')),
    status        VARCHAR(30) NOT NULL CHECK (status IN ('PENDENTE','ATIVA','VENCIDA','CANCELADA')),
    ciclo         VARCHAR(20) NOT NULL DEFAULT 'MENSAL' CHECK (ciclo IN ('MENSAL','ANUAL')),
    valor_centavos INTEGER NOT NULL,
    vigente_ate   TIMESTAMPTZ,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pagamentos (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assinatura     UUID NOT NULL REFERENCES assinaturas (id),
    provedor       VARCHAR(30) NOT NULL DEFAULT 'MERCADO_PAGO',
    provedor_id    VARCHAR(80),                  -- id do pagamento no provedor (idempotência)
    status         VARCHAR(30) NOT NULL CHECK (status IN ('PENDENTE','APROVADO','RECUSADO','ESTORNADO')),
    valor_centavos INTEGER NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmado_em  TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_pagamentos_provedor_id ON pagamentos (provedor, provedor_id)
    WHERE provedor_id IS NOT NULL;

ALTER TABLE usuarios ADD COLUMN organizacao UUID REFERENCES organizacoes (id);
```

> `usuarios.organizacao` **nullable** de propósito: as contas que já existem (e o SYSADMIN da
> plataforma) não pertencem a nenhuma organização. Migration **aditiva** (Art. IV), sem extensões
> (Art. XIV).

### 5.2 Migration V17 — o tenant nas tabelas operacionais (fase 2)

`ALTER TABLE … ADD COLUMN organizacao UUID REFERENCES organizacoes (id)` em **`viagens`,
`veiculos`, `linhas_programadas`, `solicitacoes_viagem`, `enderecos`, `conversas_bot`,
`mensagens_whatsapp`, `notificacoes`, `configuracoes_sistema`(❓ ver abaixo)** + índice por
`organizacao` em cada uma. **Backfill**: tudo o que existe hoje pertence à organização "legado"
criada na própria migration. Só depois do backfill a coluna vira `NOT NULL`.

❓ **`configuracoes_sistema`**: hoje é global (sessão, WhatsApp, **flags da SPEC-13**). Com multi-tenant,
parte vira **por organização** (nome de exibição do WhatsApp, parâmetros de OTP) e parte continua
**da plataforma** (modo de manutenção). Decisão a tomar junto com a fase 2.

---

## 6. Impacto no login — **menor do que parece**

| Peça | Muda? | O quê |
|---|---|---|
| `CaladriusUserDetailsService` (e-mail/telefone + BCrypt) | ❌ **Não** | a busca é a mesma (D7) |
| Tela de login | ❌ **Não** | sem seletor de secretaria (D7) |
| Login Google/OIDC (SPEC-08) | 🟡 Quase nada | auto-provisão passa a exigir organização resolvida (D8) |
| `UsuarioAutenticado` | ✅ **Sim, ~5 linhas** | passa a carregar `organizacaoId` |
| `TenantContext` + `CurrentTenantIdentifierResolver` | ➕ **Novo** | lê a organização do principal (uma classe pequena) |
| Autorização (RBAC) | 🟡 **+1 regra** | organização `SUSPENSA`/`INADIMPLENTE` ⇒ acesso restrito à tela de pagamento (reusa o padrão do `PerfilIncompletoFilter`) |
| `/registrar` | ✅ **Sim** | ganha o ramo "sou gestor" → checkout (o ramo "sou passageiro" continua igual) |

**Resposta direta à pergunta "daria para implementar sem danos profundos ao código?":**
**Sim para o login — não para o resto.** Autenticação praticamente não muda. O trabalho pesado é a
**fase 2**: coluna de tenant em ~8 tabelas, backfill, `@TenantId` e — o item inegociável — **testes de
isolamento** (um gestor da organização A **não** enxerga nada da B). Sem esses testes, a fase 2 não
deve subir.

---

## 7. Regras de negócio

| Regra | Descrição |
|---|---|
| **RN-PAG-01** | O papel `GERENTE` só é concedido por **confirmação servidor-a-servidor** do pagamento (webhook + reconsulta na API). Nunca pelo formulário nem pelo redirect de retorno. |
| **RN-PAG-02** | Enquanto o pagamento não confirma, a conta fica `PENDENTE` **sem** papel de gestão e a organização em `AGUARDANDO_PAGAMENTO`. |
| **RN-PAG-03** | O webhook é **idempotente** por `(provedor, provedor_id)`: a mesma notificação repetida não duplica pagamento nem concede papel duas vezes. |
| **RN-PAG-04** | Assinatura vencida ⇒ organização `INADIMPLENTE`: **leitura preservada** e escrita bloqueada durante a tolerância; depois, `SUSPENSA`. Dados **nunca** são apagados por inadimplência. |
| **RN-PAG-05** | **Isolamento**: nenhuma consulta de dados operacionais atravessa organizações. Vale para relatórios, exportações e o bot do WhatsApp. |
| **RN-PAG-06** | O **SYSADMIN da plataforma** é o único papel que enxerga várias organizações — e apenas as telas de organização/assinatura/pagamento, **não** os dados de viagem. |
| **RN-PAG-07** | Nenhum dado de cartão trafega ou é armazenado pelo CALADRIUS (Checkout Pro — D4). Guardamos só ids e status do provedor. |
| **RN-PAG-08** | Credenciais do provedor vivem **só** em variável de ambiente (Art. XI), como `EVOLUTION_API_KEY` e o token OTel. Sem elas, a assinatura fica desligada e a app sobe igual. |
| **RN-PAG-09** | Convidar mais gestores para uma organização **ativa** não gera nova cobrança (a assinatura é da organização). |

---

## 8. Critérios de aceite (Dado / Quando / Então)

- **CA-PAG-01 — Papel só depois do pagamento**
  *Dado* um cadastro de gestor com pagamento pendente, *Quando* a pessoa tenta acessar `/viagens`,
  *Então* recebe 403 (não tem `GERENTE`) e é levada à tela de pagamento.
- **CA-PAG-02 — Redirect forjado não vale**
  *Dado* um pagamento **não** confirmado, *Quando* alguém acessa manualmente a URL de retorno de
  sucesso, *Então* nada é concedido (RN-PAG-01).
- **CA-PAG-03 — Webhook confirma e promove**
  *Dado* o webhook de pagamento aprovado (assinatura válida + reconsulta ok), *Então* a organização
  vira `ATIVA`, o usuário recebe `GERENTE` e o link para definir a senha.
- **CA-PAG-04 — Webhook repetido é inócuo**
  *Dado* a mesma notificação entregue 3×, *Então* há **um** pagamento registrado e o papel é
  concedido uma vez.
- **CA-PAG-05 — Assinatura falsa é rejeitada**
  *Dado* um POST no webhook com `x-signature` inválida, *Então* responde 401/403 e nada muda.
- **CA-PAG-06 — Isolamento entre secretarias**
  *Dado* viagens/veículos/solicitações das organizações A e B, *Quando* o gestor de A lista qualquer
  uma delas, *Então* vê **somente** as de A (teste automatizado obrigatório — §6).
- **CA-PAG-07 — Inadimplência não apaga dado**
  *Dado* uma assinatura vencida, *Então* a organização fica `INADIMPLENTE`, a leitura continua e
  nenhum registro é removido.

---

## 9. Faseamento sugerido (e o risco de inverter a ordem)

| Fase | Entrega | Migration | Por quê nesta ordem |
|---|---|---|---|
| **1** | `Organizacao` + `Assinatura` + `Pagamento` + cadastro de gestor com checkout + webhook | **V16** | Fecha o item ⬜ do checklist (pagamento/Mercado Pago) e dá algo demonstrável, **sem** o refactor de risco |
| **2** | Tenant (`organizacao`) nas tabelas operacionais + `@TenantId` + **testes de isolamento** | **V17** | O refactor grande, isolado, com teste dedicado |

⚠️ **Risco explícito de rodar a fase 1 sozinha:** entre as duas fases, gestores de secretarias
diferentes **enxergam os dados uns dos outros** (é o estado de hoje — SPEC-15 §1). Para demonstração
acadêmica é aceitável **se declarado**; **antes de qualquer cliente real, a fase 2 é obrigatória**.

---

## 10. Próximos passos

1. **Responder os ❓** desta spec: planos/preços/limites (D1), resolução da organização do passageiro
   (D8) e o destino da `configuracoes_sistema` (§5.2).
2. Aprovar a **ADR-20** (Opção C substituindo a A como estratégia padrão) no
   [plano técnico §9](../02-plano-tecnico.md).
3. Implementar a **fase 1** (V16) com testes: webhook idempotente, assinatura inválida, redirect
   forjado (CA-PAG-01..05).
4. Implementar a **fase 2** (V17) com os **testes de isolamento** (CA-PAG-06) como critério de saída.
5. Atualizar [`docs/checklist.md`](../../checklist.md) (pagamento ⬜ → ✅) e a
   [SPEC-15](SPEC-15-multi-ambiente-por-secretaria.md) §7 registrando que o gatilho foi acionado.
