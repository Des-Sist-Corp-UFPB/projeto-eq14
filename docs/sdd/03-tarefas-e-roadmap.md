# Tarefas e Roadmap — CALADRIUS

> Rastreabilidade entre as [specs](specs/) e o estado real do código, mais o roteiro dos
> próximos incrementos. Legenda: ✅ Implementado · 🟡 Parcial · ⬜ Planejado · 🔵 Em avaliação.

---

## 1. Estado atual por capacidade

| Capacidade | Spec | Estado | Observação |
|---|---|---|---|
| Autenticação (login e-mail/telefone) | [SPEC-01](specs/SPEC-01-autenticacao.md) | ✅ | mensagem genérica, só ativos logam |
| Auto-cadastro de passageiro | [SPEC-01](specs/SPEC-01-autenticacao.md) | ✅ | papel `PASSAGEIRO`, status `ATIVO` |
| Logout + health `/ping` | [SPEC-01](specs/SPEC-01-autenticacao.md) | ✅ | `/ping` público |
| CRUD de usuários + papéis | [SPEC-02](specs/SPEC-02-gestao-usuarios.md) | ✅ | soft-delete; busca multi-campo |
| CRUD de veículos | [SPEC-03](specs/SPEC-03-gestao-veiculos.md) | ✅ | soft-delete; placa única |
| CRUD de cidades | [SPEC-04](specs/SPEC-04-gestao-cidades.md) | ✅ | remoção física; seed na V3 |
| Viagens: criar/listar/excluir | [SPEC-05](specs/SPEC-05-gestao-viagens.md) | 🟡 | sem edição/transição de status |
| Painel inicial (totais) | (produto §4) | ✅ | contagens por repositório |

---

## 2. Dívidas técnicas e lacunas (do código atual)

Itens já identificados nas specs, ordenados por relevância. Cada um deve virar uma spec/tarefa
antes de ser implementado.

| ID | Lacuna | Origem | Estado |
|---|---|---|---|
| **DT-01** | Excluir cidade referenciada por viagem pode violar a FK | [SPEC-04 §7](specs/SPEC-04-gestao-cidades.md) | ✅ Resolvido — `CidadeService` remove viagens em cascata + aviso no popup |
| **DT-02** | Sem trava contra excluir/suspender o **último gerente** ou a si mesmo | [SPEC-02 §9](specs/SPEC-02-gestao-usuarios.md) | ✅ Resolvido — guarda do último gerente, bloqueio de auto-deleção, `solicitarSuspensao` |
| **DT-03** | Viagens não checam conflito de veículo/motorista sobrepostos | [SPEC-06 §6](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md) | 🟡 Especificado na SPEC-06 (RN-VIA-08) — aguarda #21 |
| **DT-04** | `listarAtivos()` de veículos não filtra por `DISPONIVEL` | [SPEC-03 §8](specs/SPEC-03-gestao-veiculos.md) | ✅ Resolvido — `listarDisponiveis()` filtra `DISPONIVEL` |
| **DT-05** | Sem edição/transição de status de viagem | [SPEC-06](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md) | 🟡 Coberto pela SPEC-06 — aguarda #21 |
| **DT-06** | `SECURITY.md` descrevia o boilerplate (Mercado/in-memory) | [Plano §5](02-plano-tecnico.md) | ✅ Resolvido — `SECURITY.md` alinhado à auth no banco |
| **DT-07** | Faltam testes de `ViagemService`/camada web | [Plano §8](02-plano-tecnico.md) | 🟡 Parcial — `CidadeServiceTest` criado; falta `ViagemService`/web |
| **DT-08** | Data de viagem no passado não é bloqueada | [SPEC-06 §6](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md) | 🟡 Especificado (RN-VIA-09, só imprevistas) — aguarda #21 |
| **DT-09** | `retorno_previsto` não coletado / tipo inconsistente | [SPEC-06 §2](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md) | 🟡 Especificado — vira `horario_retorno` (LocalTime), ida/volta — aguarda #21 |
| **DT-10** | Gestão de sessão (timeout/cookie) não especificada | [Plano §2.5](02-plano-tecnico.md) | 🟡 A formalizar — config **dinâmica** via SPEC de Configuração do Sistema (#18) |
| **DT-11** | Teste de contexto (Testcontainers) falhava com Docker Engine novo | [Cenários](cenarios-de-teste.md) | ✅ Resolvido — Testcontainers **1.20.4 → 1.21.4** (negocia a API ≥ 1.40) |

---

## 3. Roadmap dos próximos incrementos

Os incrementos seguem a ordem sugerida na [`CLAUDE.md`](../../CLAUDE.md) ("Próximos Passos").
Cada um deve começar por uma **nova spec** em `specs/` e respeitar o
[checklist do Plano Técnico §10](02-plano-tecnico.md).

> **Em desenvolvimento (sequência aprovada pelo dono do projeto):**
> 1. **[SPEC-07](specs/SPEC-07-endereco-do-passageiro.md) — Endereço do passageiro** (tabela
>    `enderecos` estruturada, **migration V8**) → vem **antes** das viagens.
> 2. **[SPEC-06](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md) — Viagens rotineiras/imprevistas**
>    (`linhas_programadas` + `linha_dias` + alteração de `viagens`, **migration V9**). Decisões
>    confirmadas em [SPEC-06 §2.1](specs/SPEC-06-viagens-rotineiras-e-imprevistas.md) (ADR-12/13).

### Incremento A — Solicitações de transporte (passageiro) ✅
- **Entregue** em duas etapas: **por linha** ([SPEC-09](specs/SPEC-09-solicitacao-de-transporte.md), V11)
  e **sob demanda** ([SPEC-11](specs/SPEC-11-solicitacao-sob-demanda-e-onboarding-whatsapp.md), V13 —
  destino+data+horário+condições, sem linha; avaliação/aprovação do gestor; onboarding pelo WhatsApp).
- **Nota**: em vez de mapear a `solicitacoes_transporte` (V2), estendeu-se `solicitacoes_viagem`
  com um `tipo` (ADR-15). A tabela `solicitacoes_transporte` segue **dormente** (DT de limpeza futura).

### Incremento B — Alocação automática por prioridade ⬜
- **Objetivo**: alocar passageiros a **assentos** de viagem por prioridade (horário-limite de
  chegada), respeitando capacidade e acessibilidade do veículo.
- **Base pronta no schema**: `assentos_viagem` (V2).
- **Depende de**: Incremento A (solicitações) e SPEC-05 (viagens).
- **Resolve junto**: DT-04 (só alocar veículos disponíveis/compatíveis).

### Incremento B.1 — Aprovação/recusa do gestor (sob demanda) ✅
- **Entregue** na [SPEC-11](specs/SPEC-11-solicitacao-sob-demanda-e-onboarding-whatsapp.md): painel
  `/gestao/solicitacoes` (GERENTE) avalia demandas, **aprova** (aloca a uma viagem imprevista) ou
  **recusa** (com motivo); o passageiro é notificado por WhatsApp. Assentos/capacidade seguem no
  Incremento B (alocação por prioridade — o gestor avalia a prioridade manualmente por ora).

### Incremento C — Escalas de motorista e telas por papel ⬜
- **Objetivo**: registrar janelas de disponibilidade do motorista e dar a ele a visão das
  próprias viagens; perfis detalhados (passageiro/motorista/gerente).
- **Base pronta no schema**: `escalas_motorista`, `perfis_*` (V2).
- **Resolve junto**: DT-03 (validar disponibilidade ao criar viagem).

### Incremento D — Integração WhatsApp (Evolution API) ✅ (código) / 🟡 (infra)
- **Status**: **código implementado e testado (2026-07-14)** — [SPEC-10](specs/SPEC-10-integracao-whatsapp.md)
  (ADR-14), migration **V12** aplicada, 182 testes verdes. **Pendente**: subir a Evolution na
  **VPS da equipe** (SPEC-10 §8) e configurar as variáveis de ambiente no deploy
  (`EVOLUTION_URL`, `EVOLUTION_API_KEY`, `WHATSAPP_WEBHOOK_TOKEN`, `APP_URL_PUBLICA`) — sem elas
  o canal segue como stub e o painel informa "não configurada" (RN-WPP-02).
- **Entregue**: porta `ProvedorWhatsapp` + adaptador `EvolutionApiProvedor` (bean condicional),
  envio real no `NotificacaoWhatsappCanal` (via fachada `WhatsappService` + log
  `mensagens_whatsapp`), webhook `POST /webhooks/whatsapp` (token + idempotência), **bot de
  atendimento** (`whatsapp/bot/`, máquina de estados em `conversas_bot`) reaproveitando o
  `SolicitacaoViagemService` (SPEC-09), e painel `/whatsapp` do gerente (QR + polling HTMX,
  status, desconectar, teste, últimas mensagens).

---

## 4. Backlog técnico transversal (não-funcional)

| Item | Descrição |
|---|---|
| Alinhar `SECURITY.md` | Atualizar para o modelo real (auth no banco), removendo resíduos do boilerplate (DT-06). |
| Cobertura de testes | Testes de `ViagemService`/`CidadeService` e de controllers; um teste por critério de aceite (DT-07). |
| Regras de integridade | Proteções DT-01 (cidade referenciada) e DT-02 (último gerente). |
| Sincronizar status do veículo | Marcar `EM_VIAGEM` ao alocar; liberar ao concluir/cancelar. |
| Observabilidade | Avaliar métricas/health além de `/ping` (Actuator). |

---

## 5. Como manter este documento

- Ao concluir um item, **mover** de ⬜/🟡 para ✅ e atualizar a spec correspondente
  (status dos `FR-XX`).
- Ao descobrir uma lacuna, **registrar** como `DT-XX` apontando a origem na spec.
- Ao iniciar um incremento, **criar a spec primeiro** (a fonte da verdade do SDD) e só então
  abrir as tarefas de implementação.
